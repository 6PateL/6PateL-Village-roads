package com.villageroads;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Roads between villages, generated purely in the world generation pipeline (a Feature added to the biomes through a
 * Forge biome modifier). No tick event of any kind is used anywhere in this mod.
 */
@Mod(VillageRoads.MODID)
public class VillageRoads {
    public static final String MODID = "villageroads";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);

    public static final RegistryObject<Block> VILLAGE_PATH = BLOCKS.register("village_path", VillagePathBlock::new);
    public static final RegistryObject<Item> VILLAGE_PATH_ITEM = ITEMS.register("village_path",
            () -> new BlockItem(VILLAGE_PATH.get(), new Item.Properties()));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> VILLAGE_ROAD = FEATURES.register("village_road",
            () -> new RoadFeature(NoneFeatureConfiguration.CODEC));

    public VillageRoads() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RoadConfig.SPEC);
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        FEATURES.register(bus);
        bus.addListener(VillageRoads::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(VILLAGE_PATH_ITEM);
        }
    }
}
