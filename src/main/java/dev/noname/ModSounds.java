package dev.noname;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events added by Noname. Registered straight into the vanilla
 * {@link BuiltInRegistries#SOUND_EVENT} registry at mod init so the events
 * exist by id ({@code noname:it_hurts_to_see}) and can be fetched as typed
 * {@link SoundEvent}s for server-side playback.
 */
public final class ModSounds {

    /**
     * "It hurts to see" — the ambient sting that plays one minute after the
     * ghost player joins on day 3. The event's id and the {@code sounds.json}
     * entry stay in sync because the location is derived from the name.
     */
    public static final SoundEvent IT_HURTS_TO_SEE = createSoundEvent("it_hurts_to_see");

    /**
     * "Creepy bass ambience" — the low, ominous stinger that, from day 4 on,
     * occasionally forces the client's render distance down to the minimum
     * and plays for the duration of the drop.
     */
    public static final SoundEvent CREEPY_BASS = createSoundEvent("creepy_bass");

    /**
     * "Day-5 flash" — the short glitch played together with the black
     * "i can't stop doing it" screen flash from day 5 on.
     */
    public static final SoundEvent DAY5_FLASH = createSoundEvent("day5_flash");

    /**
     * "Day-7 apparition" — the glitch (from {@code laggy2.ogg}) that plays at
     * the fake player's position when it appears in front of the player at
     * the start of day 7.
     */
    public static final SoundEvent DAY7_FAKE = createSoundEvent("day7_fake");

    /**
     * "Tearing flesh" — the squelch that plays when a named blood mob dies
     * (from {@code TearingFlesh.ogg}), together with the falling blood
     * particles.
     */
    public static final SoundEvent TEARING_FLESH = createSoundEvent("tearing_flesh");

    /**
     * "Day-8 ambience" — the long horror drone (from the user's
     * {@code Typical Horror Ambience} mp3) that, from day 8 on, occasionally
     * plays under the red-sky event with a 1.5 s fade-in, a fade-out that
     * starts at 5 s, and a hard stop right after.
     */
    public static final SoundEvent HORROR_AMBIENCE = createSoundEvent("horror_ambience");

    /**
     * "Day-10 lag" — the user's {@code laggy3.ogg} played inside the ears
     * while the day-10+ lag event forces the camera behind the player.
     */
    public static final SoundEvent LAGGY3 = createSoundEvent("laggy3");

    /**
     * "He is here" — the user's {@code he-is-here.ogg}, cut to its first 27
     * seconds. Plays (client-side, never re-pitched) for the whole day-11+
     * secret event; if the chaser catches the player it is cut short and the
     * song jumps to second 25 ({@link #HE_IS_HERE_25}); if the player
     * survives, it simply reaches its end at second 27 and everything stops.
     */
    public static final SoundEvent HE_IS_HERE = createSoundEvent("he_is_here");

    /**
     * "He is here, from second 25" — the tail of the user's {@code
     * he-is-here.ogg} (second 25 to the end). Played when the friend catches
     * the player, so the song "jumps" to second 25 the moment they die.
     */
    public static final SoundEvent HE_IS_HERE_25 = createSoundEvent("he_is_here_25");

    /**
     * "Laggy 1" — the glitchy sound played when a day-5+ pig is killed and the
     * fake player appears at its death position.
     */
    public static final SoundEvent LAGGY1 = createSoundEvent("laggy1");

    /**
     * "Day-6 static" — the TV static sound that plays in a loop during the
     * day-6 middle-of-day overlay event.
     */
    public static final SoundEvent DAY6_STATIC = createSoundEvent("day6_static");

    /**
     * "Day-6 blip" — the short blip sound played when each text line appears
     * during the day-6 event.
     */
    public static final SoundEvent DAY6_BLIP = createSoundEvent("day6_blip");

    /**
     * "Tape motor" — the looping cassette-tape motor hum that plays in the
     * background at 40% volume for the whole session after joining a world,
     * reinforcing the old-VHS atmosphere.
     */
    public static final SoundEvent TAPE_MOTOR = createSoundEvent("tape_motor");

    /**
     * "Day-6 laughs" — the laughs sound played when the day-6 static event ends.
     */
    public static final SoundEvent DAY6_LAUGHS = createSoundEvent("day6_laughs");

    private ModSounds() {
    }

    /** Registers every sound event into the sound-event registry. */
    public static void register() {
        registerSoundEvent(IT_HURTS_TO_SEE);
        registerSoundEvent(CREEPY_BASS);
        registerSoundEvent(DAY5_FLASH);
        registerSoundEvent(DAY7_FAKE);
        registerSoundEvent(TEARING_FLESH);
        registerSoundEvent(HORROR_AMBIENCE);
        registerSoundEvent(LAGGY3);
        registerSoundEvent(HE_IS_HERE);
        registerSoundEvent(HE_IS_HERE_25);
        registerSoundEvent(LAGGY1);
        registerSoundEvent(DAY6_STATIC);
        registerSoundEvent(DAY6_BLIP);
        registerSoundEvent(TAPE_MOTOR);
        registerSoundEvent(DAY6_LAUGHS);
    }

    private static SoundEvent createSoundEvent(String name) {
        return SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Noname.MODID, name));
    }

    private static void registerSoundEvent(SoundEvent event) {
        Registry.register(BuiltInRegistries.SOUND_EVENT, event.getLocation(), event);
    }
}
