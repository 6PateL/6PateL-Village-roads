package com.villageroads;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;
import java.util.Set;

/** Paints the part of a road that lies inside one chunk. */
final class RoadBuilder {
    private RoadBuilder() {}

    private static double length(RoadData.Link l) {
        double dx = l.b.getX() - l.a.getX();
        double dz = l.b.getZ() - l.a.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Point of the road centre line at distance s from a (with a gentle wiggle, tapering to 0 at the ends). */
    private static double[] point(RoadData.Link l, double s, double len, double phase) {
        double dx = (l.b.getX() - l.a.getX()) / len;
        double dz = (l.b.getZ() - l.a.getZ()) / len;
        double excl = RoadConfig.VILLAGE_EXCLUSION.get();
        double taper = Math.max(0.0D, Math.min(1.0D, Math.min((s - excl) / 40.0D, (len - excl - s) / 40.0D)));
        double off = 4.0D * Math.sin(s / 26.0D + phase) * taper;
        double x = l.a.getX() + 0.5D + dx * s - dz * off;
        double z = l.a.getZ() + 0.5D + dz * s + dx * off;
        return new double[]{x, z};
    }

    static boolean nearChunk(RoadData.Link l, ChunkPos cp) {
        double len = length(l);
        if (len < 1.0D) {
            return false;
        }
        // cheap bounding-box test with margin for the wiggle and road width
        double minX = Math.min(l.a.getX(), l.b.getX()) - 12.0D, maxX = Math.max(l.a.getX(), l.b.getX()) + 12.0D;
        double minZ = Math.min(l.a.getZ(), l.b.getZ()) - 12.0D, maxZ = Math.max(l.a.getZ(), l.b.getZ()) + 12.0D;
        if (cp.getMaxBlockX() < minX || cp.getMinBlockX() > maxX || cp.getMaxBlockZ() < minZ || cp.getMinBlockZ() > maxZ) {
            return false;
        }
        double phase = (l.seed & 1023L) / 163.0D;
        double excl = RoadConfig.VILLAGE_EXCLUSION.get();
        for (double s = excl; s <= len - excl; s += 4.0D) {
            double[] p = point(l, s, len, phase);
            if (p[0] >= cp.getMinBlockX() - 6 && p[0] <= cp.getMaxBlockX() + 6
                    && p[1] >= cp.getMinBlockZ() - 6 && p[1] <= cp.getMaxBlockZ() + 6) {
                return true;
            }
        }
        return false;
    }

    static void build(ServerLevel level, LevelChunk chunk, RoadData.Link l) {
        double len = length(l);
        double excl = RoadConfig.VILLAGE_EXCLUSION.get();
        if (len < 2.0D * excl + 4.0D) {
            return;
        }
        double phase = (l.seed & 1023L) / 163.0D;
        int half = RoadConfig.ROAD_WIDTH.get() / 2;
        int minX = chunk.getPos().getMinBlockX(), maxX = chunk.getPos().getMaxBlockX();
        int minZ = chunk.getPos().getMinBlockZ(), maxZ = chunk.getPos().getMaxBlockZ();
        Random rnd = new Random(l.seed ^ chunk.getPos().toLong());
        Set<Long> seen = new java.util.HashSet<>();
        for (double s = excl; s <= len - excl; s += 0.7D) {
            double[] p = point(l, s, len, phase);
            int cx = (int) Math.floor(p[0]);
            int cz = (int) Math.floor(p[1]);
            if (cx < minX - half - 1 || cx > maxX + half + 1 || cz < minZ - half - 1 || cz > maxZ + half + 1) {
                continue;
            }
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    int x = cx + dx, z = cz + dz;
                    if (x < minX || x > maxX || z < minZ || z > maxZ) {
                        continue;
                    }
                    boolean edge = dx != 0 || dz != 0;
                    if (edge && half > 0 && rnd.nextInt(100) < 15) {
                        continue; // ragged edges
                    }
                    if (seen.add(BlockPos.asLong(x, 0, z))) {
                        paint(level, x, z, rnd);
                    }
                }
            }
        }
    }

    private static void paint(ServerLevel level, int x, int z, Random rnd) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (top < level.getMinBuildHeight()) {
            return;
        }
        BlockPos pos = new BlockPos(x, top, z);
        BlockState st = level.getBlockState(pos);

        if (st.is(Blocks.WATER)) {
            if (RoadConfig.BUILD_BRIDGES.get() && !level.getBiome(pos).is(BiomeTags.IS_OCEAN)
                    && level.getBlockState(pos.above()).isAir()) {
                level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
            }
            return;
        }
        if (st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS)) {
            return;
        }
        BlockState place;
        if (st.is(BlockTags.DIRT) && !st.is(Blocks.MUD)) {
            place = rnd.nextInt(100) < 18 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
        } else if (st.is(BlockTags.SAND)) {
            place = Blocks.GRAVEL.defaultBlockState();
        } else {
            return;
        }
        level.setBlock(pos, place, 2);
        BlockPos up = pos.above();
        BlockState ub = level.getBlockState(up);
        if (!ub.isAir() && ub.getCollisionShape(level, up).isEmpty() && ub.getFluidState().isEmpty()) {
            level.setBlock(up, Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
