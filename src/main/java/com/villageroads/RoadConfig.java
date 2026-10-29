package com.villageroads;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class RoadConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue BUILD_ROADS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;
    public static final ForgeConfigSpec.IntValue MAX_LINK_DISTANCE;
    public static final ForgeConfigSpec.IntValue LINKS_PER_VILLAGE;
    public static final ForgeConfigSpec.IntValue VILLAGE_EXCLUSION;
    public static final ForgeConfigSpec.IntValue ROAD_WIDTH;
    public static final ForgeConfigSpec.IntValue CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue BUILD_BRIDGES;
    public static final ForgeConfigSpec.IntValue SETTLEMENT_RADIUS;
    public static final ForgeConfigSpec.BooleanValue GENERATE_CHUNKS;

    public static final ForgeConfigSpec.BooleanValue SPEED_BONUS_ENABLED;
    public static final ForgeConfigSpec.DoubleValue SPEED_BONUS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("roads");
        BUILD_ROADS = b.comment("Build roads between neighbouring villages / settlements.").define("buildRoads", true);
        DIMENSIONS = b.comment("Dimensions where roads are built.")
                .defineList("dimensions", List.of("minecraft:overworld"), o -> o instanceof String);
        MAX_LINK_DISTANCE = b.comment("Villages farther apart than this (blocks) are not connected.")
                .defineInRange("maxLinkDistance", 700, 64, 3000);
        LINKS_PER_VILLAGE = b.comment("Each new village is connected to this many nearest known villages.")
                .defineInRange("linksPerVillage", 2, 1, 6);
        VILLAGE_EXCLUSION = b.comment("Roads stop this many blocks before a village centre (villages have their own paths).")
                .defineInRange("villageExclusion", 30, 0, 100);
        ROAD_WIDTH = b.comment("Width of the road in blocks (1-5).").defineInRange("roadWidth", 3, 1, 5);
        CHUNKS_PER_TICK = b.comment("How many chunks per tick may get road blocks (lower = less lag spikes).")
                .defineInRange("chunksPerTick", 2, 1, 16);
        BUILD_BRIDGES = b.comment("Lay simple plank bridges over rivers and lakes (never over oceans).")
                .define("buildBridges", true);
        SETTLEMENT_RADIUS = b.comment("Village parts closer than this (blocks) count as ONE settlement and are not linked to each other.")
                .defineInRange("settlementRadius", 40, 16, 500);
        GENERATE_CHUNKS = b.comment("Generate the chunks between two villages so the whole road appears at once (a few chunks per tick). If false, the road grows only where chunks are already loaded.")
                .define("generateChunks", true);
        b.pop();
        b.push("speed");
        SPEED_BONUS_ENABLED = b.comment("Players (and the mount they ride) walk faster on road blocks.")
                .define("enabled", true);
        SPEED_BONUS = b.comment("Speed bonus on roads (0.10 = +10%).").defineInRange("bonus", 0.10D, 0.0D, 1.0D);
        b.pop();
        SPEC = b.build();
    }
}
