package dev.noname;

import dev.noname.command.NonameCommand;
import dev.noname.config.ModConfig;
import dev.noname.network.ModPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * Main mod class for Noname.
 *
 * <p>Loads with the Fabric loader and wires up every server-side behaviour:
 * sound registration, the event payload type, and all the global event
 * callbacks (server ticks, join message, commands). Client-side effects are
 * registered by {@code dev.noname.client.NonameClient}.
 */
public class Noname implements ModInitializer {

    /** Mod id, must match {@code id} in fabric.mod.json. */
    public static final String MODID = "noname";

    /** Human-readable mod name. */
    public static final String MOD_NAME = "Noname";

    @Override
    public void onInitialize() {
        ModConfig.load();
        ModSounds.register();
        ModParticles.register();
        ModBlocks.register();
        ModItems.register();
        ModEntities.register();
        ModPayloads.registerCommon();
        ModPayloads.registerServer();

        ServerTickEvents.START_SERVER_TICK.register(EventQueue::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(BloodyNightHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(FakePlayerHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(GhostChatHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(KilledAnimalsLootHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day4KnifeHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day4LightningHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day3TimeSkipHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day3StalkerHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day2NullJoinHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day7FakePlayerHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(HostileMobHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(NamedMobBehaviourHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(SignPlacerHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day10LookHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day10MeatQuestionHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(DoorHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(DoorKnockHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(DoorAmbushHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(BloodFleshBlockHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day11ChestHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(HeIsHereHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day5PigHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(IseItHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(CaveZombieHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(CaveDiggingSoundHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(HorseKillHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(CameraSpasmHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day5DesktopHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(ChunkDeletionHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day13StalkerHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day14DeathHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(ItemThiefHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day8SmileHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(BlockBlinkHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(AnimalRevengeHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(InfectionHandler::onServerTick);
        ServerLivingEntityEvents.AFTER_DEATH.register(BloodMobHandler::onDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register(Day5PigHandler::onDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register(MeatDropHandler::onDeath);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(AnimalRevengeHandler::onAfterDamage);
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) ->
                PlayerPlacedBlocks.remove((ServerLevel) world, pos));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(Day4KnifeHandler::onChatMessage);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(GhostChatHandler::onChatMessage);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(MeatChatHandler::onChatMessage);
        ServerPlayConnectionEvents.JOIN.register(JoinMessageHandler::onPlayerJoin);
        ServerPlayConnectionEvents.JOIN.register(Day10MeatQuestionHandler::onPlayerJoin);
        ServerPlayConnectionEvents.JOIN.register(InfectionHandler::onPlayerJoin);
        ServerPlayerEvents.AFTER_RESPAWN.register(Day14DeathHandler::onRespawn);
        CommandRegistrationCallback.EVENT.register(NonameCommand::onRegisterCommands);
    }
}
