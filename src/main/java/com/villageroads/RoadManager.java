package com.villageroads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remembers villages as their chunks load, plans a road between each new village and its nearest neighbours,
 * and lays the road blocks chunk by chunk (only in chunks that are loaded, a few per tick).
 */
public class RoadManager {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private final Map<String, ArrayDeque<Long>> pending = new HashMap<>();
    private final Map<String, Set<Long>> queued = new HashMap<>();

    private static boolean isVillage(ResourceLocation id) {
        return id != null && (id.getPath().contains("village") || id.getNamespace().equals("recruitsvillages"));
    }

    private static String dimOf(ServerLevel level) {
        return level.dimension().location().toString();
    }

    // ------------------------------------------------------------------ discovery

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!RoadConfig.BUILD_ROADS.get() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        String dim = dimOf(level);
        if (!RoadConfig.DIMENSIONS.get().contains(dim)) {
            return;
        }
        ChunkAccess chunk = event.getChunk();
        RoadData data = RoadData.get(level.getServer());
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> en : chunk.getAllStarts().entrySet()) {
            StructureStart start = en.getValue();
            if (start == null || !start.isValid() || !isVillage(registry.getKey(en.getKey()))) {
                continue;
            }
            String key = dim + "|" + start.getChunkPos().x + "," + start.getChunkPos().z;
            if (data.hasVillage(key)) {
                continue;
            }
            registerVillage(level, data, key, start.getBoundingBox().getCenter());
        }
        enqueue(dim, chunk.getPos().toLong());
    }

    private void registerVillage(ServerLevel level, RoadData data, String key, BlockPos center) {
        String dim = dimOf(level);
        RoadData.Village nv = new RoadData.Village();
        nv.key = key;
        nv.dim = dim;
        nv.center = center;

        List<RoadData.Village> others = new ArrayList<>();
        double max = RoadConfig.MAX_LINK_DISTANCE.get();
        double same = RoadConfig.SETTLEMENT_RADIUS.get();
        for (RoadData.Village v : data.villages) {
            if (!dim.equals(v.dim)) {
                continue;
            }
            double d2 = horizontalDist2(v.center, center);
            if (d2 < same * same) {
                // another part of the same settlement: remember it, but never link inside one village
                data.villages.add(nv);
                data.setDirty();
                return;
            }
            if (d2 <= max * max) {
                others.add(v);
            }
        }
        data.villages.add(nv);
        LOG.info("[VillageRoads] village found at {} ({} other villages in range)", center, others.size());
        others.sort(Comparator.comparingDouble(v -> horizontalDist2(v.center, center)));
        int n = Math.min(others.size(), RoadConfig.LINKS_PER_VILLAGE.get());
        for (int i = 0; i < n; i++) {
            RoadData.Village o = others.get(i);
            if (data.hasLink(o.center, center)) {
                continue;
            }
            RoadData.Link l = new RoadData.Link();
            l.dim = dim;
            l.a = o.center;
            l.b = center;
            l.seed = center.asLong() * 31L + o.center.asLong();
            data.links.add(l);
            LOG.info("[VillageRoads] road planned {} -> {}", o.center, center);
            for (long c : chunksAlong(l)) {
                if (RoadConfig.GENERATE_CHUNKS.get() || level.getChunkSource().hasChunk(ChunkPos.getX(c), ChunkPos.getZ(c))) {
                    enqueue(dim, c);
                }
            }
        }
        data.setDirty();
    }

    private static double horizontalDist2(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static List<Long> chunksAlong(RoadData.Link l) {
        Set<Long> set = new HashSet<>();
        double len = Math.sqrt(horizontalDist2(l.a, l.b));
        int steps = (int) (len / 6.0D) + 1;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (l.a.getX() + (l.b.getX() - l.a.getX()) * t);
            int z = (int) (l.a.getZ() + (l.b.getZ() - l.a.getZ()) * t);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set.add(ChunkPos.asLong((x >> 4) + dx, (z >> 4) + dz));
                }
            }
        }
        return new ArrayList<>(set);
    }

    private void enqueue(String dim, long chunk) {
        if (queued.computeIfAbsent(dim, k -> new HashSet<>()).add(chunk)) {
            pending.computeIfAbsent(dim, k -> new ArrayDeque<>()).add(chunk);
        }
    }

    // ------------------------------------------------------------------ building

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !RoadConfig.BUILD_ROADS.get() || pending.isEmpty()) {
            return;
        }
        RoadData data = null;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            String dim = dimOf(level);
            ArrayDeque<Long> q = pending.get(dim);
            if (q == null || q.isEmpty()) {
                continue;
            }
            if (data == null) {
                data = RoadData.get(event.getServer());
            }
            int budget = RoadConfig.CHUNKS_PER_TICK.get();
            while (budget-- > 0 && !q.isEmpty()) {
                long c = q.poll();
                queued.get(dim).remove(c);
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
                if (chunk == null && RoadConfig.GENERATE_CHUNKS.get()) {
                    boolean onLink = false;
                    for (RoadData.Link l : data.links) {
                        if (dim.equals(l.dim) && !l.done.contains(c) && RoadBuilder.nearChunk(l, new ChunkPos(c))) {
                            onLink = true;
                            break;
                        }
                    }
                    if (onLink) {
                        chunk = level.getChunk(ChunkPos.getX(c), ChunkPos.getZ(c));
                    }
                }
                if (chunk == null) {
                    continue;
                }
                boolean changed = false;
                for (RoadData.Link l : data.links) {
                    if (!dim.equals(l.dim) || l.done.contains(c) || !RoadBuilder.nearChunk(l, chunk.getPos())) {
                        continue;
                    }
                    RoadBuilder.build(level, chunk, l);
                    l.done.add(c);
                    changed = true;
                }
                if (changed) {
                    data.setDirty();
                }
            }
        }
    }
}
