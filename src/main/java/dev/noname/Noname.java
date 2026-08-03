package dev.noname;

import dev.noname.command.NonameCommand;
import dev.noname.network.ModPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

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
        ModSounds.register();
        ModParticles.register();
        ModBlocks.register();
        ModItems.register();
        ModPayloads.registerCommon();
        ModPayloads.registerServer();

        ServerTickEvents.START_SERVER_TICK.register(EventQueue::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(FakePlayerHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(GhostChatHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(KilledAnimalsLootHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day4KnifeHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day3TimeSkipHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day2NullJoinHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day7FakePlayerHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(HostileMobHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(NamedMobBehaviourHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(SignPlacerHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day10LookHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(DoorHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(BloodFleshBlockHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day11ChestHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(HeIsHereHandler::onServerTick);
        ServerTickEvents.START_SERVER_TICK.register(Day5PigHandler::onServerTick);
        ServerLivingEntityEvents.AFTER_DEATH.register(BloodMobHandler::onDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register(Day5PigHandler::onDeath);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(Day4KnifeHandler::onChatMessage);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(GhostChatHandler::onChatMessage);
        ServerPlayConnectionEvents.JOIN.register(JoinMessageHandler::onPlayerJoin);
        CommandRegistrationCallback.EVENT.register(NonameCommand::onRegisterCommands);
    }
}
