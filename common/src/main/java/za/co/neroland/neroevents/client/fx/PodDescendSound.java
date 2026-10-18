package za.co.neroland.neroevents.client.fx;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.payload.supply.SupplyPodEntity;

/**
 * Client only. The supply pod's descent loop ({@code neroevents:entity.supply_pod.descend}): follows the pod
 * while it falls, audible to 48 blocks, and stops the moment it lands or is gone.
 */
final class PodDescendSound extends AbstractTickableSoundInstance {

    /** Audible range in blocks (the client fades a sound over 16 blocks times its volume). */
    static final float RANGE = 48.0F;

    private final SupplyPodEntity pod;

    PodDescendSound(SupplyPodEntity pod) {
        super(SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "entity.supply_pod.descend")),
                SoundSource.NEUTRAL, RandomSource.create());
        this.pod = pod;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.volume = RANGE / 16.0F;
        this.x = pod.getX();
        this.y = pod.getY();
        this.z = pod.getZ();
    }

    SupplyPodEntity pod() {
        return pod;
    }

    @Override
    public void tick() {
        if (pod.isRemoved() || pod.isLanded()) {
            this.stop();
            return;
        }
        this.x = pod.getX();
        this.y = pod.getY();
        this.z = pod.getZ();
    }
}
