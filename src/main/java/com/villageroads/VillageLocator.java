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
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Random;
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
    record Village(int x, int z, int clearance) {}

    private record Key(int src, int rx, int rz) {}

    private record Rule(RandomSpreadStructurePlacement placement, List<Structure> structures) {}

    private static final class Ctx {
        final List<Rule> rules = new ArrayList<>();
        final ChunkGeneratorStructureState state;
        final BiomeSource biomes;
        final Climate.Sampler sampler;
        final long seed;
        final ServerLevel level;
        final Recruits recruits;
        final Map<Key, Village> regionCache = new ConcurrentHashMap<>();
        final Map<Key, Boolean> missCache = new ConcurrentHashMap<>();

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
            this.level = level;
            this.recruits = RoadConfig.RECRUITS_VILLAGES.get() ? Recruits.load() : null;
        }
    }


    /** Settings of the Recruits Villages mod's own village grid (read by reflection, so it is only an optional link). */
    private record Recruits(int spacing, int separation, int salt, List<String> tags, int variance, int plotRadius) {
        static Recruits load() {
            try {
                Class<?> c = Class.forName("com.diego.recruitsvillages.config.RecruitsVillagesConfig");
                if (!(Boolean) val(c, "NATURAL_GEN_ENABLED")) {
                    return null;
                }
                List<String> tags = new ArrayList<>();
                for (Object o : (List<?>) val(c, "GEN_BIOME_TAGS")) {
                    tags.add(String.valueOf(o));
                }
                return new Recruits((Integer) val(c, "VILLAGE_SPACING_CHUNKS"), (Integer) val(c, "VILLAGE_SEPARATION_CHUNKS"),
                        (Integer) val(c, "VILLAGE_SALT"), tags, (Integer) val(c, "GEN_MAX_HEIGHT_VARIANCE"),
                        Math.min((Integer) val(c, "GEN_PLOT_CHECK_RADIUS"), 20));
            } catch (Throwable t) {
                return null;
            }
        }

        private static Object val(Class<?> c, String field) throws ReflectiveOperationException {
            return ((ForgeConfigSpec.ConfigValue<?>) c.getField(field).get(null)).get();
        }
    }

    private static boolean biomeOk(Holder<Biome> biome, List<String> tags) {
        for (String entry : tags) {
            ResourceLocation id = ResourceLocation.tryParse(entry.startsWith("#") ? entry.substring(1) : entry);
            if (id == null) {
                continue;
            }
            if (biome.is(TagKey.create(Registries.BIOME, id)) || biome.is(id)) {
                return true;
            }
        }
        return false;
    }

    /** Predicts the candidate site of a Recruits Villages grid cell, with the same random draw the mod uses. */
    private static Village recruitsRegion(Ctx c, int rx, int rz) {
        Recruits r = c.recruits;
        Key key = new Key(1, rx, rz);
        Village cached = c.regionCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (c.missCache.containsKey(key)) {
            return null;
        }
        long seed = c.seed ^ (long) r.salt();
        long cellSeed = seed ^ (long) rx * 341873128712L ^ (long) rz * 132897987541L;
        Random rng = new Random(cellSeed);
        int range = Math.max(1, r.spacing() - Math.min(r.separation(), r.spacing() - 1));
        int chunkX = rx * r.spacing() + rng.nextInt(range);
        int chunkZ = rz * r.spacing() + rng.nextInt(range);
        int bx = (chunkX << 4) + 8;
        int bz = (chunkZ << 4) + 8;
        Village result = null;
        Holder<Biome> biome = c.biomes.getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(63), QuartPos.fromBlock(bz), c.sampler);
        if (biomeOk(biome, r.tags()) && flatEnough(c, bx, bz, r)) {
            result = new Village(bx, bz, RoadConfig.RECRUITS_CLEARANCE.get());
        }
        store(c, key, result);
        return result;
    }

    private static boolean flatEnough(Ctx c, int x, int z, Recruits r) {
        int s = r.plotRadius(), h = s / 2;
        int[][] pts = {{0, 0}, {s, 0}, {-s, 0}, {0, s}, {0, -s}, {h, h}, {h, -h}, {-h, h}, {-h, -h}};
        int sea = c.level.getChunkSource().getGenerator().getSeaLevel();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] p : pts) {
            int floor = c.level.getChunkSource().getGenerator().getBaseHeight(x + p[0], z + p[1],
                    Heightmap.Types.OCEAN_FLOOR_WG, c.level, c.level.getChunkSource().randomState());
            if (floor < sea) {
                return false;
            }
            min = Math.min(min, floor);
            max = Math.max(max, floor);
        }
        return max - min <= r.variance();
    }

    private static void store(Ctx c, Key key, Village v) {
        if (c.regionCache.size() + c.missCache.size() > 40000) {
            c.regionCache.clear();
            c.missCache.clear();
        }
        if (v != null) {
            c.regionCache.put(key, v);
        } else {
            c.missCache.put(key, Boolean.TRUE);
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
        if (c.recruits != null) {
            int sb = c.recruits.spacing() * 16;
            for (int rx = Math.floorDiv(blockX - radius, sb); rx <= Math.floorDiv(blockX + radius, sb); rx++) {
                for (int rz = Math.floorDiv(blockZ - radius, sb); rz <= Math.floorDiv(blockZ + radius, sb); rz++) {
                    Village v = recruitsRegion(c, rx, rz);
                    if (v != null) {
                        long dx = v.x() - blockX, dz = v.z() - blockZ;
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
        Key key = new Key(0, rx, rz + ri * 1000003);
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
                    result = new Village(bx, bz, RoadConfig.VILLAGE_CLEARANCE.get());
                    break;
                }
            }
        }
        store(c, key, result);
        return result;
    }
}
