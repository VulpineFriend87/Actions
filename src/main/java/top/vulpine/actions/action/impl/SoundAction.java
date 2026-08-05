package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;
import top.vulpine.actions.target.Target;

import java.util.List;
import java.util.Locale;

/**
 * Plays a sound.
 *
 * <pre>{@code
 * - type: sound
 *   target: self
 *   key: "entity.player.levelup"
 *   volume: 1.0
 *   pitch: 1.4
 *   source: master
 *
 * - "[sound] self; entity.player.levelup; 1; 1.4"
 * }</pre>
 *
 * <h2>Namespaced keys, not enum names</h2>
 * <p>Never touches {@code org.bukkit.Sound}: that type changed from a class to an
 * interface in 1.21.3, so a jar compiled against an older API cannot call
 * {@code Sound.valueOf} at all. Sending the key over the protocol behaves the same on
 * every version, and lets resource pack sounds work too.</p>
 *
 * <p>{@code ENTITY_PLAYER_LEVELUP} is therefore <strong>not</strong> accepted — keys
 * are lowercase and dotted ({@code entity.player.levelup}). The two forms are not
 * related by a string transform: many sounds keep an underscore inside a segment, so
 * {@code BLOCK_NOTE_BLOCK_PLING} is {@code block.note_block.pling} and not
 * {@code block.note.block.pling}. Translating an existing config needs the server's own
 * sound registry, which belongs in the plugin doing the migration.</p>
 *
 * <h2>Validated when the config loads</h2>
 * <p>{@link Key#key(String)} throws on a malformed key. Left to runtime that would be
 * an exception per player per join; checked at load it is one warning at startup, and
 * the action becomes inert.</p>
 */
public final class SoundAction implements Action {

    /** The registered id. */
    public static final String TYPE = "sound";

    private final Target target;
    private final String configuredKey;
    private final Sound sound;
    private final float volume;
    private final float pitch;
    private final Sound.Source source;
    private final String shorthand;

    private SoundAction(final Target target, final String configuredKey, final Sound sound,
                        final float volume, final float pitch, final Sound.Source source,
                        final String shorthand) {

        this.target = target;
        this.configuredKey = configuredKey;
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
        this.source = source;
        this.shorthand = shorthand;
    }

    /**
     * Starts building one in code, for a config default.
     *
     * <p>The key is validated in {@link Builder#build()}, so a mistake in a default
     * is a warning at startup rather than a sound that never plays.</p>
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the settings of a sound.
     */
    public static final class Builder {

        private Target target = Target.SELF;
        private String key;
        private float volume = 1F;
        private float pitch = 1F;
        private Sound.Source source = Sound.Source.MASTER;

        private Builder() {
        }

        /** @param value who hears it @return this builder */
        public Builder target(final Target value) {
            this.target = value == null ? Target.SELF : value;
            return this;
        }

        /** @param value the namespaced key, e.g. {@code entity.player.levelup} @return this builder */
        public Builder key(final String value) {
            this.key = value;
            return this;
        }

        /** @param value the volume @return this builder */
        public Builder volume(final float value) {
            this.volume = value;
            return this;
        }

        /** @param value the pitch @return this builder */
        public Builder pitch(final float value) {
            this.pitch = value;
            return this;
        }

        /** @param value which volume slider applies @return this builder */
        public Builder source(final Sound.Source value) {
            this.source = value == null ? Sound.Source.MASTER : value;
            return this;
        }

