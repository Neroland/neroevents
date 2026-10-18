package za.co.neroland.neroevents.storm.client;

import java.util.Optional;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeLayer;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Client only. Adds the storm's sky and fog to a client level's environment-attribute system: time-based
 * layers over the sky colour, the fog colour and the fog distances, each a pass-through while no storm is
 * visible in that level's dimension. Installed once per {@link ClientLevel} by the common client mixin
 * (the attribute system is built in the level's constructor), which also makes this the "new level" point:
 * the client storm mirror is cleared and the server re-syncs it.
 *
 * <p>The colour attributes changed type across 26.x (packed {@code int} through 26.2, {@code Vector3fc} from
 * 26.3); the colour layer handles both at runtime so one source compiles on every cell.
 */
public final class StormRenderHooks {

    /**
     * Whether the layer hook has ever run. The hook is optional (a Minecraft version without the target method
     * just has no storm tint), so the client logs once when a storm is visible and this is still false.
     */
    private static volatile boolean installed;

    private StormRenderHooks() {
    }

    /** Whether {@link #addLayers} has run at least once this session. */
    public static boolean installed() {
        return installed;
    }

    /** Called from {@code ClientLevel#addEnvironmentAttributeLayers}. */
    public static void addLayers(ClientLevel level, EnvironmentAttributeSystem.Builder builder) {
        installed = true;
        StormClient.reset();
        Identifier dimension = level.dimension().identifier();
        addColour(builder, EnvironmentAttributes.SKY_COLOR, dimension);
        addColour(builder, EnvironmentAttributes.FOG_COLOR, dimension);
        builder.addTimeBasedLayer(EnvironmentAttributes.FOG_START_DISTANCE, (value, tick) -> {
            if (value == null) {
                return null;
            }
            Optional<ClientStormState.View> view = ClientStormState.view(dimension);
            if (view.isEmpty()) {
                return value;
            }
            return Float.valueOf(StormLook.fogStart(value.floatValue(), view.get().variant(), view.get().strength()));
        });
        addFogEnd(builder, EnvironmentAttributes.FOG_END_DISTANCE, dimension);
        addFogEnd(builder, EnvironmentAttributes.SKY_FOG_END_DISTANCE, dimension);
        addFogEnd(builder, EnvironmentAttributes.CLOUD_FOG_END_DISTANCE, dimension);
    }

    private static void addFogEnd(EnvironmentAttributeSystem.Builder builder, EnvironmentAttribute<Float> attribute,
            Identifier dimension) {
        builder.addTimeBasedLayer(attribute, (value, tick) -> {
            if (value == null) {
                return null;
            }
            Optional<ClientStormState.View> view = ClientStormState.view(dimension);
            if (view.isEmpty()) {
                return value;
            }
            return Float.valueOf(StormLook.fogEnd(value.floatValue(), view.get().variant(), view.get().strength()));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addColour(EnvironmentAttributeSystem.Builder builder, EnvironmentAttribute<?> attribute,
            Identifier dimension) {
        EnvironmentAttribute<Object> raw = (EnvironmentAttribute) attribute;
        EnvironmentAttributeLayer.TimeBased<Object> layer = (value, tick) -> tint(dimension, value);
        builder.addTimeBasedLayer(raw, layer);
    }

    /** Tints a packed ARGB {@code Integer} or a {@code Vector3fc}; anything else passes through. */
    static Object tint(Identifier dimension, Object value) {
        Optional<ClientStormState.View> view = ClientStormState.view(dimension);
        if (view.isEmpty() || value == null) {
            return value;
        }
        float strength = view.get().strength();
        if (value instanceof Integer argb) {
            return StormLook.tintArgb(argb, view.get().variant(), strength);
        }
        if (value instanceof Vector3fc colour) {
            return new Vector3f(
                    StormLook.tintChannel(colour.x(), 16, view.get().variant(), strength),
                    StormLook.tintChannel(colour.y(), 8, view.get().variant(), strength),
                    StormLook.tintChannel(colour.z(), 0, view.get().variant(), strength));
        }
        return value;
    }
}
