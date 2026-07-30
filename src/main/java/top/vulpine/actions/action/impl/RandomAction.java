package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Choice;
import top.vulpine.actions.action.Flow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs exactly one of several alternatives, chosen by weight.
 *
 * <pre>{@code
 * - type: random
 *   options:
 *     - weight: 3
 *       actions:
 *         - "[message] self; <gray>Suggerimento: /spawn"
 *     - weight: 1
 *       actions:
 *         - "[message] self; <gray>Suggerimento: /kit"
 * }</pre>
 *
 * <p>Weights are relative, not percentages: {@code 3} and {@code 1} mean three times
 * out of four. Omitting {@code weight} means 1, so a list of equal alternatives needs
 * no weights at all.</p>
 */
public final class RandomAction implements Action {

    /** The registered id. */
    public static final String TYPE = "random";

    private final List<Choice> options;
    private final int total;

    private RandomAction(final List<Choice> options) {

        this.options = List.copyOf(options);

        int sum = 0;

        for (Choice option : options) {
            sum += option.weight();
        }

        this.total = sum;
    }

    /**
     * Starts building one in code, for a config default.
     *
     * <pre>{@code
     * RandomAction.builder()
     *         .choice(3, MessageAction.builder().text("comune").build())
     *         .choice(1, MessageAction.builder().text("raro").build())
     *         .build()
     * }</pre>
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the alternatives of a random pick.
     */
    public static final class Builder {

        private final List<Choice> options = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds a weighted alternative.
         *
         * @param weight how likely, relative to the others
         * @param actions what to run if picked
         * @return this builder
         */
        public Builder choice(final int weight, final Action... actions) {
            options.add(new Choice(weight, List.of(actions)));
            return this;
        }

        /**
         * Adds an alternative of weight 1.
         *
         * @param actions what to run if picked
         * @return this builder
         */
        public Builder choice(final Action... actions) {
            return choice(1, actions);
        }

        /** @return the action */
        public RandomAction build() {
            return new RandomAction(options);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static RandomAction read(final DeserializationData data) {

        List<Choice> options = data.containsKey("options")
                ? List.copyOf(data.getAsList("options", Choice.class))
                : List.of();

        if (options.isEmpty()) {
            Actions.warn("Random action has no 'options'; it will do nothing.");
        }

        return new RandomAction(options);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (options.isEmpty()) {
            return Flow.CONTINUE;
        }

        int roll = ThreadLocalRandom.current().nextInt(total);

        for (Choice option : options) {

            roll -= option.weight();

            if (roll < 0) {
                return Flow.enter(option.actions());
            }
        }

        // Unreachable while the weights sum to total, but returning the last option
        // beats returning nothing if that ever stops being true.
        return Flow.enter(options.get(options.size() - 1).actions());
    }

    @Override
    public void write(final SerializationData data) {
        data.addCollection("options", options, Choice.class);
    }

    /**
     * @return the alternatives
     */
    public List<Choice> options() {
        return options;
    }
}
