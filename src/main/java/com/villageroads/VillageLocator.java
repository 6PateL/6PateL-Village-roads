package com.villageroads;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds where villages WILL be, from the world seed alone: the structure placement rule gives the one possible start
 * chunk per region, and the biome source says whether a village may generate there. Nothing is loaded or generated.
 * Results are memoised per region, so a chunk costs a few hash lookups.
 */
final class VillageLocator {
    record Village(int x, int z) {}

    private record Rule(RandomSpreadStructurePlacement placement, List<Structure> structures) {}

    private static final class Ctx {
        final List<Rule> rules = new ArrayList<>();
        final ChunkGeneratorStructureState state;
        final BiomeSource biomes;
        final Climate.Sampler sampler;
        final long seed;
        final Map<String, Village> regionCache = new ConcurrentHashMap<>();
        final Map<String, Boolean> missCache = new ConcurrentHashMap<>();

        Ctx(ServerLevel level, List<? extends String> setIds) {
            RegistryAccess access = level.registryAccess();
            Registry<StructureSet> sets = access.registryOrThrow(Registries.STRUCTURE_SET);
            for (String id : setIds) {
                StructureSet set = sets.get(new ResourceLocation(id));
                if (set != null && set.placement() instanceof RandomSpreadStructurePlacement rs) {
                    List<Structure> list = new ArrayList<>();
                    set.structures().forEach(e -> list.add(e.structure().value()));
                    rules.add(new Rule(rs, list));
                }
            }
            this.state = level.getChunkSource().getGeneratorState();
            this.biomes = level.getChunkSource().getGenerator().getBiomeSource();
            this.sampler = level.getChunkSource().randomState().sampler();
            this.seed = state.getLevelSeed();
        }
    }

    private static final Map<RegistryAccess, Ctx> CONTEXTS = new WeakHashMap<>();

    private VillageLocator() {}

    private static Ctx ctx(ServerLevel level) {
        synchronized (CONTEXTS) {
            return CONTEXTS.computeIfAbsent(level.registryAccess(), k -> new Ctx(level, RoadConfig.STRUCTURE_SETS.get()));
        }
    }

    /** Villages (their start chunk centres) within {@code radius} blocks of the given block position. */
    static List<Village> near(ServerLevel level, int blockX, int blockZ, int radius) {
        Ctx c = ctx(level);
        List<Village> out = new ArrayList<>();
        for (int ri = 0; ri < c.rules.size(); ri++) {
            Rule rule = c.rules.get(ri);
            int spacingBlocks = rule.placement().spacing() * 16;
            int rx0 = Math.floorDiv(blockX - radius, spacingBlocks);
            int rx1 = Math.floorDiv(blockX + radius, spacingBlocks);
            int rz0 = Math.floorDiv(blockZ - radius, spacingBlocks);
            int rz1 = Math.floorDiv(blockZ + radius, spacingBlocks);
            for (int rx = rx0; rx <= rx1; rx++) {
                for (int rz = rz0; rz <= rz1; rz++) {
                    Village v = region(c, ri, rule, rx, rz);
                    if (v != null) {
                        long dx = v.x() - blockX;
                        long dz = v.z() - blockZ;
                        if (dx * dx + dz * dz <= (long) radius * radius) {
                            out.add(v);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static Village region(Ctx c, int ri, Rule rule, int rx, int rz) {
        String key = ri + ":" + rx + ":" + rz;
        Village cached = c.regionCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (c.missCache.containsKey(key)) {
            return null;
        }
        int s = rule.placement().spacing();
        ChunkPos cp = rule.placement().getPotentialStructureChunk(c.seed, rx * s, rz * s);
        Village result = null;
        if (rule.placement().isStructureChunk(c.state, cp.x, cp.z)) {
            int bx = cp.getMiddleBlockX();
            int bz = cp.getMiddleBlockZ();
            Holder<Biome> biome = c.biomes.getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(63),
                    QuartPos.fromBlock(bz), c.sampler);
            for (Structure st : rule.structures()) {
                if (st.biomes().contains(biome)) {
                    result = new Village(bx, bz);
                    break;
                }
            }
        }
        if (c.regionCache.size() + c.missCache.size() > 40000) {
            c.regionCache.clear();
            c.missCache.clear();
        }
        if (result != null) {
            c.regionCache.put(key, result);
        } else {
            c.missCache.put(key, Boolean.TRUE);
        }
        return result;
    }
}
