package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;

/**
 * Stores a value that later actions in the same run can read as {@code %name%}.
 *
 * <pre>{@code
 * - type: set
 *   key: reward
 *   value: "%vault_eco_balance%"
 * - "[message] self; <gray>Saldo prima: %reward%"
 * }</pre>
 *
 * <p>Useful for holding a value that would otherwise change between two actions — a
 * balance read before a purchase, or a placeholder that is expensive to expand and is
 * needed several times.</p>
 *
 * <p>The value is expanded <strong>without</strong> escaping when it is stored, so it
 * behaves the same as the fixed values a plugin puts in the context: escaping happens
 * later, and only where the value ends up as text.</p>
 *
 * <p>Scope is the run, not the player: a variable set during one join is gone when the
 * list finishes. Two players joining at once each have their own.</p>
 */
public final class SetAction implements Action {

    /** The registered id. */
    public static final String TYPE = "set";

    private final String key;
    private final String value;
    private final String shorthand;

    private SetAction(final String key, final String value, final String shorthand) {
        this.key = key;
        this.value = value;
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
     * Collects the settings of a variable assignment.
     */
    public static final class Builder {

        private String key;
        private String value = "";

        private Builder() {
        }

        /** @param name the variable name @return this builder */
        public Builder key(final String name) {
            this.key = name;
            return this;
        }

        /** @param text the value, which may contain placeholders @return this builder */
        public Builder value(final String text) {
            this.value = text;
            return this;
        }

        /** @return the action */
        public SetAction build() {
            return new SetAction(key, value, null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static SetAction read(final DeserializationData data) {

        String key = data.containsKey("key") ? data.get("key", String.class) : null;

        if (key == null || key.isBlank()) {
            Actions.warn("Set action has no 'key'; it will do nothing.");
        }

        return new SetAction(key, data.containsKey("value") ? data.get("value", String.class) : "", null);
    }

    /**
     * Builds from {@code [set] <key>; <value>}.
     *
     * @param params the text after the tag
     * @param raw the whole line
     * @return the action
     */
    public static SetAction parse(final String params, final String raw) {

        String[] parts = params.split(";", 2);

        if (parts.length < 2) {
            Actions.warn("Set one-liner needs a key and a value: " + raw);
            return new SetAction(null, "", raw);
        }

        return new SetAction(parts[0].trim(), parts[1].trim(), raw);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (key == null || key.isBlank()) {
            return Flow.CONTINUE;
        }

        context.set(key, context.expandRaw(value));

        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {
        data.add("key", key == null ? "" : key);
        data.add("value", value == null ? "" : value);
    }

    @Override
    public String shorthand() {
        return shorthand;
    }

    /**
     * @return the variable name
     */
    public String key() {
        return key;
    }
}
