package dev.noname;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The "ise it" apparition: a dumb entity that is only a 3-block-tall
 * billboard with the {@code ise_it} PNG ({@link
 * dev.noname.client.IseItRenderer} renders it, always facing the camera).
 *
 * <p>It has no AI, no physics and no health of its own — {@link
 * IseItHandler} drives everything from the server tick: the idle glitchy
 * shaking, the slow chase once a player looks at it, the contact damage and
 * the despawn. The fields below are the handler's per-entity bookkeeping.
 */
public class IseItEntity extends Entity {

    private UUID targetUuid;
    private long spawnedAtTick;
    private long lastJitterTick;
    private int nextJitterInterval;
    private long lastAttackTick;
    private boolean chasing;
    /** Server tick before which the entity must not move or attack — set by
     *  {@link CrossItem} while the Cross drains it. */
    private long stoppedUntilTick;

    public IseItEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public long getSpawnedAtTick() {
        return spawnedAtTick;
    }

    public void setSpawnedAtTick(long spawnedAtTick) {
        this.spawnedAtTick = spawnedAtTick;
    }

    public long getLastJitterTick() {
        return lastJitterTick;
    }

    public void setLastJitterTick(long lastJitterTick) {
        this.lastJitterTick = lastJitterTick;
    }

    public int getNextJitterInterval() {
        return nextJitterInterval;
    }

    public void setNextJitterInterval(int nextJitterInterval) {
        this.nextJitterInterval = nextJitterInterval;
    }

    public long getLastAttackTick() {
        return lastAttackTick;
    }

    public void setLastAttackTick(long lastAttackTick) {
        this.lastAttackTick = lastAttackTick;
    }

    public boolean isChasing() {
        return chasing;
    }

    public void setChasing(boolean chasing) {
        this.chasing = chasing;
    }

    public long getStoppedUntilTick() {
        return stoppedUntilTick;
    }

    public void setStoppedUntilTick(long stoppedUntilTick) {
        this.stoppedUntilTick = stoppedUntilTick;
    }

    /** {@return whether the entity is currently pinned by the Cross} */
    public boolean isStopped(long now) {
        return now < stoppedUntilTick;
    }
}
