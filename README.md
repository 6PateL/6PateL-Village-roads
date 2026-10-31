# Village Roads (Forge 1.20.1)

Дороги між селами, що генеруються **лише під час генерації світу**. У моді немає жодного tick-івенту
(ServerTick / LevelTick / PlayerTick), тому на TPS він не впливає.

## Архітектура
- `VillageRoads` — @Mod, DeferredRegister для блоку, предмета та `Feature` (`Registries.FEATURE`).
- `RoadFeature` — `Feature<NoneFeatureConfiguration>`; для чанка, що генерується, малює лише його частину доріг.
- `VillageLocator` — знаходить майбутні села з сіда: `RandomSpreadStructurePlacement` + перевірка біома через
  `BiomeSource`. Нічого не завантажує й не генерує; результати кешуються по регіонах.
- `VillagePathBlock` — блок стежки з `speedFactor(1.075)` (≈ +10% швидкості) без жодного коду на тік.
- `RoadConfig` — налаштування (recruitsVillages, recruitsClearance, maxLinkBlocks, linksPerVillage, villageClearance, roadWidth, buildBridges, villageStructureSets).

## Datapack JSON
- `worldgen/configured_feature/village_road.json`
- `worldgen/placed_feature/village_road.json`
- `forge/biome_modifier/add_village_road.json` (додає фічу в усі біоми Оверворлда, крок `top_layer_modification`)

## Важливо
- Дороги з'являються тільки в чанках, згенерованих **після** додавання мода (новий світ або нові чанки).
- Положення сіл передбачається з сіда та біома, тому зрідка можлива дорога до села, яке не з'явилось.
- Старі класи (RoadManager, RoadBuilder, RoadData, RoadSpeed) та залежність від Realistic Terrain прибрано.
- Села Recruits Villages (мод ставить їх сам, у рантаймі, а не як vanilla-структури) передбачаються тим самим
  детермінованим розрахунком (сітка, salt, біом, перевірка рівності місцевості) — читається з конфігу того мода через reflection.
