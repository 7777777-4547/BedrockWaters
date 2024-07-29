package retr0.bedrockwaters.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import retr0.bedrockwaters.BedrockWaters;
import retr0.bedrockwaters.config.BedrockWatersConfig;
import retr0.carrotconfig.config.ConfigSavedCallback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.util.Map.entry;
//import static net.fabricmc.fabric.api.tag.convention.v1.ConventionalBiomeTags.*;
import static net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.*;
import static net.minecraft.registry.tag.BiomeTags.IS_HILL;
import static net.minecraft.registry.tag.BiomeTags.PRODUCES_CORALS_FROM_BONEMEAL;
import static retr0.bedrockwaters.util.BiomeProperties.VANILLA_BIOME_PROPERTIES;

/**
 * Utility for generating and handling custom water properties for biomes.
 */
@Environment(EnvType.CLIENT)
public final class WaterPropertiesManager {
    /**
     * @see <a href="https://minecraft.wiki/w/Water#Bedrock_Edition">Water#Bedrock Edition</a>
     */
    public static final BiomeProperties DEFAULT_VANILLA_PROPERTIES = new BiomeProperties("#3F76E4", "#050533", 15);
    public static final BiomeProperties DEFAULT_BEDROCK_PROPERTIES = new BiomeProperties("#44AFF5", "#44AFF5", 15);
    public static final int DEFAULT_WATER_FOG_DISTANCE = 15;

    /**
     * Cache for generated biome properties.
     */
    private static ConcurrentMap<Identifier, BiomeProperties> propertyCache = new ConcurrentHashMap<>();

    private static HashSet<Identifier> overwrittenBiomeIds = new HashSet<>();

