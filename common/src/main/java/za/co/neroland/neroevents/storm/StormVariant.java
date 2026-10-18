package za.co.neroland.neroevents.storm;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * The four storm variants and their per-variant table: which status effects an exposed player gets at
 * each intensity, whether the storm freezes, whether it drains power and how hard, the NeroAgriculture
 * crop modifier, and the client look (sky tint and fog range). {@link StormEffects}, {@link DrainPlan}
 * and the client read this table; none of them branches on the variant.
 *
 * <p>Pure data: no Minecraft registry is touched here, so the table is unit-testable on a plain JVM.
 * Effect names are vanilla effect paths ({@code weakness}, {@code slowness}, ...), resolved by
 * {@link StormEffects}.
 */
public enum StormVariant {

    /** Any space dimension (the solar type). Breathable air is not shelter. */
    SOLAR(1, "solar", true, 1.0D, false, new float[] {0.9F, 1.0F, 0.9F}, 0xFFB347, 48.0F, 160.0F,
            List.of(List.of(effect("weakness", 0)),
                    List.of(effect("weakness", 0), effect("hunger", 0)),
                    List.of(effect("weakness", 1), effect("hunger", 0)))),

    /** Greenxertz dust storm: visibility is the mechanic. */
    DUST(2, "dust", false, 0.0D, false, new float[] {0.6F, 0.8F, 0.7F}, 0xC9A066, 8.0F, 40.0F,
            List.of(List.of(effect("slowness", 0)),
                    List.of(effect("slowness", 0), effect("mining_fatigue", 0)),
                    List.of(effect("slowness", 1), effect("mining_fatigue", 0)))),

    /** Cindara ion squall: the strongest power drain; at intensity 3 a periodic nausea jolt. */
    ION(3, "ion", true, 1.5D, false, new float[] {0.8F, 0.9F, 0.8F}, 0x7FD7FF, 24.0F, 96.0F,
            List.of(List.of(),
                    List.of(effect("weakness", 0)),
                    List.of(effect("weakness", 0)))),

    /** Glacira cryo blizzard: vanilla freezing on exposed players. */
    CRYO(4, "cryo", false, 0.0D, true, new float[] {0.5F, 0.7F, 0.6F}, 0xBFE9FF, 12.0F, 56.0F,
            List.of(List.of(effect("slowness", 0)),
                    List.of(effect("slowness", 0)),
                    List.of(effect("slowness", 1), effect("mining_fatigue", 0))));

    /** One status effect: the vanilla effect path and its amplifier (0 = level I). */
    public record EffectSpec(String effect, int amplifier) {
    }

    /** Duration of every storm effect, in ticks (re-applied every {@link #EFFECT_INTERVAL_TICKS}). */
    public static final int EFFECT_DURATION_TICKS = 60;
    /** How often exposed players are re-evaluated and effects re-applied. */
    public static final int EFFECT_INTERVAL_TICKS = 40;
    /** The ion jolt: nausea for {@link #JOLT_DURATION_TICKS} every {@link #JOLT_PERIOD_TICKS}. */
    public static final int JOLT_PERIOD_TICKS = 400;
    public static final int JOLT_DURATION_TICKS = 40;
    /** Wire id of "no storm" in {@link StormSyncPayload}. */
    public static final byte NONE_WIRE_ID = 0;

    private static final String NEROSPACE = "nerospace";

    private final byte wireId;
    private final String serializedName;
    private final boolean drains;
    private final double drainMultiplier;
    private final boolean freezes;
    private final float[] agriculture;
    private final int skyTint;
    private final float fogStart;
    private final float fogEnd;
    private final List<List<EffectSpec>> effectsByIntensity;

