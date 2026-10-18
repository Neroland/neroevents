package za.co.neroland.neroevents.mixin.storm;

import java.util.function.BooleanSupplier;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import za.co.neroland.neroevents.storm.client.StormClient;
import za.co.neroland.neroevents.storm.client.StormRenderHooks;

/**
 * Client only (listed under {@code "client"} in {@code neroevents-common.mixins.json}). Two hooks into
 * {@link ClientLevel}, identical on Fabric, NeoForge and Forge:
 * <ul>
 *   <li>the environment-attribute builder in the constructor, where the storm sky/fog layers are added
 *       (vanilla adds its own lightning-flash layers at the same point);</li>
 *   <li>the end of the level tick, which drives the storm particles and sounds.</li>
 * </ul>
 * Both delegate straight to {@code storm.client}; no logic lives here. The layer hook is optional
 * ({@code require = 0}): it is cosmetic, so a Minecraft version without that method gets no storm tint
 * instead of a crash at startup. The tick hook stays required.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelStormMixin {

    @Inject(method = "addEnvironmentAttributeLayers", at = @At("RETURN"), require = 0)
    private void neroevents$stormLayers(EnvironmentAttributeSystem.Builder builder,
            CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
        StormRenderHooks.addLayers((ClientLevel) (Object) this, builder);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void neroevents$stormTick(BooleanSupplier haveTime, CallbackInfo ci) {
        StormClient.tick((ClientLevel) (Object) this);
    }
}
