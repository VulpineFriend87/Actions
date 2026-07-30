package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;
import top.vulpine.actions.target.Target;

import java.util.List;
import java.util.Locale;

/**
 * Runs a command, as the console or as the player.
 *
 * <pre>{@code
 * - type: command
 *   as: console
 *   command: "give %player% diamond 1"
 *
 * - type: command
 *   as: player
 *   target: all          # each of them runs it
 *   command: "spawn"
 *
 * - "[command] console; give %player% diamond 1"
 * }</pre>
 *
 * <p>{@code as} is who runs it, which is a different question from {@code target} —
 * the latter only matters for {@code as: player}, where the command runs once per
 * player. Console commands run once regardless.</p>
 *
 * <p>The command is expanded <strong>without</strong> escaping, since it is not going
 * through MiniMessage. Placeholder values therefore reach the command as typed: worth
 * remembering if one of them can be set by a player, such as a nickname from
 * PlaceholderAPI.</p>
 */
public final class CommandAction implements Action {

    /** The registered id. */
    public static final String TYPE = "command";

    private final boolean asConsole;
    private final Target target;
    private final String command;
    private final String shorthand;

    private CommandAction(final boolean asConsole, final Target target,
                          final String command, final String shorthand) {

        this.asConsole = asConsole;
        this.target = target;
        this.command = command == null ? "" : command;
        this.shorthand = shorthand;
    }

    /**
     * Starts building one in code, for a config default. Runs as the console unless
     * {@link Builder#asPlayer(Target)} says otherwise.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the settings of a command.
     */
    public static final class Builder {

        private boolean asConsole = true;
        private Target target = Target.SELF;
        private String command;

        private Builder() {
        }

        /**
         * Runs it once, as the console. The default.
         *
         * @return this builder
         */
        public Builder asConsole() {
            this.asConsole = true;
            return this;
        }

        /**
         * Runs it once per targeted player, as that player.
         *
         * @param value who runs it
         * @return this builder
         */
        public Builder asPlayer(final Target value) {
            this.asConsole = false;
            this.target = value == null ? Target.SELF : value;
            return this;
        }

        /** @param value the command, without a leading slash @return this builder */
        public Builder command(final String value) {
            this.command = value;
            return this;
        }

        /** @return the action */
        public CommandAction build() {
            return new CommandAction(asConsole, target, command, null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static CommandAction read(final DeserializationData data) {

        String as = data.containsKey("as") ? data.get("as", String.class) : "console";

        return new CommandAction(
                parseAs(as),
                Target.parse(data.containsKey("target") ? data.get("target", String.class) : null),
                data.containsKey("command") ? data.get("command", String.class) : null,
                null);
    }

    /**
     * Builds from {@code [command] <console/player>; <command>}.
     *
     * @param params the text after the tag
     * @param raw the whole line
     * @return the action
     */
    public static CommandAction parse(final String params, final String raw) {

        String[] parts = params.split(";", 2);

        if (parts.length < 2) {
            Actions.warn("Command one-liner needs 'console' or 'player' first: " + raw);
            return new CommandAction(true, Target.SELF, null, raw);
        }

        return new CommandAction(parseAs(parts[0]), Target.SELF, parts[1].trim(), raw);
    }

    private static boolean parseAs(final String as) {

        if (as == null) {
            return true;
        }

        String value = as.trim().toLowerCase(Locale.ROOT);

        if (value.equals("player")) {
            return false;
        }

        if (!value.equals("console")) {
            Actions.warn("Command 'as: " + as + "' is not 'console' or 'player'; using console.");
        }

        return true;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (command.isBlank()) {
            return Flow.CONTINUE;
        }

        // No escaping: this is a command line, not markup.
        String expanded = context.expandRaw(command);

        // A leading slash is easy to type and Bukkit does not want it.
        String line = expanded.startsWith("/") ? expanded.substring(1) : expanded;

        if (asConsole) {
            context.scheduler().run(null, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
            return Flow.CONTINUE;
        }

        for (Player player : target.resolve(context)) {
            context.scheduler().run(player, () -> player.performCommand(line));
        }

        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {

        data.add("as", asConsole ? "console" : "player");

        if (!asConsole) {
            data.add("target", target.raw());
        }

        data.add("command", command);
    }

    @Override
    public String shorthand() {
        return shorthand;
    }

    /**
     * @return true if it runs as the console
     */
    public boolean asConsole() {
        return asConsole;
    }

    /**
     * @return the command, unexpanded and without a leading slash removed
     */
    public String command() {
        return command;
    }
}
