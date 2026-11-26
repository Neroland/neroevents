package za.co.neroland.neroevents.fabric.mixin;

//? if <1.21 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import za.co.neroland.neroevents.creatures.EventDamageHooks;

@Mixin(LivingEntity.class)
abstract class LegacyEventDamageMixin {
    @WrapOperation(method = "actuallyHurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void neroevents$afterDamage(LivingEntity entity, float health, Operation<Void> setHealth,
            @Local(argsOnly = true) DamageSource source) {
        float oldHealth = entity.getHealth();
        setHealth.call(entity, health);
        float damage = oldHealth - entity.getHealth();
        if (!entity.level().isClientSide() && damage > 0) {
            EventDamageHooks.afterDamage(entity, source, damage);
        }
    }
}
*///?}
