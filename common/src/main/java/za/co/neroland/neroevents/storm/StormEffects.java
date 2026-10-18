package za.co.neroland.neroevents.storm;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Applies one storm row of {@link StormVariant} to one exposed player: the status effects for the
 * intensity (60 ticks, re-applied every 40, ambient, no particles, icon shown), the ion jolt at intensity 3
 * (nausea for 40 ticks every 400), and vanilla freezing for the cryo blizzard. No branching per variant:
 * the table decides. No oxygen change and no block effect.
 */
public final class StormEffects {

    private StormEffects() {
    }

    /** Server thread; the caller has already decided the player is exposed. */
    public static void apply(StormVariant variant, int intensity, ServerPlayer player, long now) {
        for (StormVariant.EffectSpec spec : variant.effects(intensity)) {
            add(player, spec, StormVariant.EFFECT_DURATION_TICKS);
        }
        variant.jolt(intensity).ifPresent(spec -> {
            if (joltDue(now)) {
                add(player, spec, StormVariant.JOLT_DURATION_TICKS);
            }
        });
        if (variant.freezes() && player.canFreeze()) {
            player.setTicksFrozen(Math.max(player.getTicksFrozen(), StormVariant.freezeLevel(intensity)));
        }
    }

    /** The jolt fires on the first 40-tick sweep of every 400-tick period. */
    static boolean joltDue(long now) {
        return Math.floorMod(now, (long) StormVariant.JOLT_PERIOD_TICKS) < StormVariant.EFFECT_INTERVAL_TICKS;
    }

    private static void add(ServerPlayer player, StormVariant.EffectSpec spec, int duration) {
        holder(spec.effect()).ifPresent(effect -> player.addEffect(
                new MobEffectInstance(effect, duration, spec.amplifier(), true, false, true)));
    }

    static Optional<Holder<MobEffect>> holder(String name) {
        return Optional.ofNullable(switch (name) {
            case "weakness" -> MobEffects.WEAKNESS;
            case "hunger" -> MobEffects.HUNGER;
            case "slowness" -> MobEffects.SLOWNESS;
            case "mining_fatigue" -> MobEffects.MINING_FATIGUE;
            case "nausea" -> MobEffects.NAUSEA;
            default -> null;
        });
    }
}
