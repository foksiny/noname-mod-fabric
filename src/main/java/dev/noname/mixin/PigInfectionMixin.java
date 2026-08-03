package dev.noname.mixin;

import dev.noname.InfectedPig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.animal.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a dedicated, synced boolean "infected" flag onto every {@link Pig} so
 * the day-5 infection can be tracked without using a custom name. A synced
 * custom name is fundamentally unhideable in vanilla (it is still drawn above
 * the pig when the crosshair is over it); a dedicated data flag has no such
 * side-effect, syncs to clients automatically for the red tint layer, and is
 * persisted alongside the pig like any other entity data.
 */
@Mixin(Pig.class)
public abstract class PigInfectionMixin implements InfectedPig {

    @Unique
    private static final EntityDataAccessor<Boolean> NONAME_INFECTED =
            SynchedEntityData.defineId(Pig.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V",
            at = @At("TAIL"))
    private void noname$defineInfected(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(NONAME_INFECTED, Boolean.FALSE);
    }

    @Override
    public boolean noname$isInfected() {
        return ((Pig) (Object) this).getEntityData().get(NONAME_INFECTED);
    }

    @Override
    public void noname$setInfected(boolean infected) {
        ((Pig) (Object) this).getEntityData().set(NONAME_INFECTED, Boolean.valueOf(infected));
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL"))
    private void noname$saveInfected(CompoundTag tag, CallbackInfo ci) {
        if (noname$isInfected()) {
            tag.putBoolean("NonameInfected", true);
        }
    }

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL"))
    private void noname$readInfected(CompoundTag tag, CallbackInfo ci) {
        if (tag.getBoolean("NonameInfected")) {
            noname$setInfected(true);
        }
    }
}