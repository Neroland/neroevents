package za.co.neroland.neroevents.storm.client;

import za.co.neroland.neroevents.storm.StormVariant;

/**
 * Pure: how a storm bends the sky colour and the fog. The sky and fog colours lerp toward the variant's
 * tint by {@code 0.55 × strength}; the ion squall's tint is darkened toward {@code GLASS}. Fog start and end
 * lerp from the vanilla value toward the variant's range (never further out than vanilla) by
 * {@code strength}. {@code strength} is the fade times intensity/3, so the table's fog range is reached at
 * intensity 3 once the storm has fully ramped in.
 */
public final class StormLook {

    /** Sky-tint weight at full strength. */
    public static final float TINT_WEIGHT = 0.55F;
    /** The dark screen-glass colour the ion sky is mixed with. */
    public static final int GLASS = 0x0B1119;
    /** How much of {@link #GLASS} goes into the ion tint. */
    public static final float ION_GLASS_MIX = 0.35F;

    private StormLook() {
    }

    /** The tint target for a variant, {@code 0xRRGGBB}. */
    public static int tintTarget(StormVariant variant) {
        int tint = variant.skyTint();
        return variant == StormVariant.ION ? lerpRgb(tint, GLASS, ION_GLASS_MIX) : tint;
    }

    /** {@code argb} (alpha kept) lerped toward the variant's tint. */
    public static int tintArgb(int argb, StormVariant variant, float strength) {
        float t = clamp01(strength) * TINT_WEIGHT;
        if (t <= 0.0F) {
            return argb;
        }
        return (argb & 0xFF000000) | lerpRgb(argb & 0xFFFFFF, tintTarget(variant), t);
    }

    /** One 0..1 colour channel lerped toward channel {@code shift} (16 red, 8 green, 0 blue) of the tint. */
    public static float tintChannel(float value, int shift, StormVariant variant, float strength) {
        float t = clamp01(strength) * TINT_WEIGHT;
        float target = ((tintTarget(variant) >> shift) & 0xFF) / 255.0F;
        return value + (target - value) * t;
    }

    /** Fog start: from vanilla toward {@code variant.fogStart()}, never pushed further out. */
    public static float fogStart(float vanilla, StormVariant variant, float strength) {
        return lerp(vanilla, Math.min(vanilla, variant.fogStart()), clamp01(strength));
    }

    /** Fog end (also used for sky and cloud fog): from vanilla toward {@code variant.fogEnd()}. */
    public static float fogEnd(float vanilla, StormVariant variant, float strength) {
        return lerp(vanilla, Math.min(vanilla, variant.fogEnd()), clamp01(strength));
    }

    static int lerpRgb(int from, int to, float t) {
        int r = Math.round(lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t));
        int g = Math.round(lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t));
        int b = Math.round(lerp(from & 0xFF, to & 0xFF, t));
        return (r << 16) | (g << 8) | b;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(1.0F, v);
    }
}
