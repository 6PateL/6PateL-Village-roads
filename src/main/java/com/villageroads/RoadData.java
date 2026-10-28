package com.villageroads;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Known villages, planned road links, and which chunks already received their road blocks. */
public class RoadData extends SavedData {
    private static final String NAME = "villageroads_data";

    public static final class Village {
        public String key;
        public String dim;
        public BlockPos center;
    }

    public static final class Link {
        public String dim;
        public BlockPos a;
        public BlockPos b;
        public long seed;
        public final Set<Long> done = new HashSet<>();
    }

    public final List<Village> villages = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();

    public static RoadData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RoadData::load, RoadData::new, NAME);
    }

    public boolean hasVillage(String key) {
        for (Village v : villages) {
            if (v.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasLink(BlockPos a, BlockPos b) {
        for (Link l : links) {
            if ((l.a.equals(a) && l.b.equals(b)) || (l.a.equals(b) && l.b.equals(a))) {
                return true;
            }
        }
        return false;
    }

    public static RoadData load(CompoundTag tag) {
        RoadData d = new RoadData();
        ListTag vs = tag.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < vs.size(); i++) {
            CompoundTag c = vs.getCompound(i);
            Village v = new Village();
            v.key = c.getString("key");
            v.dim = c.getString("dim");
            v.center = BlockPos.of(c.getLong("c"));
            d.villages.add(v);
        }
        ListTag ls = tag.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < ls.size(); i++) {
            CompoundTag c = ls.getCompound(i);
            Link l = new Link();
            l.dim = c.getString("dim");
            l.a = BlockPos.of(c.getLong("a"));
            l.b = BlockPos.of(c.getLong("b"));
            l.seed = c.getLong("seed");
            for (long x : c.getLongArray("done")) {
                l.done.add(x);
            }
            d.links.add(l);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag vs = new ListTag();
        for (Village v : villages) {
            CompoundTag c = new CompoundTag();
            c.putString("key", v.key);
            c.putString("dim", v.dim);
            c.putLong("c", v.center.asLong());
            vs.add(c);
        }
        tag.put("villages", vs);
        ListTag ls = new ListTag();
        for (Link l : links) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", l.dim);
            c.putLong("a", l.a.asLong());
            c.putLong("b", l.b.asLong());
            c.putLong("seed", l.seed);
            long[] arr = new long[l.done.size()];
            int i = 0;
            for (long x : l.done) {
                arr[i++] = x;
            }
            c.put("done", new LongArrayTag(arr));
            ls.add(c);
        }
        tag.put("links", ls);
        return tag;
    }
}
