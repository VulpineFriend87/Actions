package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;
import top.vulpine.actions.target.Target;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Changes the game mode.
 *
 * <pre>{@code
 * - type: gamemode
 *   target: self
 *   mode: adventure
 *
 * - "[gamemode] self; adventure"
 * }</pre>
 *
 * <p>{@code GameMode} is a plain enum and has stayed one, unlike {@code Sound}, so
 * {@code valueOf} is safe here. Verified against both 1.18.2 and current Paper rather
 * than assumed — the same assumption is what broke the sound action.</p>
 */
public final class GamemodeAction implements Action {

    /** The registered id. */
    public static final String TYPE = "gamemode";

    private final Target target;
    private final GameMode mode;
    private final String shorthand;

    private GamemodeAction(final Target target, final GameMode mode, final String shorthand) {
        this.target = target;
        this.mode = mode;
        this.shorthand = shorthand;
    }

    /**
     * Starts building one in code, for a config default.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the settings of a game mode change.
     */
    public static final class Builder {

        private Target target = Target.SELF;
        private GameMode mode;

        private Builder() {
        }

        /** @param value whose game mode changes @return this builder */
        public Builder target(final Target value) {
            this.target = value == null ? Target.SELF : value;
            return this;
        }

        /** @param value the mode @return this builder */
        public Builder mode(final GameMode value) {
            this.mode = value;
            return this;
        }

        /** @return the action */
        public GamemodeAction build() {
            return new GamemodeAction(target, mode, null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static GamemodeAction read(final DeserializationData data) {

        return new GamemodeAction(
                Target.parse(data.containsKey("target") ? data.get("target", String.class) : null),
                mode(data.containsKey("mode") ? data.get("mode", String.class) : null),
                null);
    }

    /**
     * Builds from {@code [gamemode] <target>; <mode>}.
     *
     * @param params the text after the tag
     * @param raw the whole line
     * @return the action
     */
    public static GamemodeAction parse(final String params, final String raw) {

        String[] parts = params.split(";", 2);

        if (parts.length < 2) {
            Actions.warn("Gamemode one-liner needs a target and a mode: " + raw);
            return new GamemodeAction(Target.SELF, null, raw);
        }

        return new GamemodeAction(Target.parse(parts[0]), mode(parts[1]), raw);
    }

    private static GameMode mode(final String raw) {

        if (raw == null || raw.isBlank()) {
            Actions.warn("Gamemode action has no 'mode'. Valid: " + names());
            return null;
        }

        try {
            return GameMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));

        } catch (IllegalArgumentException e) {
            Actions.warn("Unknown gamemode '" + raw.trim() + "'. Valid: " + names());
            return null;
        }
    }

    private static String names() {
        return Arrays.stream(GameMode.values())
                .map(value -> value.name().toLowerCase(Locale.ROOT))
                .toList()
                .toString();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (mode == null) {
            return Flow.CONTINUE;
        }

        List<Player> players = target.resolve(context);

        for (Player player : players) {
            context.scheduler().run(player, () -> player.setGameMode(mode));
        }

        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {
        data.add("target", target.raw());
        data.add("mode", mode == null ? "" : mode.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public String shorthand() {
        return shorthand;
    }

    /**
     * @return the mode, or null if it could not be read
     */
    public GameMode mode() {
        return mode;
    }
}