    /**
     * Handler mappings to generate customized biome properties for unknown biomes.
     */
    private static final HandlerMap<TagKey<Biome>, RegistryEntry<Biome>, BiomeProperties> customizedProperties = new HandlerMap<>(Map.ofEntries(
        entry(IS_OCEAN, b -> {
            if (b.isIn(IS_AQUATIC_ICY))
                return VANILLA_BIOME_PROPERTIES.get(b.isIn(IS_DEEP_OCEAN) ? BiomeKeys.DEEP_FROZEN_OCEAN : BiomeKeys.FROZEN_OCEAN);
            else if (b.isIn(PRODUCES_CORALS_FROM_BONEMEAL))
                return VANILLA_BIOME_PROPERTIES.get(b.isIn(IS_DEEP_OCEAN) ? BiomeKeys.DEEP_LUKEWARM_OCEAN : BiomeKeys.WARM_OCEAN);
            else
                return VANILLA_BIOME_PROPERTIES.get(b.isIn(IS_DEEP_OCEAN) ? BiomeKeys.DEEP_OCEAN : BiomeKeys.OCEAN);
        }),
        entry(IS_FOREST, b -> {
            if (b.isIn(IS_HILL))
                return VANILLA_BIOME_PROPERTIES.get(BiomeKeys.WINDSWEPT_FOREST);
            else if (b.isIn(IS_COLD))
                return VANILLA_BIOME_PROPERTIES.get(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
            else
                return VANILLA_BIOME_PROPERTIES.get(BiomeKeys.OLD_GROWTH_BIRCH_FOREST);
        }),
        entry(IS_MOUNTAIN, b -> {
            if (b.isIn(IS_ICY))
                return VANILLA_BIOME_PROPERTIES.get(BiomeKeys.FROZEN_PEAKS);
            if (b.isIn(IS_MOUNTAIN_SLOPE))
                return VANILLA_BIOME_PROPERTIES.get(BiomeKeys.SNOWY_SLOPES);
            else if (b.isIn(IS_MOUNTAIN_PEAK))
                return VANILLA_BIOME_PROPERTIES.get(BiomeKeys.JAGGED_PEAKS);
            else
                return VANILLA_BIOME_PROPERTIES.get(BiomeKeys.MEADOW);
        }),
        entry(IS_RIVER,             b -> VANILLA_BIOME_PROPERTIES.get(
            b.isIn(IS_AQUATIC_ICY) ? BiomeKeys.FROZEN_RIVER : BiomeKeys.RIVER)),
        entry(IS_BEACH,             b -> VANILLA_BIOME_PROPERTIES.get(
            b.isIn(IS_SNOWY) ? BiomeKeys.SNOWY_BEACH : BiomeKeys.BEACH)),
        entry(IS_TAIGA,             b -> VANILLA_BIOME_PROPERTIES.get(
            b.isIn(IS_SNOWY) ? BiomeKeys.SNOWY_TAIGA : BiomeKeys.TAIGA)),
        entry(IS_PLAINS,            b -> VANILLA_BIOME_PROPERTIES.get(
            b.isIn(IS_VEGETATION_DENSE) ? BiomeKeys.SUNFLOWER_PLAINS : BiomeKeys.PLAINS)),
        entry(IS_HILL,     b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.WINDSWEPT_HILLS)),
        entry(IS_SWAMP,             b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.SWAMP)),
        entry(IS_JUNGLE,            b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.JUNGLE)),
        entry(IS_SAVANNA,           b -> VANILLA_BIOME_PROPERTIES.get(
            b.isIn(IS_VEGETATION_SPARSE) ? BiomeKeys.SAVANNA_PLATEAU : BiomeKeys.SAVANNA)),
        entry(IS_BADLANDS,          b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.BADLANDS)),
        entry(IS_DESERT,            b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.DESERT)),
        entry(IS_CAVE,             b -> VANILLA_BIOME_PROPERTIES.get(
            b.isIn(IS_WET) ? BiomeKeys.LUSH_CAVES : BiomeKeys.DRIPSTONE_CAVES)),
        entry(IS_NETHER,         b -> VANILLA_BIOME_PROPERTIES.get(
            b.isIn(IS_NETHER_FOREST) ? BiomeKeys.CRIMSON_FOREST : BiomeKeys.BASALT_DELTAS)),
        entry(IS_END,        b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.THE_END)),

        // For "themed biomes" we check for more generic tags.
        entry(IS_ICY,               b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.ICE_SPIKES)),
        entry(IS_SNOWY,             b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.SNOWY_PLAINS)),
        entry(IS_FLORAL,            b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.FLOWER_FOREST)),
        entry(IS_MUSHROOM,          b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.MUSHROOM_FIELDS)),

        // For all other cases, we check climate tags.
        entry(IS_DRY,       b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.DESERT)),
        entry(IS_HOT,       b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.JUNGLE)),
        entry(IS_TEMPERATE, b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.BIRCH_FOREST)),
        entry(IS_COLD,      b -> VANILLA_BIOME_PROPERTIES.get(BiomeKeys.TAIGA))
    ), DEFAULT_BEDROCK_PROPERTIES);

    public static void init() {
        // Load configured biome property overrides
        var biomeIds = new HashSet<String>();
        biomeIds.addAll(BedrockWatersConfig.waterColorOverrides.keySet());
        biomeIds.addAll(BedrockWatersConfig.waterFogDistanceOverrides.keySet());
        biomeIds.addAll(BedrockWatersConfig.waterOpacityOverrides.keySet());
        overwrittenBiomeIds = biomeIds.stream().map(Identifier::tryParse).collect(Collectors.toCollection(HashSet::new));

        // Fill property cache with vanilla biome properties that are not overwritten
        propertyCache = new ConcurrentHashMap<>();
        VANILLA_BIOME_PROPERTIES.forEach((biomeKey, properties) -> {
            var biomeId = biomeKey.getValue();
            if (!overwrittenBiomeIds.contains(biomeId)) propertyCache.put(biomeId, properties);
        });

        // Register a new listener for the disconnection of the client play network handler. Whenever the client exits
        // a world, we regenerate the cache containing generated biome properties as it may contain data pack biomes.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> init());
        ConfigSavedCallback.EVENT.register(configClass -> {
            init();
            var client = MinecraftClient.getInstance();
            // TODO: Find a way to make this not run every time any setting is changed!
            if (client.world != null) client.execute(client.worldRenderer::reload);
        });
    }


    /**
     * Gets the Bedrock Edition water properties for the target biome; or, if the biome is non-vanilla but has the
     * default vanilla water color, retrieves a cached customized water color dependent on the biome's tags.
     *
     * @param biomeKey The {@link RegistryKey<Biome>} associated with the target biome.
     * @param biomeRef The {@link RegistryEntry<Biome>} associated with the target biome.
     * @return A {@link BiomeProperties} reference containing the updated water properties.
     */
    public static BiomeProperties getWaterProperties(RegistryKey<Biome> biomeKey, RegistryEntry<Biome> biomeRef) {
        var biomeId = biomeKey.getValue();
        var properties = propertyCache.get(biomeId);
        var biome = biomeRef.value();

        // If the biome does yet have a cached property, generate properties and add them to the cache.
        if (properties == null) {
            properties = customizedProperties.handle(biomeRef, biomeRef::isIn);

            // For biomes which do not have default vanilla properties, we want to instead use their assigned water and
            // water fog colors but still keep the generated water fog distance (as it's a property not associated
            // with biome instances).
            if (!hasDefaultProperties(biome))
                properties = properties.override(biome.getWaterColor(), biome.getWaterFogColor(), null, null);
            if (overwrittenBiomeIds.contains(biomeId)) {
                var waterColor = BedrockWatersConfig.waterColorOverrides.getOrDefault(biomeId.toString(), null);
                var waterFogDistance = BedrockWatersConfig.waterFogDistanceOverrides.getOrDefault(biomeId.toString(), null);
                var waterOpacity = BedrockWatersConfig.waterOpacityOverrides.getOrDefault(biomeId.toString(), null);
                properties = properties.override(waterColor, waterColor, waterFogDistance, waterOpacity);
            }

            propertyCache.putIfAbsent(biomeId, properties);
        }

        return properties;
    }



    /**
     * @see WaterPropertiesManager#getWaterProperties(RegistryKey, RegistryEntry)
     */
    public static BiomeProperties getWaterProperties(RegistryEntry<Biome> biomeRef) {
        var biomeKey = biomeRef.getKey();

        return biomeKey.isPresent() ? getWaterProperties(biomeKey.get(), biomeRef) : DEFAULT_BEDROCK_PROPERTIES;
    }



    /**
     * Determines whether a biome has the default vanilla water and water fog colors.
     *
     * @param biome The target biome.
     * @return {@code true} if {@code biome} has the default vanilla water and water fog color; otherwise,
     * {@code false}.
     */
    public static boolean hasDefaultProperties(Biome biome) {
        return biome.getWaterColor() == DEFAULT_VANILLA_PROPERTIES.waterColor() &&
            biome.getWaterFogColor() == DEFAULT_VANILLA_PROPERTIES.waterFogColor();
    }
}