    StormVariant(int wireId, String serializedName, boolean drains, double drainMultiplier, boolean freezes,
            float[] agriculture, int skyTint, float fogStart, float fogEnd, List<List<EffectSpec>> effects) {
        this.wireId = (byte) wireId;
        this.serializedName = serializedName;
        this.drains = drains;
        this.drainMultiplier = drainMultiplier;
        this.freezes = freezes;
        this.agriculture = agriculture;
        this.skyTint = skyTint;
        this.fogStart = fogStart;
        this.fogEnd = fogEnd;
        this.effectsByIntensity = effects;
    }

    private static EffectSpec effect(String name, int amplifier) {
        return new EffectSpec(name, amplifier);
    }

    /** The effects an exposed player gets at {@code intensity} (clamped to 1..3); never the ion jolt. */
    public List<EffectSpec> effects(int intensity) {
        return effectsByIntensity.get(clampIntensity(intensity) - 1);
    }

    /** The ion jolt at intensity 3 (nausea, {@link #JOLT_DURATION_TICKS} every {@link #JOLT_PERIOD_TICKS}). */
    public Optional<EffectSpec> jolt(int intensity) {
        return this == ION && clampIntensity(intensity) >= 3 ? Optional.of(effect("nausea", 0)) : Optional.empty();
    }

    /** Whether this storm drains exposed energy storages. */
    public boolean drains() {
        return drains;
    }

    /** Multiplier on the per-pass drain fraction (ion 1.5, solar 1.0; 0 when the variant does not drain). */
    public double drainMultiplier() {
        return drainMultiplier;
    }

    /** Whether exposed players freeze (vanilla powder-snow freezing). */
    public boolean freezes() {
        return freezes;
    }

    /** The NeroAgriculture cycle modifier {@code {growth, yield, environment}} while this storm is active. */
    public @Nullable float[] agriculture() {
        return agriculture.clone();
    }

    /**
     * The frozen ticks an exposed player is raised to once per {@link #EFFECT_INTERVAL_TICKS} at {@code intensity}
     * (clamped to 1..3): 100, 139, 220. Vanilla thaws 2 ticks per tick outside powder snow and only hurts at 140 or
     * more, so intensity 1 and 2 only chill (2 peaks just under the damage line) and intensity 3 stays frozen
     * across the whole interval — the powder-snow damage rate, unless the player wears freeze-immune armour.
     */
    public static int freezeLevel(int intensity) {
        return switch (clampIntensity(intensity)) {
            case 1 -> 100;
            case 2 -> 139;
            default -> 220;
        };
    }

    /** The wire id in {@link StormSyncPayload} (1..4; 0 is "no storm"). */
    public byte wireId() {
        return wireId;
    }

    /** The name used in a definition's {@code variant} field and in lang keys. */
    public String serializedName() {
        return serializedName;
    }

    /** The client sky-tint lerp target, {@code 0xRRGGBB}. */
    public int skyTint() {
        return skyTint;
    }

    /** Client fog start at full strength, in blocks. */
    public float fogStart() {
        return fogStart;
    }

    /** Client fog end at full strength, in blocks. */
    public float fogEnd() {
        return fogEnd;
    }

    public static int clampIntensity(int intensity) {
        return Math.max(1, Math.min(3, intensity));
    }

    public static Optional<StormVariant> byWireId(int wireId) {
        for (StormVariant v : values()) {
            if (v.wireId == wireId) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    public static Optional<StormVariant> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        for (StormVariant v : values()) {
            if (v.serializedName.equals(lower)) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    /**
     * The planetary variant for a Nerospace planet id: Greenxertz dust, Cindara ion, Glacira cryo. The
     * station, an unknown planet or a non-Nerospace id has none.
     */
    public static Optional<StormVariant> forPlanet(@Nullable Identifier planet) {
        if (planet == null || !NEROSPACE.equals(planet.getNamespace())) {
            return Optional.empty();
        }
        return switch (planet.getPath()) {
            case "greenxertz" -> Optional.of(DUST);
            case "cindara" -> Optional.of(ION);
            case "glacira" -> Optional.of(CRYO);
            default -> Optional.empty();
        };
    }
}
