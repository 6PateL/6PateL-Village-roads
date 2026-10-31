package com.villageroads;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class RoadConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_SETS;
    public static final ForgeConfigSpec.IntValue MAX_LINK_BLOCKS;
    public static final ForgeConfigSpec.IntValue LINKS_PER_VILLAGE;
    public static final ForgeConfigSpec.IntValue VILLAGE_CLEARANCE;
    public static final ForgeConfigSpec.IntValue ROAD_WIDTH;
    public static final ForgeConfigSpec.BooleanValue BUILD_BRIDGES;
    public static final ForgeConfigSpec.BooleanValue RECRUITS_VILLAGES;
    public static final ForgeConfigSpec.IntValue RECRUITS_CLEARANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Roads are made while chunks are generated, so they appear only in chunks generated AFTER the mod was added.").push("roads");
        ENABLED = b.define("enabled", true);
        STRUCTURE_SETS = b.comment("Structure sets whose placements count as villages (modded villages can be added).")
                .defineList("villageStructureSets", List.of("minecraft:villages"), o -> o instanceof String);
        MAX_LINK_BLOCKS = b.comment("Villages farther apart than this (blocks) are not connected.")
                .defineInRange("maxLinkBlocks", 900, 128, 2500);
        LINKS_PER_VILLAGE = b.comment("Every village is connected to this many of its nearest neighbours.")
                .defineInRange("linksPerVillage", 2, 1, 4);
        VILLAGE_CLEARANCE = b.comment("The road stops this many blocks before a village centre.")
                .defineInRange("villageClearance", 36, 0, 100);
        ROAD_WIDTH = b.comment("Width of the road in blocks (1-5).").defineInRange("roadWidth", 3, 1, 5);
        BUILD_BRIDGES = b.comment("Lay plank bridges over rivers (never over oceans).").define("buildBridges", true);
        RECRUITS_VILLAGES = b.comment("Also connect the villages of the Recruits Villages mod (their sites are predicted from the world seed).")
                .define("recruitsVillages", true);
        RECRUITS_CLEARANCE = b.comment("Road stops this many blocks before a Recruits Villages centre.")
                .defineInRange("recruitsClearance", 56, 0, 120);
        b.pop();
        SPEC = b.build();
    }
}
