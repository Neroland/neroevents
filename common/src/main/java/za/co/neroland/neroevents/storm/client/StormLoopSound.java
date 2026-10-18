package za.co.neroland.neroevents.storm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import za.co.neroland.neroevents.storm.StormVariant;

/**
 * Client only. The dimension-wide ambient loop of one storm ({@code neroevents:storm.<variant>.loop}, and
 * the cryo blizzard's {@code loop_layer} on top). Follows the player, never attenuates; its volume follows
 * the fade and the exposure (1.0 out in the storm, 0.3 under a roof) and it stops itself when the storm in
 * this dimension is gone or changed.
 */
final class StormLoopSound extends AbstractTickableSoundInstance {

    private final Identifier dimension;
    private final StormVariant variant;
    private final float layerScale;

    StormLoopSound(SoundEvent event, Identifier dimension, StormVariant variant, float layerScale) {
        super(event, SoundSource.WEATHER, RandomSource.create());
        this.dimension = dimension;
        this.variant = variant;
        this.layerScale = layerScale;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.01F;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    static Identifier loopId(StormVariant variant) {
        return Identifier.fromNamespaceAndPath("neroevents", "storm." + variant.serializedName() + ".loop");
    }

    StormVariant variant() {
        return variant;
    }

    Identifier dimension() {
        return dimension;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.level().dimension().identifier().equals(dimension)) {
            this.stop();
            return;
        }
        ClientStormState.View view = ClientStormState.view(dimension).orElse(null);
        if (view == null || view.variant() != variant) {
            this.stop();
            return;
        }
        this.volume = Math.max(0.01F, view.loopVolume() * layerScale);
    }
}
