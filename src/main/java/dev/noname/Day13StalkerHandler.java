package dev.noname;

import com.mojang.authlib.GameProfile;
import dev.noname.config.ModConfig;
import dev.noname.network.NonameEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Day-13+ stalker: every 4-9 minutes spent on day 13 or later there is a 37%
 * chance per player that the flesh-skinned fake player (the same "你的朋友"
 * as the day-3 ghost) appears 15 blocks behind the player, says "behind you."
 * in chat, plays a cave sound at its position and then <b>follows the player
 * like a real player would</b> — real physics, real pathfinding, and a small
 * brain of its own.
 *
 * <p>The moment the stalker becomes visible on the player's screen (view
 * frustum plus an unobstructed line of sight) it stops and faces them. If the
 * player keeps staring for 2 seconds it says "hey :)" in chat; if the player
 * stops looking it quietly resumes the chase. Staring at it long enough —
 * 20 seconds non-stop, or catching it 10 separate times — makes it give up:
 * it says "aight bro, that's it." and keeps following no matter how much it
 * is watched, and its hunt may now last up to 3 minutes instead of 40
 * seconds. When it gets within 3 blocks it vanishes, hits the player with
 * darkness II for 2 seconds, blasts the laggy2 sound at maximum volume and
 * flashes the victim's screen with 0.1 seconds of red static (via the
 * {@code day13_stalker} payload, drawn client-side by
 * {@code StalkerStaticOverlay}).
 *
 * <p>Like the other fake-player events it is a real tracked
 * {@link ServerPlayer} entity added straight into the world, so the client
 * renders it through vanilla networking with the flesh skin (same UUID as
 * the day-3 ghost). Its connection is the same dummy that swallows every
 * packet. Unlike the ghosts it keeps its physics: the stalker must collide
 * with the world, so {@code noPhysics} stays {@code false}.
 *
 * <p>Movement: {@code LivingEntity.aiStep()} clears the {@code xxa}/{@code
 * zza}/{@code jumping} input fields at the start of every tick, so the
 * stalker cannot be steered through the player-input fields. Instead the
 * handler pushes a horizontal {@link net.minecraft.world.phys.Vec3} velocity
 * into {@code ServerPlayer.setDeltaMovement} every server tick — the entity's
 * own {@code travel()} then applies gravity, friction and collision handling
 * exactly like it does for a real mob. Pathfinding is vanilla
 * {@link PathFinder} + {@link WalkNodeEvaluator} driven through a dummy
 * {@link EntityType#ZOMBIE} (never added to the world) whose position and
 * grounded state mirror the fake player, since the evaluator requires a
 * {@link Mob} and a {@link ServerPlayer} is not one.
 *
 * <p>One vanilla quirk makes this work: the world ticks a player through
 * {@code ServerPlayer.tick()}, which only updates network bookkeeping — the
 * real physics chain ({@code Player.tick} → {@code LivingEntity.tick} →
 * {@code travel()}) lives in {@code ServerPlayer.doTick()}, which vanilla
 * drives from the connection's own tick loop. The dummy connection has no
 * such loop, so the handler calls {@code fake.doTick()} itself every server
 * tick; without it the stalker would hover frozen at its spawn point.
 *
 * <p>When plain walking cannot reach the player, the stalker acts like a
 * desperate player would, swapping through small behaviours:
 * <ul>
 *   <li><b>pillar</b> — the victim is out of reach above: it looks down,
 *       jumps, and places a cobblestone block under its own feet, climbing a
 *       tower one block per jump up to the victim's level, then walking over
 *       or bridging across to them;</li>
 *   <li><b>bridge</b> — a gap or void separates it from the victim: it
 *       crouches, walks to the edge, glances back and down, and drops
 *       cobblestone blocks ahead of itself, stepping onto each one as it
 *       goes;</li>
 *   <li><b>dig up</b> — a solid block sits between it and the victim (an
 *       overhang above a half-built tower, a ceiling it stands under, the
 *       floor of the victim's tower): it digs it out with the cracking
 *       animation, then carries on with whatever it was doing;</li>
 *   <li><b>break</b> — a diggable wall stands in the way: it digs it with a
 *       real cracking animation and the block's particles and drops;</li>
 *   <li><b>climb</b> — a wall blocks it: it walks along the wall looking for
 *       a passable spot or a one-block step up;</li>
 *   <li><b>ladder</b> — a ladder or vine leads up to the victim: it grabs it
 *       and rides it up;</li>
 *   <li><b>swim</b> — water separates it from the victim: it swims across
 *       instead of walking around;</li>
 *   <li><b>pounce</b> — close to the victim with a narrow gap between: it
 *       leaps across instead of bridging;</li>
 *   <li><b>rush</b> — while the victim is not watching it sprints, so the
 *       gap closes fast whenever they look away;</li>
 *   <li><b>mlg</b> — it is falling from a height: it pulls out a water
 *       bucket, looks down, and places the water exactly where it will land
 *       so the fall cannot hurt it.</li>
 * </ul>
 * While it builds, the cobblestone is visibly held in its hand; the hand
 * is empty again while it just walks or digs.
 */
public final class Day13StalkerHandler {

    /** Roll cadence: 4-9 minutes (4800-10800 ticks). */
    private static final int MIN_ROLL_TICKS = 20 * 60 * 4;
    private static final int MAX_ROLL_TICKS = 20 * 60 * 9;

    /** Probability that a roll actually spawns the stalker — 37%. */
    private static final float EVENT_CHANCE = 0.37F;

    /** How far behind the player the stalker appears, in blocks. */
    private static final double SPAWN_DISTANCE = 15.0D;

    /** How close the stalker may get before it catches the player, in
     *  blocks. */
    private static final double CATCH_DISTANCE = 3.0D;

    /** How far away the player's look can still spot the stalker. */
    private static final double LOOK_MAX_DISTANCE = 25.0D;

    /** Half-angle of the cone the player's look must fall inside for the
     *  stalker to count as visible on screen (a rough server-side view
     *  frustum). */
    private static final double LOOK_CONE_RAD = Math.toRadians(55.0D);

    /** How long the player must keep staring before "hey :)" fires, in ticks
     *  (20 ticks = 1 second). */
    private static final int OBSERVE_MESSAGE_TICKS = 20 * 2;

    /** Staring at it this long without a break makes it give up, in ticks
     *  (20 seconds). */
    private static final int GIVE_UP_STARE_TICKS = 20 * 20;

    /** Catching it on screen this many separate times also makes it give up. */
    private static final int MAX_LOOK_EVENTS = 10;

    /** Duration of the darkness effect on the caught player, in ticks
     *  (20 ticks = 1 second). */
    private static final int DARKNESS_DURATION_TICKS = 20 * 2;

    /** Volume of the laggy2 blast — the client sound engine clamps at 3.0,
     *  so this is as loud as a sound can get. */
    private static final float LAGGY2_VOLUME = 3.0F;

    /** Volume of the cave sound at the stalker's position. */
    private static final float CAVE_VOLUME = 2.0F;

    /** Stalking speed, in blocks per tick (a brisk but not sprinting walk). */
    private static final double WALK_SPEED = 0.2D;

    /** Vertical impulse of a jump, in blocks per tick (vanilla jump). */
    private static final double JUMP_SPEED = 0.42D;

    /** How often the path to the player is recomputed, in ticks. */
    private static final int PATH_RECOMPUTE_TICKS = 20;

    /** Max range of the pathfinding search, in blocks. */
    private static final float PATH_FOLLOW_RANGE = 48.0F;

    /** Max nodes the pathfinder may visit before giving up. */
    private static final int PATH_MAX_NODES = 512;

    /** Normal lifetime of a hunt, in ticks (40 seconds). */
    private static final int DESPAWN_TICKS = 20 * 40;

    /** Lifetime after the stalker gave up, in ticks (3 minutes). */
    private static final int DESPAWN_TICKS_GIVE_UP = 20 * 60 * 3;

    /** Fall distance (in blocks) from which the stalker switches to the MLG
     *  water-bucket routine. */
    private static final double MLG_MIN_FALL_DISTANCE = 6.0D;

    /** Vertical lead of the victim above the stalker that calls for a pillar,
     *  in blocks. */
    private static final double PILLAR_MIN_DY = 3.0D;

    /** How close (horizontally) the victim must be for a pillar to be worth
     *  building. */
    private static final double PILLAR_MAX_HORIZ = 24.0D;

    /** A victim this high up gets a pillar even when the pathfinder found a
     *  path: a path to someone 8+ blocks up only ever leads to the base. */
    private static final double PILLAR_FORCE_DY = 8.0D;

    /** The stalker abandons a pillar hunt when the victim gets further away
     *  than this. */
    private static final double PILLAR_ABANDON_HORIZ = 28.0D;

    /** Safety cap for a single pillar climb, in ticks. */
    private static final int PILLAR_MAX_TICKS = 800;

    /** How far across a gap the stalker is willing to bridge, in blocks. */
    private static final double BRIDGE_MAX_HORIZ = 24.0D;

    /** The stalker abandons a bridge when the victim moves further than
     *  this. */
    private static final double BRIDGE_ABANDON_HORIZ = 28.0D;

    /** Safety cap for a single bridge, in ticks. */
    private static final int BRIDGE_MAX_TICKS = 400;

    /** A gap this narrow is leapt instead of bridged, in blocks. */
    private static final int POUNCE_MAX_GAP = 3;

    /** The stalker only pounces when the victim is closer than this. */
    private static final double POUNCE_MAX_DIST = 10.0D;

    /** Chase speed while the victim is not watching, in blocks per tick (a
     *  sprint; the walk is {@link #WALK_SPEED}). */
    private static final double RUSH_SPEED = 0.3D;

    /** Cliff drops shallower than this are just jumped, not MLG'd. */
    private static final double MLG_MIN_CLIFF_FALL = 6.0D;

    /** Safety cap for a single ladder climb, in ticks. */
    private static final int LADDER_MAX_TICKS = 200;

    /** How long the stalker searches a wall for a climbable spot before it
     *  falls back to building, in ticks. */
    private static final int CLIMB_MAX_TICKS = 80;

    /** Ticks between crack stages while digging a block (10 stages total). */
    private static final int BREAK_STAGE_TICKS = 3;

    /** How many blocks a single hunt is willing to dig through. */
    private static final int BREAK_BUDGET = 12;

    /** The line the stalker says in chat when it appears. */
    private static final String SPAWN_CHAT = "behind you.";

    /** The line it says after being stared at for 2 seconds. */
    private static final String HEY_CHAT = "hey :)";

    /** The line it says when it finally gives up on being watched. */
    private static final String GIVE_UP_CHAT = "aight bro, that's it.";

    /** Player -> ticks until that player's next roll. */
    private static final Map<UUID, Integer> ticksUntilRoll = new HashMap<>();

    /** Player -> the stalker currently hunting them. */
    private static final Map<UUID, Stalker> stalkers = new HashMap<>();

    private Day13StalkerHandler() {
    }

    /** What the stalker is currently doing. */
    private enum Mode {
        /** Walking along a vanilla path (or straight at the victim). */
        FOLLOW,
        /** Walking along a wall looking for a way up. */
        CLIMB,
        /** Building a cobblestone pillar up to the victim. */
        PILLAR,
        /** Building a cobblestone bridge across a gap. */
        BRIDGE,
        /** Digging a wall with a cracking animation. */
        BREAK,
        /** Leaping a narrow gap at the victim. */
        POUNCE,
        /** Swimming across water toward the victim. */
        SWIM,
        /** Climbing a ladder or vine up to the victim. */
        LADDER,
        /** Falling — water bucket out, MLG landing. */
        MLG
    }

    /** One active stalker and its per-hunt state. */
    private static final class Stalker {
        /** The fake player entity walking behind the victim. */
        final ServerPlayer fake;
        /** Dummy zombie that mirrors the fake player for the pathfinder. */
        final Mob pathProxy;
        /** The current computed path; {@code null} = none yet or unreachable. */
        Path path;
        /** Ticks since the path was last computed. */
        int pathAge;
        /** Whether the victim is currently watching the stalker. */
        boolean observed;
        /** Consecutive ticks the victim has been watching it. */
        int observedTicks;
        /** How many separate times the victim caught it on screen. */
        int lookEvents;
        /** Whether "hey :)" already fired for this hunt. */
        boolean heySent;
        /** Whether the stalker gave up being watched and follows anyway. */
        boolean givenUp;
        /** Server tick at which the stalker was spawned. */
        final long spawnTick;
        /** The current behaviour mode. */
        Mode mode = Mode.FOLLOW;
        /** Ticks spent in the current mode. */
        int modeTicks;
        /** Block being dug in BREAK mode. */
        BlockPos breakPos;
        /** Crack stage reached while digging (0-9). */
        int breakStage;
        /** Blocks left this hunt the stalker is willing to dig. */
        int breakBudget = BREAK_BUDGET;
        /** What to do after the current BREAK finishes. */
        Mode breakReturnMode = Mode.FOLLOW;
        /** Whether the current pillar jump already placed its block. */
        boolean pillarPlaced;
        /** Whether the pillar is ready for the next jump (on the ground). */
        boolean pillarReady = true;
        /** Remaining ticks of the bridge's "look back" animation. */
        int bridgeLookTicks;
        /** Position of the bridge block the stalker has not stepped onto yet;
         *  {@code null} when the next step does not sit on a fresh block. */
        BlockPos bridgePlacedPos;

        Stalker(ServerPlayer fake, Mob pathProxy, long spawnTick) {
            this.fake = fake;
            this.pathProxy = pathProxy;
            this.spawnTick = spawnTick;
        }
    }

    public static void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long day = DayCounter.currentDay(overworld);

        // Tick every active stalker; drop stalkers whose victim left or that
        // somehow died.
        for (var it = stalkers.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                entry.getValue().fake.discard();
                continue;
            }
            if (!tickStalker(server, player, entry.getValue())) {
                it.remove();
            }
        }

        if (day < ModConfig.scaledDay(13) || !ModConfig.isEnabled("day13_stalker")) {
            ticksUntilRoll.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            if (stalkers.containsKey(player.getUUID())) {
                continue;
            }
            int remaining = ticksUntilRoll.getOrDefault(player.getUUID(), MIN_ROLL_TICKS);
            if (remaining > 1) {
                ticksUntilRoll.put(player.getUUID(), remaining - 1);
                continue;
            }
            ticksUntilRoll.put(player.getUUID(), MIN_ROLL_TICKS
                    + overworld.getRandom().nextInt(MAX_ROLL_TICKS - MIN_ROLL_TICKS + 1));
            if (overworld.getRandom().nextFloat()
                    < ModConfig.chance("day13_stalker", BloodyNightHandler.boost(EVENT_CHANCE, overworld))) {
                triggerForPlayer(server, player);
            }
        }
    }

    /**
     * Advances one stalker by a tick. {@return whether the hunt is still
     * running — {@code false} when the stalker caught the player (or left the
     * victim's dimension) and the entry must be dropped.}
     */
    private static boolean tickStalker(MinecraftServer server, ServerPlayer player, Stalker stalker) {
        ServerPlayer fake = stalker.fake;
        ServerLevel level = player.serverLevel();
        if (!fake.isAlive() || fake.level() != level) {
            fake.discard();
            return false;
        }

        // Pinned by the Cross: hold still — no catch, no hunt, no movement —
        // until the charge completes.
        if (CrossItem.isStopped(fake)) {
            CrossItem.pin(fake);
            return true;
        }

        // The server only drives the full player tick chain (gravity,
        // collision, travel) through the connection's own tick loop; the
        // dummy connection has no such loop, so ServerPlayer.tick() alone
        // would leave the stalker frozen mid-air with its physics never
        // running. doTick() runs that chain directly.
        fake.doTick();

        // Lifetime: 40 seconds normally, 3 minutes once it gave up.
        long deadline = stalker.givenUp ? DESPAWN_TICKS_GIVE_UP : DESPAWN_TICKS;
        if (server.getTickCount() - stalker.spawnTick >= deadline) {
            fake.discard();
            return false;
        }

        double dx = player.getX() - fake.getX();
        double dy = player.getY() - fake.getY();
        double dz = player.getZ() - fake.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Caught: close enough — vanish with darkness, laggy2 and the red
        // static flash.
        if (distance <= CATCH_DISTANCE) {
            catchPlayer(server, player, stalker);
            return false;
        }

        // Visibility: does the player's view actually contain the stalker?
        boolean visible = isOnScreen(player, fake);
        if (visible && !stalker.observed) {
            stalker.lookEvents++;
        }
        stalker.observed = visible;
        if (visible) {
            stalker.observedTicks++;
        } else {
            stalker.observedTicks = 0;
        }

        // Watched and not (yet) given up: freeze, face the victim, and count
        // how stubborn the victim is being.
        if (!stalker.givenUp && visible) {
            // Drop any in-flight mode and stale state so that, once the
            // stare ends, the stalker re-decides from scratch instead of
            // resuming a half-finished jump or bridge.
            stalker.mode = Mode.FOLLOW;
            stalker.modeTicks = 0;
            stalker.bridgeLookTicks = 0;
            stalker.bridgePlacedPos = null;
            face(fake, player.getX(), player.getZ());
            fake.setXRot(0.0F);
            fake.setShiftKeyDown(false);
            fake.setSprinting(false);
            fake.setSwimming(false);
            if (!stalker.heySent && stalker.observedTicks >= OBSERVE_MESSAGE_TICKS) {
                stalker.heySent = true;
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + HEY_CHAT), false);
            }
            if (stalker.observedTicks >= GIVE_UP_STARE_TICKS
                    || stalker.lookEvents >= MAX_LOOK_EVENTS) {
                giveUp(server, stalker);
            }
            return true;
        }

        driveStalker(server, player, stalker);
        return true;
    }

    /** Decides what the stalker does this tick and executes it. */
    private static void driveStalker(MinecraftServer server, ServerPlayer player, Stalker stalker) {
        ServerPlayer fake = stalker.fake;
        ServerLevel level = player.serverLevel();
        stalker.modeTicks++;

        // MLG preempts everything while falling fast enough to be hurt.
        if (stalker.mode == Mode.MLG) {
            tickMlg(fake, stalker);
            if (stalker.mode == Mode.MLG) {
                return;
            }
        }
        boolean falling = !fake.onGround() && fake.getDeltaMovement().y < -0.3D;
        if (falling && fake.fallDistance > MLG_MIN_FALL_DISTANCE) {
            stalker.mode = Mode.MLG;
            stalker.modeTicks = 0;
            return;
        }
        // A cliff with the victim below: jump off it and MLG the landing —
        // but only when the drop is deep enough to actually need it.
        if (stalker.mode == Mode.FOLLOW && fake.onGround() && isCliffAhead(fake)
                && cliffDrop(fake) >= MLG_MIN_CLIFF_FALL
                && player.getY() < fake.getY() - 2.0D) {
            Vec3 toPlayer = new Vec3(player.getX() - fake.getX(), 0.0D,
                    player.getZ() - fake.getZ());
            Vec3 dir = facing(fake.getYRot());
            if (toPlayer.lengthSqr() > 0.1D
                    && dir.dot(toPlayer.normalize()) > 0.5D) {
                fake.setDeltaMovement(dir.x * WALK_SPEED, JUMP_SPEED,
                        dir.z * WALK_SPEED);
                stalker.mode = Mode.MLG;
                stalker.modeTicks = 0;
                return;
            }
        }

        // Hand item follows the current mode: cobblestone while building,
        // the bucket while falling, nothing while walking or digging.
        if (stalker.mode == Mode.PILLAR || stalker.mode == Mode.BRIDGE) {
            setHand(fake, Items.COBBLESTONE);
        } else if (stalker.mode == Mode.MLG) {
            setHand(fake, Items.WATER_BUCKET);
        } else {
            setHand(fake, null);
        }

        // Path upkeep: recompute when stale, finished or when we just hit a
        // wall.
        stalker.pathAge++;
        boolean needPath = stalker.mode == Mode.FOLLOW || stalker.mode == Mode.CLIMB;
        if (needPath && (stalker.path == null || stalker.path.isDone()
                || stalker.pathAge >= PATH_RECOMPUTE_TICKS
                || fake.horizontalCollision)) {
            stalker.path = computePath(player, fake, stalker.pathProxy);
            stalker.pathAge = 0;
        }
        boolean pathOk = stalker.path != null && !stalker.path.isDone();

        // Long-running behaviours keep running until they finish on their
        // own; only FOLLOW/CLIMB re-decide every tick.
        switch (stalker.mode) {
            case BREAK -> {
                tickBreak(player, stalker);
                return;
            }
            case POUNCE -> {
                tickPounce(player, stalker);
                return;
            }
            case SWIM -> {
                tickSwim(player, stalker);
                return;
            }
            case LADDER -> {
                tickLadder(player, stalker);
                return;
            }
            case PILLAR -> {
                tickPillar(player, stalker);
                return;
            }
            case BRIDGE -> {
                tickBridge(player, stalker);
                return;
            }
            case CLIMB -> {
                tickClimb(player, stalker);
                return;
            }
            default -> { }
        }

        boolean blocked = fake.horizontalCollision && fake.onGround();
        BlockPos ahead = aheadBlock(fake);
        BlockState aheadState = level.getBlockState(ahead);
        double dy = player.getY() - fake.getY();
        double horiz = Math.sqrt((player.getX() - fake.getX()) * (player.getX() - fake.getX())
                + (player.getZ() - fake.getZ()) * (player.getZ() - fake.getZ()));

        // A victim up high gets a pillar before anything else: build up to
        // their level instead of digging under their tower or crawling along
        // a wall. Only a walkable path is preferred when the climb is small.
        if (dy > PILLAR_MIN_DY && horiz < PILLAR_MAX_HORIZ
                && (!pathOk || dy > PILLAR_FORCE_DY)) {
            stalker.mode = Mode.PILLAR;
            stalker.modeTicks = 0;
            stalker.pillarPlaced = false;
            stalker.pillarReady = true;
            return;
        }
        // Victim directly above with a solid block between (a ceiling, the
        // floor of their tower): dig up through it.
        if (dy > 0.5D && horiz < 2.5D && !pathOk && stalker.breakBudget > 0) {
            BlockPos up = solidAbove(level, fake);
            if (up != null) {
                startBreak(stalker, up, Mode.FOLLOW);
                return;
            }
        }
        // Wall in the way and the pathfinder cannot get around it: dig.
        if (blocked && !pathOk && isBreakable(level, ahead, aheadState)
                && stalker.breakBudget > 0) {
            startBreak(stalker, ahead, Mode.FOLLOW);
            return;
        }
        // A narrow gap with the victim close: leap across instead of
        // building a bridge for it.
        if (isGapAhead(fake) && gapWidth(fake) <= POUNCE_MAX_GAP
                && horiz < POUNCE_MAX_DIST) {
            stalker.mode = Mode.POUNCE;
            stalker.modeTicks = 0;
            return;
        }
        // Water in the way: swim through it.
        if (waterAhead(fake)) {
            stalker.mode = Mode.SWIM;
            stalker.modeTicks = 0;
            return;
        }
        // A ladder or vine with the victim above: climb it.
        if (dy > 1.0D && ladderAhead(fake)) {
            stalker.mode = Mode.LADDER;
            stalker.modeTicks = 0;
            return;
        }
        // Unreachable target: pick the player-like strategy.
        if (!pathOk) {
            if (isGapAhead(fake) && Math.abs(dy) < 3.5D
                    && horiz < BRIDGE_MAX_HORIZ) {
                stalker.mode = Mode.BRIDGE;
                stalker.modeTicks = 0;
                stalker.bridgeLookTicks = 0;
                stalker.bridgePlacedPos = null;
                return;
            }
            if (blocked && dy > 0.8D) {
                stalker.mode = Mode.CLIMB;
                stalker.modeTicks = 0;
                return;
            }
        }
        followPath(player, stalker, pathOk);
    }

    /** Walks the stalker along its path (or straight at the victim when
     *  there is no path), jumping walls and steps as needed. */
    private static void followPath(ServerPlayer player, Stalker stalker, boolean pathOk) {
        ServerPlayer fake = stalker.fake;
        Vec3 node = null;
        if (pathOk) {
            node = stalker.path.getNextEntityPos(fake);
            double ndx = node.x - fake.getX();
            double ndz = node.z - fake.getZ();
            if (Math.sqrt(ndx * ndx + ndz * ndz) < 0.4D) {
                stalker.path.advance();
                if (!stalker.path.isDone()) {
                    node = stalker.path.getNextEntityPos(fake);
                }
            }
        }
        // No route (or the victim is unreachable): walk straight at them.
        if (node == null) {
            node = player.position();
        }

        double ndx = node.x - fake.getX();
        double ndz = node.z - fake.getZ();
        face(fake, node.x, node.z);

        // Jump when walking into a wall on the ground, or when the next path
        // node sits up a block.
        boolean jump = fake.horizontalCollision && fake.onGround();
        double nodeUp = node.y - fake.getY();
        if (nodeUp > 0.5D && Math.sqrt(ndx * ndx + ndz * ndz) < 1.6D) {
            jump = true;
        }

        // Sprint while the victim is not watching; walk calmly once it gave
        // up being watched (or was just spotted this instant).
        boolean rush = !stalker.observed;
        fake.setSprinting(rush);
        double speed = rush ? RUSH_SPEED : WALK_SPEED;
        Vec3 dir = facing(fake.getYRot());
        Vec3 vel = fake.getDeltaMovement();
        fake.setDeltaMovement(dir.x * speed,
                jump ? JUMP_SPEED : vel.y, dir.z * speed);
    }

    /** MLG: water bucket out, water placed exactly where the stalker will
     *  land. Ends when it touches the ground. */
    private static void tickMlg(ServerPlayer fake, Stalker stalker) {
        ServerLevel level = (ServerLevel) fake.level();
        clearPosture(fake);
        setHand(fake, Items.WATER_BUCKET);
        fake.setXRot(-80.0F);
        if (fake.onGround() || fake.fallDistance <= 1.0D) {
            setHand(fake, null);
            fake.setXRot(0.0F);
            stalker.mode = Mode.FOLLOW;
            stalker.modeTicks = 0;
            return;
        }
        // Find the ground below and place the water over it once the landing
        // is imminent — deterministic, so always accurate. Landing in a
        // fluid needs no bucket, so the scan stops there.
        BlockPos feet = BlockPos.containing(fake.getX(), fake.getY() - 0.2D, fake.getZ());
        for (int i = 1; i <= 8; i++) {
            BlockPos p = feet.below(i);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) {
                continue;
            }
            if (!s.getFluidState().isEmpty()) {
                return;
            }
            double landY = p.getY() + 1.0D;
            if (fake.getY() - landY <= 2.0D) {
                BlockPos waterPos = p.above();
                if (level.getBlockState(waterPos).isAir()) {
                    level.setBlockAndUpdate(waterPos, Blocks.WATER.defaultBlockState());
                    level.playSound(null, waterPos, SoundEvents.BUCKET_EMPTY,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            return;
        }
    }

    /** Pillar: looks down, jumps and places a cobblestone block under its
     *  feet, one block per jump, until the victim is at arm's reach. */
    private static void tickPillar(ServerPlayer player, Stalker stalker) {
        ServerPlayer fake = stalker.fake;
        ServerLevel level = player.serverLevel();
        clearPosture(fake);
        setHand(fake, Items.COBBLESTONE);
        fake.setXRot(-90.0F);
        face(fake, player.getX(), player.getZ());

        double dy = player.getY() - fake.getY();
        double dx = player.getX() - fake.getX();
        double dz = player.getZ() - fake.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        if (fake.onGround()) {
            if (stalker.pillarReady) {
                // An overhang or ceiling above the head: dig it out first,
                // then carry on climbing.
                if (stalker.breakBudget > 0) {
                    BlockPos above = solidAbove(level, fake);
                    if (above != null) {
                        startBreak(stalker, above, Mode.PILLAR);
                        fake.setXRot(0.0F);
                        return;
                    }
                }
                stalker.pillarReady = false;
                stalker.pillarPlaced = false;
                fake.setDeltaMovement(0.0D, JUMP_SPEED, 0.0D);
            }
        } else if (fake.getDeltaMovement().y > 0.05D && !stalker.pillarPlaced) {
            // Rising: drop the block at the feet, then land on top of it.
            BlockPos feet = fake.blockPosition();
            if (level.getBlockState(feet).isAir()) {
                level.setBlockAndUpdate(feet, Blocks.COBBLESTONE.defaultBlockState());
                level.playSound(null, feet, SoundEvents.STONE_PLACE,
                        SoundSource.BLOCKS, 0.9F, 0.9F);
                stalker.pillarPlaced = true;
            }
        } else if (fake.getDeltaMovement().y <= 0.05D) {
            // Coming back down: re-arm the climb whether or not the block
            // landed. A bonked jump (ceiling/wall, no block placed) must
            // still retry instead of leaving the stalker standing idle.
            stalker.pillarReady = true;
        }

        // Done: at the victim's height — walk over when they are close,
        // bridge across when they are still far. Only a victim that ran off
        // (or a climb that ran too long) aborts the tower.
        if (stalker.modeTicks > PILLAR_MAX_TICKS || horiz > PILLAR_ABANDON_HORIZ) {
            stalker.mode = Mode.FOLLOW;
            stalker.modeTicks = 0;
            fake.setXRot(0.0F);
        } else if (dy <= 1.0D) {
            stalker.mode = horiz < 8.0D ? Mode.FOLLOW : Mode.BRIDGE;
            stalker.modeTicks = 0;
            stalker.bridgeLookTicks = 0;
            stalker.bridgePlacedPos = null;
            fake.setXRot(0.0F);
        }
    }

    /** Bridge: crouches, walks to the edge, glances back and down and drops
     *  a cobblestone block ahead of itself, stepping onto each block as it
     *  goes. */
    private static void tickBridge(ServerPlayer player, Stalker stalker) {
        ServerPlayer fake = stalker.fake;
        ServerLevel level = player.serverLevel();
        clearPosture(fake);
        setHand(fake, Items.COBBLESTONE);
        fake.setShiftKeyDown(true);
        fake.setXRot(-55.0F);

        double dx = player.getX() - fake.getX();
        double dz = player.getZ() - fake.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        if (stalker.bridgeLookTicks > 0) {
            // At the edge: crouched, head straight and looking down at the
            // block just placed.
            stalker.bridgeLookTicks--;
            fake.yHeadRot = fake.getYRot();
            fake.yBodyRot = fake.getYRot();
            fake.setXRot(-60.0F);
        } else {
            face(fake, player.getX(), player.getZ());
        }

        // Getting close to the edge of the gap: place the block below the
        // next step, then walk onto it.
        BlockPos ahead = aheadBlock(fake);
        BlockState aheadBelow = level.getBlockState(ahead.below());
        boolean gap = level.getBlockState(ahead).isAir()
                && (aheadBelow.isAir() || !aheadBelow.getFluidState().isEmpty());
        double distToAhead = Math.sqrt(
                (ahead.getX() + 0.5D - fake.getX()) * (ahead.getX() + 0.5D - fake.getX())
                        + (ahead.getZ() + 0.5D - fake.getZ()) * (ahead.getZ() + 0.5D - fake.getZ()));
        // Low ceiling right above the stalker's own head: dig it out so it
        // can keep standing and stepping forward. (Deliberately the stalker's
        // own head space, not the next bridge cell's — the victim's tower
        // sits over that cell while the stalker bridges toward it, and
        // digging the tower would make the bridge stop and stare upward.)
        BlockPos head = fake.blockPosition().above(1);
        if (stalker.breakBudget > 0
                && isBreakable(level, head, level.getBlockState(head))) {
            startBreak(stalker, head, Mode.BRIDGE);
            fake.setShiftKeyDown(false);
            fake.setXRot(0.0F);
            return;
        }
        if (gap && distToAhead < 1.3D) {
            level.setBlockAndUpdate(ahead.below(), Blocks.COBBLESTONE.defaultBlockState());
            level.playSound(null, ahead.below(), SoundEvents.STONE_PLACE,
                    SoundSource.BLOCKS, 0.9F, 0.9F);
            stalker.bridgeLookTicks = 6;
            stalker.bridgePlacedPos = ahead.below();
        }

        // Stand still for the "glance back at the block" beat; walk on
        // otherwise.
        if (stalker.bridgeLookTicks == 0) {
            Vec3 dir = facing(fake.getYRot());
            Vec3 vel = fake.getDeltaMovement();
            fake.setDeltaMovement(dir.x * WALK_SPEED, vel.y, dir.z * WALK_SPEED);
        }

        // Reached the far side (solid ground under the next step that is not
        // a block the bridge itself just laid), or the victim moved off: stop
        // bridging.
        boolean nextStepIsFresh = stalker.bridgePlacedPos != null
                && ahead.below().equals(stalker.bridgePlacedPos);
        if ((!gap && !nextStepIsFresh) || horiz > BRIDGE_ABANDON_HORIZ
                || stalker.modeTicks > BRIDGE_MAX_TICKS) {
            stalker.mode = Mode.FOLLOW;
            stalker.modeTicks = 0;
            stalker.bridgePlacedPos = null;
            fake.setShiftKeyDown(false);
            fake.setXRot(0.0F);
        }
    }

    /** Digs the block in front with the vanilla cracking animation, then
     *  breaks it with particles, sound and drops. */
    private static void tickBreak(ServerPlayer player, Stalker stalker) {
        ServerPlayer fake = stalker.fake;
        ServerLevel level = player.serverLevel();
        clearPosture(fake);
        setHand(fake, null);
        face(fake, stalker.breakPos.getX() + 0.5D, stalker.breakPos.getZ() + 0.5D);
        fake.setXRot(0.0F);

        stalker.modeTicks++;
        if (stalker.modeTicks % BREAK_STAGE_TICKS == 0 && stalker.breakStage < 9) {
            stalker.breakStage++;
            level.destroyBlockProgress(fake.getId(), stalker.breakPos, stalker.breakStage);
        }
        if (stalker.breakStage >= 9) {
            level.destroyBlockProgress(fake.getId(), stalker.breakPos, -1);
            level.destroyBlock(stalker.breakPos, true);
            PlayerPlacedBlocks.remove(level, stalker.breakPos);
            stalker.breakBudget--;
            stalker.mode = stalker.breakReturnMode;
            stalker.modeTicks = 0;
        }
    }

    /** Walks along a wall looking for a passable spot or a one-block step
     *  up; escalates to building when nothing turns up. */
    private static void tickClimb(ServerPlayer player, Stalker stalker) {
        ServerPlayer fake = stalker.fake;
        clearPosture(fake);
        stalker.modeTicks++;
        if (stalker.modeTicks > CLIMB_MAX_TICKS) {
            stalker.mode = Mode.FOLLOW;
            stalker.modeTicks = 0;
            return;
        }

        double bx = player.getX() - fake.getX();
        double bz = player.getZ() - fake.getZ();
        double bestScore = -2.0D;
        int bestDx = 0;
        int bestDz = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0 || !isPassable(fake, dx, dz)) {
                    continue;
                }
                double score = (bx * dx + bz * dz)
                        / Math.sqrt(bx * bx + bz * bz + 0.001D)
                        / Math.sqrt(dx * dx + dz * dz);
                if (score > bestScore) {
                    bestScore = score;
                    bestDx = dx;
                    bestDz = dz;
                }
            }
        }
        if (bestScore < -1.0D) {
            // Walled in completely — give the decisions a chance to escalate.
            stalker.mode = Mode.FOLLOW;
            stalker.modeTicks = 0;
            return;
        }
        face(fake, fake.getX() + bestDx, fake.getZ() + bestDz);
        Vec3 dir = facing(fake.getYRot());
        Vec3 vel = fake.getDeltaMovement();
        boolean jump = fake.horizontalCollision && fake.onGround();
        fake.setDeltaMovement(dir.x * WALK_SPEED, jump ? JUMP_SPEED : vel.y,
                dir.z * WALK_SPEED);
    }

    /** Pounce: leaps a narrow gap at the victim when they are close. */
    private static void tickPounce(ServerPlayer player, Stalker stalker) {
        ServerPlayer fake = stalker.fake;
        stalker.modeTicks++;
        clearPosture(fake);
        setHand(fake, null);
        face(fake, player.getX(), player.getZ());
        Vec3 dir = facing(fake.getYRot());
        if (fake.onGround()) {
            // The leap itself; the following ticks only steer in the air.
            fake.setDeltaMovement(dir.x * RUSH_SPEED, JUMP_SPEED, dir.z * RUSH_SPEED);
        } else {
            // Airborne: keep steering but never fight gravity — the leap
            // must arc and fall naturally, so a missed pounce lands (or MLG
            // catches it) instead of hovering forever.
            Vec3 vel = fake.getDeltaMovement();
            fake.setDeltaMovement(dir.x * RUSH_SPEED, vel.y,
                    dir.z * RUSH_SPEED);
        }
        if (fake.onGround() && stalker.modeTicks > 1) {
            stalker.mode = Mode.FOLLOW;
            stalker.modeTicks = 0;
        }
    }

    /** Swim: crosses water toward the victim. */
    private static void tickSwim(ServerPlayer player, Stalker stalker) {
        ServerPlayer fake = stalker.fake;
        stalker.modeTicks++;
        clearPosture(fake);
        fake.setSwimming(true);
        setHand(fake, null);
        face(fake, player.getX(), player.getZ());
        Vec3 dir = facing(fake.getYRot());
        Vec3 vel = fake.getDeltaMovement();
        // Push forward at a fast swim stroke and bob up instead of sinking.
        double up = vel.y < 0.0D ? 0.05D : vel.y;
        fake.setDeltaMovement(dir.x * WALK_SPEED * 1.5D, up,
                dir.z * WALK_SPEED * 1.5D);
        if (!fake.isInWater() && !fake.isUnderWater()) {
            fake.setSwimming(false);
            stalker.mode = Mode.FOLLOW;
            stalker.modeTicks = 0;
        }
    }

    /** Ladder: climbs a ladder or vine up to the victim. */
    private static void tickLadder(ServerPlayer player, Stalker stalker) {
        ServerPlayer fake = stalker.fake;
        stalker.modeTicks++;
        clearPosture(fake);
        setHand(fake, null);
        face(fake, player.getX(), player.getZ());
        Vec3 vel = fake.getDeltaMovement();
        if (fake.onClimbable()) {
            // On the ladder: ride it straight up.
            fake.setDeltaMovement(0.0D, 0.18D, 0.0D);
        } else {
            Vec3 dir = facing(fake.getYRot());
            fake.setDeltaMovement(dir.x * WALK_SPEED, vel.y, dir.z * WALK_SPEED);
        }
        if (stalker.modeTicks > LADDER_MAX_TICKS
                || !fake.onClimbable() && stalker.modeTicks > 30) {
            stalker.mode = Mode.FOLLOW;
            stalker.modeTicks = 0;
        }
    }

    /** Resets posture flags so mode switches never leak sprint/swim state
     *  into the next behaviour's animation. */
    private static void clearPosture(ServerPlayer fake) {
        fake.setSprinting(false);
        fake.setSwimming(false);
    }

    /**
     * {@return a fresh vanilla path from the stalker to the victim's feet,
     * or {@code null} when no route exists}. Computed through a dummy zombie
     * (never added to the world) that mirrors the fake player's position and
     * grounded state, because the pathfinder requires a {@link Mob}.
     */
    private static Path computePath(ServerPlayer player, ServerPlayer fake, Mob pathProxy) {
        ServerLevel level = player.serverLevel();
        pathProxy.moveTo(fake.getX(), fake.getY(), fake.getZ());
        pathProxy.setOnGround(fake.onGround());
        int reach = (int) (PATH_FOLLOW_RANGE + 1.0F);
        BlockPos center = fake.blockPosition();
        PathNavigationRegion region = new PathNavigationRegion(level,
                center.offset(-reach, -8, -reach),
                center.offset(reach, 32, reach));
        Set<BlockPos> targets = Set.of(player.blockPosition());
        return new PathFinder(new WalkNodeEvaluator(), PATH_MAX_NODES)
                .findPath(region, pathProxy, targets,
                        PATH_FOLLOW_RANGE, 1, 1.0F);
    }

    /** {@return whether the player's view actually contains the stalker:
     *  within range, inside the view cone and with nothing in between} */
    private static boolean isOnScreen(ServerPlayer player, ServerPlayer fake) {
        Vec3 eye = player.getEyePosition();
        Vec3 toFake = fake.getEyePosition().subtract(eye);
        double dist = toFake.length();
        if (dist < 0.5D || dist > LOOK_MAX_DISTANCE) {
            return false;
        }
        double cos = player.getLookAngle().dot(toFake.scale(1.0D / dist));
        double angle = Math.acos(Math.max(-1.0D, Math.min(1.0D, cos)));
        if (angle > LOOK_CONE_RAD) {
            return false;
        }
        // Line of sight: a block closer than the stalker hides it.
        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, fake.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == BlockHitResult.Type.BLOCK) {
            double hitDistSq = hit.getLocation().distanceToSqr(eye);
            if (hitDistSq < dist * dist - 0.5D) {
                return false;
            }
        }
        return true;
    }

    /** The stalker gives up being watched: says the line, keeps following
     *  no matter what, and its hunt may now last 3 minutes. */
    private static void giveUp(MinecraftServer server, Stalker stalker) {
        stalker.givenUp = true;
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + GIVE_UP_CHAT), false);
    }

    /** Puts the given item in the stalker's hand ({@code null} empties it),
     *  only touching the entity when the item actually changed so the held
     *  item does not flicker. */
    private static void setHand(ServerPlayer fake, Item item) {
        if (item == null) {
            if (!fake.getMainHandItem().isEmpty()) {
                fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        } else if (fake.getMainHandItem().getItem() != item) {
            fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        }
    }

    /** {@return the block one block ahead of the stalker at feet level, in
     *  the direction it is facing} */
    private static BlockPos aheadBlock(ServerPlayer fake) {
        Vec3 dir = facing(fake.getYRot());
        return fake.blockPosition().offset((int) Math.round(dir.x), 0,
                (int) Math.round(dir.z));
    }

    /** {@return whether the block in front of the stalker is a gap it would
     *  fall into — air over air, fluid or the void} */
    private static boolean isGapAhead(ServerPlayer fake) {
        ServerLevel level = (ServerLevel) fake.level();
        BlockPos ahead = aheadBlock(fake);
        if (!level.getBlockState(ahead).isAir()) {
            return false;
        }
        BlockPos below = ahead.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isAir() || !belowState.getFluidState().isEmpty()
                || below.getY() < level.getMinBuildHeight();
    }

    /** {@return whether the stalker stands at the edge of a cliff — air in
     *  front and no ground for at least 3 blocks down} */
    private static boolean isCliffAhead(ServerPlayer fake) {
        ServerLevel level = (ServerLevel) fake.level();
        BlockPos ahead = aheadBlock(fake);
        if (!level.getBlockState(ahead).isAir()) {
            return false;
        }
        for (int i = 1; i <= 3; i++) {
            if (!level.getBlockState(ahead.below(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** {@return whether the stalker can step into the block one ahead and
     *  one beside it} */
    private static boolean isPassable(ServerPlayer fake, int dx, int dz) {
        ServerLevel level = (ServerLevel) fake.level();
        BlockPos feet = fake.blockPosition().offset(dx, 0, dz);
        if (!level.getBlockState(feet.above()).isAir()) {
            return false;
        }
        BlockState s = level.getBlockState(feet);
        return s.isAir() || s.isSolid() || !s.getFluidState().isEmpty();
    }

    /** {@return whether the block is worth digging: solid, not a fluid and
     *  not absurdly hard (bedrock, obsidian)} */
    private static boolean isBreakable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.isSolid() || !state.getFluidState().isEmpty()) {
            return false;
        }
        float speed = state.getDestroySpeed(level, pos);
        return speed >= 0.0F && speed <= 6.0F;
    }

    /** Switches the stalker into BREAK for the given block, resuming {@code
     *  returnMode} once the block is gone. */
    private static void startBreak(Stalker stalker, BlockPos pos, Mode returnMode) {
        stalker.mode = Mode.BREAK;
        stalker.modeTicks = 0;
        stalker.breakPos = pos;
        stalker.breakStage = 0;
        stalker.breakReturnMode = returnMode;
    }

    /** {@return the first solid, breakable block at head height directly
     *  above the stalker, or {@code null} when the sky is clear} */
    private static BlockPos solidAbove(ServerLevel level, ServerPlayer fake) {
        BlockPos feet = fake.blockPosition();
        for (int i = 1; i <= 2; i++) {
            BlockPos p = feet.above(i);
            if (isBreakable(level, p, level.getBlockState(p))) {
                return p;
            }
        }
        return null;
    }

    /** {@return how many blocks wide the gap directly ahead is — the number
     *  of open cells before the first solid footing or wall} */
    private static int gapWidth(ServerPlayer fake) {
        ServerLevel level = (ServerLevel) fake.level();
        Vec3 dir = facing(fake.getYRot());
        int y = fake.blockPosition().getY();
        for (int i = 1; i <= 8; i++) {
            BlockPos p = BlockPos.containing(
                    fake.getX() + dir.x * i, y, fake.getZ() + dir.z * i);
            BlockState s = level.getBlockState(p);
            if (!s.isAir()) {
                return i - 1;
            }
            BlockState below = level.getBlockState(p.below());
            if (below.getFluidState().isEmpty() && !below.isAir()) {
                return i - 1;
            }
        }
        return 8;
    }

    /** {@return whether the stalker is in water or about to step into it} */
    private static boolean waterAhead(ServerPlayer fake) {
        ServerLevel level = (ServerLevel) fake.level();
        if (fake.isInWater() || fake.isUnderWater()) {
            return true;
        }
        BlockPos ahead = aheadBlock(fake);
        BlockState s = level.getBlockState(ahead);
        return !s.isAir() && s.getFluidState().is(FluidTags.WATER);
    }

    /** {@return whether a ladder, vine or other climbable block sits ahead
     *  of or above the stalker} */
    private static boolean ladderAhead(ServerPlayer fake) {
        ServerLevel level = (ServerLevel) fake.level();
        BlockPos feet = fake.blockPosition();
        for (int i = 1; i <= 2; i++) {
            BlockPos p = feet.above(i);
            if (level.getBlockState(p).is(BlockTags.CLIMBABLE)) {
                return true;
            }
        }
        return false;
    }

    /** {@return how far the ground is below the stalker's feet, in blocks —
     *  {@code 0} when standing or when water is below} */
    private static double cliffDrop(ServerPlayer fake) {
        ServerLevel level = (ServerLevel) fake.level();
        BlockPos feet = fake.blockPosition();
        for (int i = 1; i <= 32; i++) {
            BlockState s = level.getBlockState(feet.below(i));
            if (s.isAir()) {
                continue;
            }
            return s.getFluidState().isEmpty() ? i : 0.0D;
        }
        return 0.0D;
    }

    /** The catch: the stalker vanishes with darkness II, a max-volume laggy2
     *  blast bound to the victim, and the client's red-static flash. */
    private static void catchPlayer(MinecraftServer server, ServerPlayer player, Stalker stalker) {
        stalker.fake.discard();
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                DARKNESS_DURATION_TICKS, 1, false, false, false));
        player.serverLevel().playSound(null, player,
                ModSounds.DAY7_FAKE, SoundSource.AMBIENT, LAGGY2_VOLUME, 1.0F);
        ServerPlayNetworking.send(player, NonameEventPayload.play("day13_stalker"));
    }

    /** Spawns the stalker 15 blocks behind the victim, on the ground. */
    private static void triggerForPlayer(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (stalkers.containsKey(uuid)) {
            return;
        }
        ServerLevel level = player.serverLevel();

        // Where the player is NOT facing, 15 blocks out (horizontal only),
        // on the ground.
        Vec3 behind = player.position().subtract(
                facing(player.getYRot()).scale(SPAWN_DISTANCE));
        int groundX = (int) Math.floor(behind.x);
        int groundZ = (int) Math.floor(behind.z);
        double groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, groundX, groundZ) + 1.0D;

        GameProfile profile = new GameProfile(FakePlayerUtil.FAKE_UUID, FakePlayerUtil.FAKE_NAME);
        ServerPlayer fake = new ServerPlayer(server, level, profile,
                ClientInformation.createDefault());
        fake.moveTo(behind.x, groundY, behind.z, player.getYRot(), 0.0F);
        // The player model turns with yHeadRot/yBodyRot, not yRot.
        fake.yHeadRot = fake.getYRot();
        fake.yBodyRot = fake.getYRot();
        fake.setInvulnerable(true);
        fake.setSilent(true);
        // Deliberately NOT noPhysics: the stalker must collide with the
        // world so it walks around obstacles instead of phasing through.
        fake.connection = new ServerGamePacketListenerImpl(server,
                FakePlayerHandler.createDummyConnection(), fake,
                CommonListenerCookie.createInitial(profile, false));
        // The client only creates a player-type entity when it knows the
        // UUID from the player list, so register the profile in the victim's
        // tab list before the entity itself arrives.
        player.connection.send(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, fake));

        Mob pathProxy = EntityType.ZOMBIE.create(level);
        if (pathProxy == null) {
            fake.discard();
            return;
        }
        level.addFreshEntity(fake);

        stalkers.put(uuid, new Stalker(fake, pathProxy, server.getTickCount()));

        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "<" + FakePlayerUtil.FAKE_TAB_NAME + "> " + SPAWN_CHAT), false);
        // Cave sound bound to the stalker entity itself, so it follows them
        // as they walk.
        level.playSound(null, fake, SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.AMBIENT, CAVE_VOLUME, 1.0F);
    }

    /** Dev/test hook — spawn a stalker behind every online player right now,
     *  bypassing the day-13 gate and the roll timer. Dispatched by
     *  {@code /noname event play day13_stalker}. */
    public static void triggerForAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(FakePlayerUtil.FAKE_UUID)) {
                continue;
            }
            triggerForPlayer(server, player);
        }
    }

    /** Dev/test hook — remove every stalker and cancel the armed rolls.
     *  Used by {@code /noname event stopall}. */
    public static void stopAll() {
        for (Stalker stalker : stalkers.values()) {
            stalker.fake.discard();
        }
        stalkers.clear();
        ticksUntilRoll.clear();
    }

    /** Destroys the given stalker with the Cross and ends its hunt.
     *  {@return whether this handler owned the stalker} */
    public static boolean destroyStalker(ServerPlayer fake) {
        for (var it = stalkers.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue().fake == fake) {
                it.remove();
                fake.discard();
                return true;
            }
        }
        return false;
    }

    /** Points the stalker's whole body (yRot plus the model's head and body
     *  rotations) at the given position. */
    private static void face(ServerPlayer fake, double x, double z) {
        double dx = x - fake.getX();
        double dz = z - fake.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        fake.setYRot(yaw);
        fake.yHeadRot = yaw;
        fake.yBodyRot = yaw;
    }

    /** {@return the horizontal facing unit vector for a yaw in degrees, in
     *  vanilla's convention (yaw 0 = +Z, turning left rotates counterclock-
     *  wise when seen from above)} */
    private static Vec3 facing(float yRot) {
        double rad = Math.toRadians(yRot);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }
}
