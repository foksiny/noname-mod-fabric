package dev.noname;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Queue system for Noname events. Events are queued and executed one at a time
 * with a random 10-20 second delay between them. Only events whose conditions
 * are met (return true from {@link QueuedEvent#shouldRun}) actually execute.
 *
 * <p>This replaces the old {@link EventLock} which simply blocked concurrent
 * events. Now multiple events can be queued and will play in sequence.
 */
public final class EventQueue {

    /** A queued event with its execution logic and condition check. */
    public record QueuedEvent(
            String name,
            Supplier<Boolean> shouldRun,
            Runnable action
    ) {
    }

    private static final Queue<QueuedEvent> queue = new ArrayDeque<>();
    private static QueuedEvent currentEvent = null;
    private static long nextEventTick = -1;
    private static final Random random = new Random();
    private static MinecraftServer serverRef = null;

    private static final int MIN_DELAY_TICKS = 20 * 10; // 10 seconds
    private static final int MAX_DELAY_TICKS = 20 * 20; // 20 seconds

    private EventQueue() {
    }

    /**
     * Sets the server reference for tick counting. Must be called once on init.
     */
    public static void setServer(MinecraftServer server) {
        serverRef = server;
    }

    /**
     * Queues an event to run when its turn comes. The event will only actually
     * execute if {@code shouldRun} returns true at execution time.
     *
     * @param name       unique event name (for debugging/stopall)
     * @param shouldRun  condition checked at execution time; if false, event is skipped
     * @param action     the event logic to run if shouldRun returns true
     */
    public static void queueEvent(String name, Supplier<Boolean> shouldRun, Runnable action) {
        queue.add(new QueuedEvent(name, shouldRun, action));
    }

    /**
     * Tries to acquire the "lock" for an event immediately (bypassing queue).
     * Used by dev commands ({@code /noname event play}) which should fire
     * regardless of queue state.
     *
     * @return true if no event is currently running and queue is empty
     */
    public static boolean tryAcquireImmediate(String eventName) {
        return currentEvent == null && queue.isEmpty();
    }

    /**
     * Releases the current event. Called when an event finishes naturally
     * or via {@code /noname event stopall}.
     */
    public static void release(String eventName) {
        if (currentEvent != null && currentEvent.name().equals(eventName)) {
            finishCurrentEvent();
        }
    }

    /** Forces release of any current event and clears the queue. Used by stopall. */
    public static void releaseAll() {
        currentEvent = null;
        queue.clear();
        nextEventTick = -1;
    }

    /** {@return true if an event is currently running} */
    public static boolean isRunning() {
        return currentEvent != null;
    }

    /** {@return name of currently running event, or null} */
    public static String currentEventName() {
        return currentEvent != null ? currentEvent.name() : null;
    }

    /** {@return number of events waiting in queue} */
    public static int queueSize() {
        return queue.size();
    }

    /** @return the server reference for condition checks, or null if not set */
    static MinecraftServer getServerForCheck() {
        return serverRef;
    }

    /**
     * Must be called every server tick to process the queue.
     */
    public static void onServerTick(MinecraftServer server) {
        if (serverRef == null) {
            serverRef = server;
        }

        long now = server.getTickCount();

        // If an event is running, check if it's done (caller must call release)
        if (currentEvent != null) {
            return; // current event controls its own release
        }

        // If waiting for delay between events
        if (nextEventTick > 0 && now < nextEventTick) {
            return;
        }

        // Try to start next queued event
        if (!queue.isEmpty()) {
            QueuedEvent next = queue.peek();
            if (next.shouldRun().get()) {
                queue.poll();
                startEvent(next, server);
            } else {
                // Condition not met, skip this event
                queue.poll();
            }
        }
    }

    private static void startEvent(QueuedEvent event, MinecraftServer server) {
        currentEvent = event;
        event.action().run();
        // Note: the event must call EventQueue.release(name) when done
    }

    private static void finishCurrentEvent() {
        currentEvent = null;
        // Schedule next event after 10-20 second delay
        int delay = MIN_DELAY_TICKS + random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
        nextEventTick = serverRef != null ? serverRef.getTickCount() + delay : -1;
    }
}