        /** @return the action */
        public SoundAction build() {
            return new SoundAction(target, key, sound(key), volume, pitch, source, null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static SoundAction read(final DeserializationData data) {

        return build(
                Target.parse(data.containsKey("target") ? data.get("target", String.class) : null),
                data.containsKey("key") ? data.get("key", String.class) : null,
                data.containsKey("volume") ? data.get("volume", String.class) : null,
                data.containsKey("pitch") ? data.get("pitch", String.class) : null,
                data.containsKey("source") ? data.get("source", String.class) : null,
                null);
    }

    /**
     * Builds from {@code [sound] <target>; <key>; [volume]; [pitch]}.
     *
     * @param params the text after the tag
     * @param raw the whole line
     * @return the action
     */
    public static SoundAction parse(final String params, final String raw) {

        String[] parts = params.split(";");

        return build(
                Target.parse(at(parts, 0)),
                at(parts, 1),
                at(parts, 2),
                at(parts, 3),
                at(parts, 4),
                raw);
    }

    private static String at(final String[] parts, final int index) {
        return index < parts.length ? parts[index].trim() : null;
    }

    private static SoundAction build(final Target target, final String key, final String volume,
                                     final String pitch, final String source, final String shorthand) {

        return new SoundAction(target, key, sound(key), number(volume, 1F, "volume"),
                number(pitch, 1F, "pitch"), source(source), shorthand);
    }

    private static Sound sound(final String key) {

        if (key == null || key.isBlank()) {
            // A blank key disables the sound, so it can be turned off from config by
            // clearing it rather than deleting the block.
            return null;
        }

        String value = key.trim();

        // Checked before parsing, and refused rather than lowercased. Underscores are
        // legal in a key path, so ENTITY_PLAYER_LEVELUP lowercases into
        // 'entity_player_levelup' — a perfectly valid key that no sound has. That
        // parses, plays nothing, and reports nothing, which is worse than an error.
        if (!value.equals(value.toLowerCase(Locale.ROOT))) {

            if (value.indexOf('_') >= 0) {
                Actions.warn("Sound '" + value + "' looks like an old Bukkit enum name. "
                        + "Use the namespaced key instead, e.g. 'entity.player.levelup'. "
                        + "Note the two are not interchangeable by lowercasing: "
                        + "BLOCK_NOTE_BLOCK_PLING is 'block.note_block.pling'.");
            } else {
                Actions.warn("Sound '" + value + "' must be lowercase, "
                        + "e.g. 'entity.player.levelup'.");
            }

            return null;
        }

        try {
            return Sound.sound(Key.key(value), Sound.Source.MASTER, 1F, 1F);

        } catch (Exception e) {
            Actions.warn("Sound '" + value + "' is not a valid key: " + e.getMessage()
                    + " Keys look like 'block.note_block.pling'.");
            return null;
        }
    }

    private static float number(final String raw, final float fallback, final String what) {

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return Float.parseFloat(raw.trim());

        } catch (NumberFormatException e) {
            Actions.warn("Sound " + what + " '" + raw + "' is not a number; using " + fallback + ".");
            return fallback;
        }
    }

    private static Sound.Source source(final String raw) {

        if (raw == null || raw.isBlank()) {
            return Sound.Source.MASTER;
        }

        try {
            return Sound.Source.valueOf(raw.trim().toUpperCase(Locale.ROOT));

        } catch (IllegalArgumentException e) {
            Actions.warn("Unknown sound source '" + raw + "'; using master. "
                    + "The source decides which volume slider applies.");
            return Sound.Source.MASTER;
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (sound == null) {
            return Flow.CONTINUE;
        }

        List<Player> players = target.resolve(context);

        if (players.isEmpty()) {
            return Flow.CONTINUE;
        }

        Sound played = Sound.sound(sound.name(), source, volume, pitch);

        for (Player player : players) {
            context.scheduler().runAtEntity(player, task -> player.playSound(played));
        }

        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {

        data.add("target", target.raw());
        data.add("key", configuredKey == null ? "" : configuredKey);
        data.add("volume", String.valueOf(volume));
        data.add("pitch", String.valueOf(pitch));
        data.add("source", source.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public String shorthand() {
        return shorthand;
    }

    /**
     * @return true if the key was readable and the sound will play
     */
    public boolean valid() {
        return sound != null;
    }

    /**
     * @return the key as configured
     */
    public String key() {
        return configuredKey;
    }
}
