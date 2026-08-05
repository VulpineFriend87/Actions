package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;
import top.vulpine.actions.target.Target;
import top.vulpine.commons.text.Colorize;

import java.util.List;

/**
 * Sends chat lines.
 *
 * <pre>{@code
 * - type: message
 *   target: all
 *   text:
 *     - "<gray>Line one"
 *     - "<gray>Line two"
 *
 * - "[message] self; <green>Welcome, %player%"
 * }</pre>
 */
public final class MessageAction implements Action {

    /** The registered id. */
    public static final String TYPE = "message";

    private final Target target;
    private final List<String> lines;
    private final String shorthand;

    private MessageAction(final Target target, final List<String> lines, final String shorthand) {
        this.target = target;
        this.lines = List.copyOf(lines);
        this.shorthand = shorthand;
    }

    /**
     * Starts building one in code, for a config default.
     *
     * <p>What it builds reports no {@link #shorthand()}, so it is written to the file
     * as a block — which is what a default should be, given the one-liner form is on
     * its way out.</p>
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the settings of a chat message.
     */
    public static final class Builder {

        private Target target = Target.SELF;
        private List<String> lines = List.of();

        private Builder() {
        }

        /** @param value who receives it @return this builder */
        public Builder target(final Target value) {
            this.target = value == null ? Target.SELF : value;
            return this;
        }

        /** @param value the chat lines @return this builder */
        public Builder text(final String... value) {
            this.lines = List.of(value);
            return this;
        }

        /** @return the action */
        public MessageAction build() {
            return new MessageAction(target, lines, null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static MessageAction read(final DeserializationData data) {

        Target target = Target.parse(data.containsKey("target") ? data.get("target", String.class) : null);

        return new MessageAction(target, lines(data), null);
    }

    /**
     * Builds from {@code [message] <target>; <text>}.
     *
     * @param params the text after the tag
     * @param raw the whole line
     * @return the action
     */
    public static MessageAction parse(final String params, final String raw) {

        String[] parts = params.split(";", 2);

        if (parts.length < 2) {
            // No target: SELF
            return new MessageAction(Target.SELF, List.of(params.trim()), raw);
        }

        return new MessageAction(Target.parse(parts[0]), List.of(parts[1].trim()), raw);
    }

    private static List<String> lines(final DeserializationData data) {

        if (!data.containsKey("text")) {
            return List.of();
        }

        // Accepts both strings and lists
        return data.getRaw("text") instanceof List
                ? List.copyOf(data.getAsList("text", String.class))
                : List.of(data.get("text", String.class));
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (lines.isEmpty()) {
            return Flow.CONTINUE;
        }

        List<Player> players = target.resolve(context);

        if (players.isEmpty()) {
            return Flow.CONTINUE;
        }

        for (String line : lines) {

            Component rendered = Colorize.color(context.expand(line));

            for (Player player : players) {
                context.scheduler().runAtEntity(player, task -> player.sendMessage(rendered));
            }
        }

        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {

        data.add("target", target.raw());

        if (lines.size() == 1) {
            data.add("text", lines.get(0));
        } else {
            data.addCollection("text", lines, String.class);
        }
    }

    @Override
    public String shorthand() {
        return shorthand;
    }

    /**
     * @return the lines, unexpanded
     */
    public List<String> lines() {
        return lines;
    }

    /**
     * @return who this is aimed at
     */
    public Target target() {
        return target;
    }
}
