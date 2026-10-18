package za.co.neroland.neroevents.event;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import za.co.neroland.neroevents.event.anchor.AnchorStrategy;

/**
 * A definition's {@code bounds}: how far the event reaches and how its anchor is chosen.
 *
 * @param kind              {@code dimension} (no anchor, whole dimension), {@code radius} (density anchor,
 *                          cylinder of {@code radius}) or {@code target} (the payload names the anchor)
 * @param radius            8..512 blocks
 * @param fallbackToDensity for {@code target}: use a density anchor when the payload finds no target
 */
public record EventBounds(Kind kind, int radius, boolean fallbackToDensity) {

    public static final Set<String> KEYS = Set.of("kind", "radius", "fallback_to_density");

    public static final int DEFAULT_RADIUS = 64;

    public static final EventBounds DEFAULT = new EventBounds(Kind.RADIUS, DEFAULT_RADIUS, true);

    public enum Kind {
        DIMENSION(AnchorStrategy.NONE), RADIUS(AnchorStrategy.DENSITY), TARGET(AnchorStrategy.TARGET);

        private final AnchorStrategy strategy;

        Kind(AnchorStrategy strategy) {
            this.strategy = strategy;
        }

        public AnchorStrategy strategy() {
            return strategy;
        }

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byName(String name) {
            for (Kind kind : values()) {
                if (kind.serializedName().equals(name)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }

        public static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(
                name -> byName(name).map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "unknown bounds kind " + name)),
                Kind::serializedName);
    }

    public static final Codec<EventBounds> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Kind.CODEC.optionalFieldOf("kind", Kind.RADIUS).forGetter(EventBounds::kind),
            Codec.INT.optionalFieldOf("radius", DEFAULT_RADIUS).forGetter(EventBounds::radius),
            Codec.BOOL.optionalFieldOf("fallback_to_density", true).forGetter(EventBounds::fallbackToDensity)
    ).apply(inst, EventBounds::new));

    public boolean radiusValid() {
        return radius >= 8 && radius <= 512;
    }
}
