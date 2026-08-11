package dev.noname;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world save of the Noname effects that must not repeat across sessions.
 * Stored as {@code data/noname.dat} next to the overworld's own data, so a
 * player leaving and rejoining the world (or a server restart) does not
 * replay effects that already happened.
 *
 * <p>Currently tracks how many of the ghost's chat lines were already said;
 * once all lines are recorded as sent, the ghost stays silent forever in
 * that world. Also tracks whether the initial content warning has been shown.
 * And the day-10 question state per player (asked-but-unanswered / punishment
 * owed), so the question survives logouts and server restarts.
 */
public final class NonameSavedData extends SavedData {

    public static final String ID = "noname";

    private static final String TAG_GHOST_LINES_SENT = "GhostLinesSent";
    private static final String TAG_WARNING_SHOWN = "WarningShown";
    private static final String TAG_KEEP_INVENTORY_PROMPT_SHOWN = "KeepInventoryPromptShown";
    private static final String TAG_MEAT_QUESTION = "MeatQuestion";
    private static final String TAG_INFECTION_SEEDED = "InfectionSeeded";

    private int ghostLinesSent;
    private boolean warningShown;
    private boolean keepInventoryPromptShown;

    /** Player UUID string -> day-10 question state (see
     *  {@link dev.noname.Day10MeatQuestionHandler}: 1 = asked but not
     *  answered, 2 = answered "no", punishment owed). Absent = no state. */
    private final Map<String, Byte> meatQuestionState = new HashMap<>();

    /** Player UUID strings of players whose day-17 world-infection seed was
     *  already placed, so nobody is seeded twice (see
     *  {@link dev.noname.InfectionHandler}). */
    private final Set<String> infectionSeeded = new HashSet<>();

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
        data.keepInventoryPromptShown = tag.getBoolean(TAG_KEEP_INVENTORY_PROMPT_SHOWN);
        ListTag meatList = tag.getList(TAG_MEAT_QUESTION, Tag.TAG_COMPOUND);
        for (int i = 0; i < meatList.size(); i++) {
            CompoundTag entry = meatList.getCompound(i);
            data.meatQuestionState.put(entry.getString("U"), entry.getByte("S"));
        }
        ListTag seededList = tag.getList(TAG_INFECTION_SEEDED, Tag.TAG_STRING);
        for (int i = 0; i < seededList.size(); i++) {
            data.infectionSeeded.add(seededList.getString(i));
        }
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

    /** {@return true if the keep-inventory prompt has already been shown} */
    public boolean isKeepInventoryPromptShown() {
        return keepInventoryPromptShown;
    }

    /** Marks the keep-inventory prompt as shown and saves. */
    public void markKeepInventoryPromptShown() {
        if (!keepInventoryPromptShown) {
            keepInventoryPromptShown = true;
            setDirty();
        }
    }

    /** {@return the day-10 question state of a player: 0 = none, 1 = asked
     *  but not answered, 2 = "no" answered, punishment owed} */
    public byte getMeatQuestionState(UUID playerId) {
        return meatQuestionState.getOrDefault(playerId.toString(), (byte) 0);
    }

    /** Sets the day-10 question state of a player (0 clears it) and saves. */
    public void setMeatQuestionState(UUID playerId, byte state) {
        if (state == 0) {
            if (meatQuestionState.remove(playerId.toString()) != null) {
                setDirty();
            }
        } else {
            Byte previous = meatQuestionState.put(playerId.toString(), state);
            if (previous == null || previous != state) {
                setDirty();
            }
        }
    }

    /** {@return true if the day-17 world-infection seed was already placed
     *  for this player in this world} */
    public boolean isInfectionSeeded(UUID playerId) {
        return infectionSeeded.contains(playerId.toString());
    }

    /** Records that the player's infection seed was placed and saves. */
    public void markInfectionSeeded(UUID playerId) {
        if (infectionSeeded.add(playerId.toString())) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt(TAG_GHOST_LINES_SENT, ghostLinesSent);
        tag.putBoolean(TAG_WARNING_SHOWN, warningShown);
        tag.putBoolean(TAG_KEEP_INVENTORY_PROMPT_SHOWN, keepInventoryPromptShown);
        ListTag meatList = new ListTag();
        for (Map.Entry<String, Byte> entry : meatQuestionState.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("U", entry.getKey());
            item.putByte("S", entry.getValue());
            meatList.add(item);
        }
        tag.put(TAG_MEAT_QUESTION, meatList);
        ListTag seededList = new ListTag();
        for (String playerId : infectionSeeded) {
            seededList.add(StringTag.valueOf(playerId));
        }
        tag.put(TAG_INFECTION_SEEDED, seededList);
        return tag;
    }
}
