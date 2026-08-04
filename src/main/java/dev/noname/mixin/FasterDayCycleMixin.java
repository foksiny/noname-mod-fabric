package dev.noname.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Speeds the daylight cycle up to double speed: every server tick advances
 * day time by 2 instead of 1, so a full day/night cycle takes ~10 minutes
 * instead of 20. Game time (potion clocks, stats) is left at the normal
 * rate.
 */
@Mixin(ServerLevel.class)
public abstract class FasterDayCycleMixin {

    @Shadow
    @Final
    private boolean tickTime;

    @Shadow
    @Final
    private ServerLevelData serverLevelData;

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void noname$doubleTimeSpeed(CallbackInfo ci) {
        if (!this.tickTime) {
            return;
        }
        ServerLevel self = (ServerLevel) (Object) this;
        long gameTime = self.getGameTime() + 1L;
        this.serverLevelData.setGameTime(gameTime);
        this.serverLevelData.getScheduledEvents().tick(this.server, gameTime);
        if (self.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            self.setDayTime(self.getDayTime() + 2L);
        }
        ci.cancel();
    }
}