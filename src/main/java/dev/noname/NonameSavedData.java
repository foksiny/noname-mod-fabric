package dev.noname;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-world save of the Noname effects that must not repeat across sessions.
 * Stored as {@code data/noname.dat} next to the overworld's own data, so a
 * player leaving and rejoining the world (or a server restart) does not
 * replay effects that already happened.
 *
 * <p>Currently tracks how many of the ghost's chat lines were already said;
 * once all lines are recorded as sent, the ghost stays silent forever in
 * that world. Also tracks whether the initial content warning has been shown.
 */
public final class NonameSavedData extends SavedData {

    public static final String ID = "noname";

    private static final String TAG_GHOST_LINES_SENT = "GhostLinesSent";
    private static final String TAG_WARNING_SHOWN = "WarningShown";

    private int ghostLinesSent;
    private boolean warningShown;

    private NonameSavedData() {
    }

    /** Factory for {@link DimensionDataStorage#computeIfAbsent}. */
    public static Factory<NonameSavedData> factory() {
        return new Factory<>(
                NonameSavedData::new,
                NonameSavedData::load,
                DataFixTypes.SAVED_DATA_MAP_DATA);
    }

    private static NonameSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        NonameSavedData data = new NonameSavedData();
        data.ghostLinesSent = tag.getInt(TAG_GHOST_LINES_SENT);
        data.warningShown = tag.getBoolean(TAG_WARNING_SHOWN);
        return data;
    }

    /** {@return how many of the ghost's chat lines have already been said in
     *  this world (0 = none yet)} */
    public int getGhostLinesSent() {
        return ghostLinesSent;
    }

    /** Records that the ghost line at {@code index} (0-based) was sent, and
     *  marks the data dirty so it is written to disk. */
    public void markGhostLineSent(int index) {
        if (ghostLinesSent < index + 1) {
            ghostLinesSent = index + 1;
            setDirty();
        }
    }

    /** {@return true if the initial content warning has already been shown} */
    public boolean isWarningShown() {
        return warningShown;
    }

    /** Marks the warning as shown and saves. */
    public void markWarningShown() {
        if (!warningShown) {
            warningShown = true;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt(TAG_GHOST_LINES_SENT, ghostLinesSent);
        tag.putBoolean(TAG_WARNING_SHOWN, warningShown);
        return tag;
    }
}
