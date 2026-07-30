package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;
import top.vulpine.actions.condition.Condition;

import java.util.List;

/**
 * Runs one of two lists depending on a condition.
 *
 * <pre>{@code
 * - type: if
 *   condition:
 *     any:
 *       - "permission: lobby.vip"
 *       - "%vault_eco_balance% >= 10000"
 *     not: "world: arena"
 *   then:
 *     - "[message] self; <gold>Perk attivi"
 *   else:
 *     - type: message
 *       target: self
 *       text: "<gray>Nessun perk"
 * }</pre>
 *
 * <p>The action itself does no branching: it returns {@link Flow#enter(List)} with
 * whichever list won, and the executor pushes it. A missing branch is an empty list,
 * which {@code enter} already treats as "carry on", so there is no special case for
 * an {@code if} without an {@code else}.</p>
 */
public final class IfAction implements Action {

    /** The registered id. */
    public static final String TYPE = "if";

    private final Condition condition;
    private final List<Action> then;
    private final List<Action> otherwise;

    private IfAction(final Condition condition, final List<Action> then, final List<Action> otherwise) {
        this.condition = condition;
        this.then = List.copyOf(then);
        this.otherwise = List.copyOf(otherwise);
    }

    /**
     * Starts building one in code, for a config default.
     *
     * <pre>{@code
     * IfAction.builder()
     *         .condition("permission: lobby.vip")
     *         .then(MessageAction.builder().text("<gold>Perk attivi").build())
     *         .otherwise(MessageAction.builder().text("<gray>Nessun perk").build())
     *         .build()
     * }</pre>
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the branches of a conditional.
     */
    public static final class Builder {

        private Condition condition = Condition.ALWAYS;
        private List<Action> then = List.of();
        private List<Action> otherwise = List.of();

        private Builder() {
        }

        /**
         * @param value an expression, or a Map/List of {@code all}/{@code any}/{@code not}
         * @return this builder
         */
        public Builder condition(final Object value) {
            this.condition = Condition.parse(value);
            return this;
        }

        /**
         * @param actions what to run when the condition holds
         * @return this builder
         */
        public Builder then(final Action... actions) {
            this.then = List.of(actions);
            return this;
        }

        /**
         * @param actions what to run when it does not
         * @return this builder
         */
        public Builder otherwise(final Action... actions) {
            this.otherwise = List.of(actions);
            return this;
        }

        /**
         * @return the action
         */
        public IfAction build() {
            return new IfAction(condition, then, otherwise);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static IfAction read(final DeserializationData data) {

        Condition condition = Condition.parse(data.containsKey("condition")
                ? data.getRaw("condition") : null);

        return new IfAction(condition, branch(data, "then"), branch(data, "else"));
    }

    private static List<Action> branch(final DeserializationData data, final String key) {
        return data.containsKey(key)
                ? List.copyOf(data.getAsList(key, Action.class))
                : List.of();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {
        return Flow.enter(condition.test(context) ? then : otherwise);
    }

    @Override
    public void write(final SerializationData data) {

        if (condition.source() != null) {
            data.addRaw("condition", condition.source());
        }

        if (!then.isEmpty()) {
            data.addCollection("then", then, Action.class);
        }

        if (!otherwise.isEmpty()) {
            data.addCollection("else", otherwise, Action.class);
        }
    }

    /**
     * @return the actions run when the condition holds
     */
    public List<Action> then() {
        return then;
    }

    /**
     * @return the actions run when it does not
     */
    public List<Action> otherwise() {
        return otherwise;
    }
}
