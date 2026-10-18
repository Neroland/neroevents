package za.co.neroland.neroevents.war;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * Faction colours for the war beacon's band and capture ring. NeroFactions' definitions carry no colour, so a
 * faction's colour is derived from its id: {@code HSB(hue = (id.hashCode() & 0xFFFF) / 65535, 0.65, 0.95)}.
 * Deterministic, so every client and every restart agrees. An unowned beacon shows the WAR severity colour.
 * Pure (no AWT): plain-JVM testable.
 */
public final class WarColours {

    /** The WAR severity colour ({@code B388FF}), used while no faction owns a beacon. */
    public static final int UNOWNED = 0xB388FF;

    private WarColours() {
    }

    /** The faction's colour as {@code 0xRRGGBB}; {@link #UNOWNED} for {@code null}. */
    public static int of(@Nullable Identifier faction) {
        if (faction == null) {
            return UNOWNED;
        }
        float hue = (faction.hashCode() & 0xFFFF) / 65535.0F;
        return hsbToRgb(hue, 0.65F, 0.95F);
    }

    /** {@code java.awt.Color.HSBtoRGB} without AWT, as {@code 0xRRGGBB}. */
    static int hsbToRgb(float hue, float saturation, float brightness) {
        int r;
        int g;
        int b;
        if (saturation == 0.0F) {
            r = g = b = (int) (brightness * 255.0F + 0.5F);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0F;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0F - saturation);
            float q = brightness * (1.0F - saturation * f);
            float t = brightness * (1.0F - saturation * (1.0F - f));
            switch ((int) h) {
                case 0 -> {
                    r = (int) (brightness * 255.0F + 0.5F);
                    g = (int) (t * 255.0F + 0.5F);
                    b = (int) (p * 255.0F + 0.5F);
                }
                case 1 -> {
                    r = (int) (q * 255.0F + 0.5F);
                    g = (int) (brightness * 255.0F + 0.5F);
                    b = (int) (p * 255.0F + 0.5F);
                }
                case 2 -> {
                    r = (int) (p * 255.0F + 0.5F);
                    g = (int) (brightness * 255.0F + 0.5F);
                    b = (int) (t * 255.0F + 0.5F);
                }
                case 3 -> {
                    r = (int) (p * 255.0F + 0.5F);
                    g = (int) (q * 255.0F + 0.5F);
                    b = (int) (brightness * 255.0F + 0.5F);
                }
                case 4 -> {
                    r = (int) (t * 255.0F + 0.5F);
                    g = (int) (p * 255.0F + 0.5F);
                    b = (int) (brightness * 255.0F + 0.5F);
                }
                default -> {
                    r = (int) (brightness * 255.0F + 0.5F);
                    g = (int) (p * 255.0F + 0.5F);
                    b = (int) (q * 255.0F + 0.5F);
                }
            }
        }
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
