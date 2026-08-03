package dev.noname;

import java.util.UUID;

/**
 * Identity of the ghost player: a fixed UUID (so the client can match it for
 * the custom skin), the name shown in the join message, and the separate
 * display name shown in the player list tab.
 */
public final class FakePlayerUtil {

    /**
     * Fixed UUID of the ghost player. Kept constant so the client-side skin
     * replacement can recognize it.
     */
    public static final UUID FAKE_UUID = UUID.fromString("f3a7c9d1-2b4e-4f6a-8b0c-1d2e3f4a5b6c");

    /** Profile name — shown in the "<name> joined the game" message.
     *  Limited to 16 chars by the protocol (ClientboundPlayerInfoUpdatePacket). */
    public static final String FAKE_NAME = "player.name.err";

    /** Tab list display name — shown in the player list. */
    public static final String FAKE_TAB_NAME = "你的朋友";

    /**
     * Fixed UUID of the day-2 {@code null} visitor. Kept constant so the
     * client-side black skin replacement can recognize it. Distinct from
     * {@link #FAKE_UUID} so the two fake players never clash in the player
     * list if they coexist (e.g. via {@code /noname event play}).
     */
    public static final UUID NULL_UUID =
            UUID.fromString("0b1e2f3a-4d5c-6b7a-8901-234567890abc");

    /** Profile name of the day-2 {@code null} visitor, shown both in the
     *  vanilla "{@code null joined the game}" / "{@code null left the game}"
     *  messages and as the {@code <null> ...} chat prefix. */
    public static final String NULL_NAME = "null";

    private FakePlayerUtil() {
    }
}
