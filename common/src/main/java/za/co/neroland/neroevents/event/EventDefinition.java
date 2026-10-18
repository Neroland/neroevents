package za.co.neroland.neroevents.event;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * One event definition, loaded from {@code data/<namespace>/neroevents/events/<path>.json} (id
 * {@code <namespace>:<path>}). Immutable: a running instance holds a reference to the definition it was
 * created from, so a {@code /reload} never changes an event that is already under way.
 *
 * <p>{@link #CODEC} decodes the JSON as written ({@link Raw}, snake_case keys);
 * {@code EventDefinitions.parse} turns it into a definition, resetting out-of-range optional fields with a
 * warning and dropping the file when a required field is missing or a rule is broken. The payload type
 * and its parsed config are attached by {@code EventDefinitions.validate}.
 */
public record EventDefinition(Identifier id, Identifier type, EventCategory category, Severity severity,
        Display display, int weight, long cooldownTicks, int announceTicks, int durationTicks, boolean enabled,
        boolean destructive, boolean consentExempt, boolean calmExempt, boolean locationPublic,
        EventConditions conditions, EventBounds bounds, RewardSpec rewards, JsonElement payloadJson,
        @Nullable PayloadType<?, ?> payloadType, @Nullable PayloadConfig payloadConfig, List<String> loadWarnings) {

    public static final long DEFAULT_COOLDOWN_TICKS = 72000L;
    public static final int DEFAULT_ANNOUNCE_TICKS = 6000;
    public static final int DEFAULT_DURATION_TICKS = 12000;

    /** Every top-level key a definition understands. */
    public static final Set<String> KEYS = Set.of("type", "category", "severity", "display", "weight",
            "cooldown_ticks", "announce_ticks", "duration_ticks", "enabled", "destructive", "consent_exempt",
            "calm_exempt", "location_public", "conditions", "bounds", "payload", "rewards");

    /** Translation keys for the event's title, its "what to do" line and its announcement. */
    public record Display(String title, String whatToDo, String announce) {

        public static final Set<String> KEYS = Set.of("title", "what_to_do", "announce");

        public static final Codec<Display> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.optionalFieldOf("title", "").forGetter(Display::title),
                Codec.STRING.optionalFieldOf("what_to_do", "").forGetter(Display::whatToDo),
                Codec.STRING.optionalFieldOf("announce", "").forGetter(Display::announce)
        ).apply(inst, Display::new));
    }

    /** The boolean flags, grouped so the codec stays within the builder's arity. */
    public record Flags(boolean enabled, boolean destructive, boolean consentExempt, boolean calmExempt,
            boolean locationPublic) {

        static final MapCodec<Flags> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Flags::enabled),
                Codec.BOOL.optionalFieldOf("destructive", false).forGetter(Flags::destructive),
                Codec.BOOL.optionalFieldOf("consent_exempt", false).forGetter(Flags::consentExempt),
                Codec.BOOL.optionalFieldOf("calm_exempt", false).forGetter(Flags::calmExempt),
                Codec.BOOL.optionalFieldOf("location_public", false).forGetter(Flags::locationPublic)
        ).apply(inst, Flags::new));
    }

    /** The JSON as written, before normalisation. */
    public record Raw(Identifier type, String category, Optional<String> severity, Display display,
            Optional<Integer> weight, long cooldownTicks, int announceTicks, int durationTicks, Flags flags,
            EventConditions conditions, EventBounds bounds, JsonElement payload, RewardSpec.Raw rewards) {
    }

    static final Codec<JsonElement> JSON = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
            json -> new Dynamic<>(JsonOps.INSTANCE, json));

    private static final RewardSpec.Raw NO_REWARDS = new RewardSpec.Raw(0L, "contribution", 0.05D, List.of(),
            List.of(), false, true);

    /** The definition schema (snake_case keys). */
    public static final Codec<Raw> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("type").forGetter(Raw::type),
            Codec.STRING.fieldOf("category").forGetter(Raw::category),
            Codec.STRING.optionalFieldOf("severity").forGetter(Raw::severity),
            Display.CODEC.optionalFieldOf("display", new Display("", "", "")).forGetter(Raw::display),
            Codec.INT.optionalFieldOf("weight").forGetter(Raw::weight),
            Codec.LONG.optionalFieldOf("cooldown_ticks", DEFAULT_COOLDOWN_TICKS).forGetter(Raw::cooldownTicks),
            Codec.INT.optionalFieldOf("announce_ticks", DEFAULT_ANNOUNCE_TICKS).forGetter(Raw::announceTicks),
            Codec.INT.optionalFieldOf("duration_ticks", DEFAULT_DURATION_TICKS).forGetter(Raw::durationTicks),
            Flags.MAP_CODEC.forGetter(Raw::flags),
            EventConditions.CODEC.optionalFieldOf("conditions", EventConditions.DEFAULT).forGetter(Raw::conditions),
            EventBounds.CODEC.optionalFieldOf("bounds", EventBounds.DEFAULT).forGetter(Raw::bounds),
            JSON.optionalFieldOf("payload", new JsonObject()).forGetter(Raw::payload),
            RewardSpec.RAW_CODEC.optionalFieldOf("rewards", NO_REWARDS).forGetter(Raw::rewards)
    ).apply(inst, Raw::new));

    public EventDefinition {
        loadWarnings = List.copyOf(loadWarnings);
    }

    /** The payload type with its generic parameters erased for the engine's calls. */
    @SuppressWarnings("unchecked")
    public PayloadType<PayloadConfig, PayloadState> payload() {
        if (payloadType == null) {
            throw new IllegalStateException("Event definition " + id + " has no resolved payload type");
        }
        return (PayloadType<PayloadConfig, PayloadState>) payloadType;
    }

    /** A copy with the payload type and config resolved at validation. */
    public EventDefinition withPayload(PayloadType<?, ?> type, PayloadConfig config, List<String> warnings) {
        return new EventDefinition(id, this.type, category, severity, display, weight, cooldownTicks, announceTicks,
                durationTicks, enabled, destructive, consentExempt, calmExempt, locationPublic, conditions, bounds,
                rewards, payloadJson, type, config, warnings);
    }

    public EventDefinition withRewards(RewardSpec newRewards) {
        return new EventDefinition(id, type, category, severity, display, weight, cooldownTicks, announceTicks,
                durationTicks, enabled, destructive, consentExempt, calmExempt, locationPublic, conditions, bounds,
                newRewards, payloadJson, payloadType, payloadConfig, loadWarnings);
    }
}
