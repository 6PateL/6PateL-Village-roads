package com.villageroads;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(VillageRoads.MODID)
public class VillageRoads {
    public static final String MODID = "villageroads";

    public VillageRoads() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RoadConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new RoadManager());
        MinecraftForge.EVENT_BUS.register(new RoadSpeed());
    }
}
