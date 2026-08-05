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
 * Sends text to the action bar, above the hotbar.
 *
 * <pre>{@code
 * - type: actionbar
 *   target: self
 *   text: "<yellow>Teletrasporto in %time%s"
 *
 * - "[actionbar] self; <yellow>Teletrasporto"
 * }</pre>
 *
 * <p>Sent through Adventure, so no bungeecord-chat dependency is involved.</p>
 */
public final class ActionBarAction implements Action {

    /** The registered id. */
    public static final String TYPE = "actionbar";

    private final Target target;
    private final String text;
    private final String shorthand;

    private ActionBarAction(final Target target, final String text, final String shorthand) {
        this.target = target;
        this.text = text == null ? "" : text;
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
     * Collects the settings of an action bar message.
     */
    public static final class Builder {

        private Target target = Target.SELF;
        private String text = "";

        private Builder() {
        }

        /** @param value who sees it @return this builder */
        public Builder target(final Target value) {
            this.target = value == null ? Target.SELF : value;
            return this;
        }

        /** @param value the text @return this builder */
        public Builder text(final String value) {
            this.text = value;
            return this;
        }

        /** @return the action */
        public ActionBarAction build() {
            return new ActionBarAction(target, text, null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static ActionBarAction read(final DeserializationData data) {

        return new ActionBarAction(
                Target.parse(data.containsKey("target") ? data.get("target", String.class) : null),
                data.containsKey("text") ? data.get("text", String.class) : null,
                null);
    }

    /**
     * Builds from {@code [actionbar] <target>; <text>}.
     *
     * @param params the text after the tag
     * @param raw the whole line
     * @return the action
     */
    public static ActionBarAction parse(final String params, final String raw) {

        String[] parts = params.split(";", 2);

        if (parts.length < 2) {
            return new ActionBarAction(Target.SELF, params.trim(), raw);
        }

        return new ActionBarAction(Target.parse(parts[0]), parts[1].trim(), raw);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (text.isBlank()) {
            return Flow.CONTINUE;
        }

        List<Player> players = target.resolve(context);

        if (players.isEmpty()) {
            return Flow.CONTINUE;
        }

        Component rendered = Colorize.color(context.expand(text));

        for (Player player : players) {
            context.scheduler().runAtEntity(player, task -> player.sendActionBar(rendered));
        }

        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {
        data.add("target", target.raw());
        data.add("text", text);
    }

    @Override
    public String shorthand() {
        return shorthand;
    }

    /**
     * @return the text, unexpanded
     */
    public String text() {
        return text;
    }
}
