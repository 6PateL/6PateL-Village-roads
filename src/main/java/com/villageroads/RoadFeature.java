package com.villageroads;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws the part of every road that crosses the chunk being generated. It is stateless and deterministic: the village
 * positions come from the world seed (see VillageLocator), the road network between them is computed the same way in
 * every chunk, and only columns of the current chunk are written. Runs once per chunk during generation, never again.
 */
public class RoadFeature extends Feature<NoneFeatureConfiguration> {
    private record Edge(VillageLocator.Village a, VillageLocator.Village b) {}

    private static final java.util.concurrent.atomic.AtomicInteger LOGGED = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger CALLS = new java.util.concurrent.atomic.AtomicInteger();

    public RoadFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!RoadConfig.ENABLED.get()) {
            return false;
        }
        if (CALLS.getAndIncrement() == 0) {
            VillageRoads.LOGGER.info("Road feature is being placed by world generation (first call)");
        }
        WorldGenLevel level = ctx.level();
        ServerLevel server = level.getLevel();
        BlockPos origin = ctx.origin();
        int x0 = origin.getX() & ~15;
        int z0 = origin.getZ() & ~15;
        int max = RoadConfig.MAX_LINK_BLOCKS.get();
        int width = RoadConfig.ROAD_WIDTH.get();
                int k = RoadConfig.LINKS_PER_VILLAGE.get();

        int cxm = x0 + 8;
        int czm = z0 + 8;
        List<VillageLocator.Village> all = VillageLocator.near(server, cxm, czm, 2 * max + 64);
        if (all.size() < 2) {
            return false;
        }
        Set<Edge> edges = new HashSet<>();
        for (VillageLocator.Village a : all) {
            if (dist2(a.x(), a.z(), cxm, czm) > sq(max + 32L)) {
                continue;
            }
            List<VillageLocator.Village> others = new ArrayList<>();
            for (VillageLocator.Village b : all) {
                if (b != a && dist2(a.x(), a.z(), b.x(), b.z()) <= sq(max)) {
                    others.add(b);
                }
            }
            others.sort(Comparator.comparingLong(b -> dist2(a.x(), a.z(), b.x(), b.z())));
            for (int i = 0; i < Math.min(k, others.size()); i++) {
                VillageLocator.Village b = others.get(i);
                boolean ordered = a.x() < b.x() || (a.x() == b.x() && a.z() < b.z());
                edges.add(ordered ? new Edge(a, b) : new Edge(b, a));
            }
        }

        if (LOGGED.getAndIncrement() < 8) {
            VillageRoads.LOGGER.info("Road feature at chunk {},{}: {} villages nearby, {} road links", x0 >> 4, z0 >> 4,
                    all.size(), edges.size());
        }
        boolean[] done = new boolean[256];
        boolean any = false;
        for (Edge e : edges) {
            any |= paint(level, e, x0, z0, width, done, server.getSeed());
        }
        return any;
    }

    private boolean paint(WorldGenLevel level, Edge e, int x0, int z0, int width,
                          boolean[] done, long worldSeed) {
        double ax = e.a().x(), az = e.a().z(), bx = e.b().x(), bz = e.b().z();
        double margin = 6 + width;
        if (Math.max(ax, bx) + margin < x0 || Math.min(ax, bx) - margin > x0 + 15
                || Math.max(az, bz) + margin < z0 || Math.min(az, bz) - margin > z0 + 15) {
            return false;
        }
        double dx = bx - ax, dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        int ca = e.a().clearance(), cb = e.b().clearance();
        if (len <= ca + cb + 8) {
            return false;
        }
        double ux = dx / len, uz = dz / len;
        double nx = -uz, nz = ux;
        long edgeSeed = mix(worldSeed ^ mix(e.a().x() * 31L + e.a().z()) ^ mix(e.b().x() * 17L + e.b().z() * 131L));
        double phase = (edgeSeed & 1023) / 1023.0 * Math.PI * 2;
        RandomSource rnd = RandomSource.create(edgeSeed ^ (x0 * 341873128712L + z0 * 132897987541L));
        double radius = width / 2.0 + 0.5;
        boolean placedAny = false;

        for (double s = ca; s <= len - cb; s += 0.7) {
            double taper = Math.min(1.0, Math.min(s / 60.0, (len - s) / 60.0));
            double off = 4.0 * Math.sin(s / 26.0 + phase) * taper;
            double px = ax + ux * s + nx * off;
            double pz = az + uz * s + nz * off;
            if (px < x0 - radius - 1 || px > x0 + 16 + radius || pz < z0 - radius - 1 || pz > z0 + 16 + radius) {
                continue;
            }
            int ix0 = Math.max(x0, (int) Math.floor(px - radius));
            int ix1 = Math.min(x0 + 15, (int) Math.ceil(px + radius));
            int iz0 = Math.max(z0, (int) Math.floor(pz - radius));
            int iz1 = Math.min(z0 + 15, (int) Math.ceil(pz + radius));
            for (int x = ix0; x <= ix1; x++) {
                for (int z = iz0; z <= iz1; z++) {
                    int idx = (x - x0) * 16 + (z - z0);
                    if (done[idx]) {
                        continue;
                    }
                    double ddx = x + 0.5 - px, ddz = z + 0.5 - pz;
                    double d2 = ddx * ddx + ddz * ddz;
                    if (d2 > radius * radius) {
                        continue;
                    }
                    if (d2 > (radius - 0.8) * (radius - 0.8) && rnd.nextFloat() < 0.15F) {
                        continue;
                    }
                    done[idx] = true;
                    placedAny |= surface(level, x, z, rnd);
                }
            }
        }
        return placedAny;
    }

    private boolean surface(WorldGenLevel level, int x, int z, RandomSource rnd) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int min = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (y > min) {
            pos.setY(y);
            BlockState s = level.getBlockState(pos);
            if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.SNOW)
                    || (s.getFluidState().isEmpty() && !s.blocksMotion())) {
                y--;
            } else {
                break;
            }
        }
        if (y <= min) {
            return false;
        }
        pos.setY(y);
        BlockState ground = level.getBlockState(pos);

        if (ground.getFluidState().is(FluidTags.WATER)) {
            if (!RoadConfig.BUILD_BRIDGES.get() || level.getBiome(pos).is(BiomeTags.IS_OCEAN)) {
                return false;
            }
            boolean shallow = false;
            BlockPos.MutableBlockPos below = pos.mutable();
            for (int i = 1; i <= 4; i++) {
                below.setY(y - i);
                if (level.getFluidState(below).isEmpty()) {
                    shallow = !level.getBlockState(below).isAir();
                    break;
                }
            }
            if (!shallow) {
                return false;
            }
            level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            clearAbove(level, pos);
            return true;
        }

        BlockState place = null;
        if (ground.is(BlockTags.DIRT) || ground.is(Blocks.DIRT_PATH) || ground.is(Blocks.FARMLAND)) {
            float r = rnd.nextFloat();
            place = r < 0.18F ? Blocks.GRAVEL.defaultBlockState()
                    : r < 0.30F ? Blocks.COARSE_DIRT.defaultBlockState()
                    : VillageRoads.VILLAGE_PATH.get().defaultBlockState();
        } else if (ground.is(BlockTags.SAND)) {
            place = Blocks.GRAVEL.defaultBlockState();
        }
        if (place == null) {
            return false;
        }
        level.setBlock(pos, place, Block.UPDATE_CLIENTS);
        clearAbove(level, pos);
        return true;
    }

    private static void clearAbove(WorldGenLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 1; i <= 3; i++) {
            p.setY(pos.getY() + i);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty()) {
                continue;
            }
            if (s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(Blocks.SNOW)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static long dist2(long ax, long az, long bx, long bz) {
        long dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz;
    }

    private static long sq(long v) {
        return v * v;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
