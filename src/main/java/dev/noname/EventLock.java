package dev.noname;

/**
 * A single global lock that keeps the non-structure Noname events from
 * stacking on top of each other: while one event is running, the other
 * events' natural rolls are skipped. Structure events (creepy signs,
 * bedrock pillars, flesh trees) don't participate — they are exempt by
 * design.
 *
 * <p>Every event that takes the lock must release it again on ALL of its
 * exit paths: the natural end, the {@code /noname event stopall} teardown
 * and (for client handlers) leaving the world mid-event. The dev/test
 * command bypasses the lock on purpose: {@code /noname event play x} calls
 * the trigger methods directly, so it fires even while another event runs.
 *
 * <p>Ownership is tracked by the event's name; {@link #release(String)} only
 * clears the lock if the caller is actually the owner, so a stray release
 * from a dev-triggered event never unlocks someone else's event.
 *
 * <p>This now delegates to {@link EventQueue} which queues events and runs
 * them sequentially with 10-20 second delays between them.
 */
public final class EventLock {

    private EventLock() {
    }

    /**
     * Tries to acquire the event lock. If another event is running, the event
     * is queued instead of being rejected. Returns true if the event can run
     * immediately (queue is empty and no event running), false if queued.
     *
     * @param eventName name of the event trying to acquire
     * @return true if event runs now, false if queued for later
     */
    public static boolean tryAcquire(String eventName) {
        if (EventQueue.isRunning()) {
            return false; // will be queued by caller
        }
        if (EventQueue.queueSize() > 0) {
            return false; // will be queued by caller
        }
        return EventQueue.tryAcquireImmediate(eventName);
    }

    /** {@return whether any non-structure event is currently running} */
    public static boolean isLocked() {
        return EventQueue.isRunning();
    }

    /** {@return the name of the event holding the lock, or {@code null}} */
    public static String owner() {
        return EventQueue.currentEventName();
    }

    /** Releases the lock if {@code eventName} is the current owner. */
    public static void release(String eventName) {
        EventQueue.release(eventName);
    }

    /** Forces the lock free, regardless of owner. Used by stopall. */
    public static void releaseAll() {
        EventQueue.releaseAll();
    }
}
