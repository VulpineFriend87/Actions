package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a list several times over.
 *
 * <pre>{@code
 * - type: repeat
 *   times: 3
 *   actions:
 *     - "[sound] self; block.note_block.pling"
 *     - "[delay] 10t"
 * }</pre>
 *
 * <p>To space the repetitions out, put a {@code delay} inside the list — the executor
 * keeps its place across the pause, so the remaining repetitions carry on afterwards.</p>
 */
public final class RepeatAction implements Action {

    /** The registered id. */
    public static final String TYPE = "repeat";

    /**
     * Repetitions are flattened into one list, so this caps how large that gets. High
     * enough for anything an operator means, low enough that a stray {@code times:
     * 1000000} is a warning instead of an out-of-memory error.
     */
    private static final int MAX_TIMES = 1000;

    private final int times;
    private final List<Action> actions;
    private final List<Action> flattened;

    private RepeatAction(final int times, final List<Action> actions) {

        this.times = times;
        this.actions = List.copyOf(actions);

        List<Action> expanded = new ArrayList<>(actions.size() * Math.max(0, times));

        for (int pass = 0; pass < times; pass++) {
            expanded.addAll(this.actions);
        }

        // Built once at load. The entries are references to the same action objects,
        // so repeating a five-action list ten times costs fifty pointers, not fifty
        // actions — and the executor needs no special frame type.
        this.flattened = List.copyOf(expanded);
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
     * Collects the settings of a repetition.
     */
    public static final class Builder {

        private int times = 1;
        private List<Action> actions = List.of();

        private Builder() {
        }

        /** @param value how many passes @return this builder */
        public Builder times(final int value) {
            this.times = Math.min(Math.max(0, value), MAX_TIMES);
            return this;
        }

        /** @param value the actions of one pass @return this builder */
        public Builder actions(final Action... value) {
            this.actions = List.of(value);
            return this;
        }

        /** @return the action */
        public RepeatAction build() {
            return new RepeatAction(times, actions);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static RepeatAction read(final DeserializationData data) {

        int times = 1;

        if (data.containsKey("times")) {

            try {
                times = Integer.parseInt(String.valueOf(data.getRaw("times")).trim());

            } catch (NumberFormatException e) {
                Actions.warn("Repeat 'times' is not a whole number: " + data.getRaw("times"));
                times = 0;
            }
        }

        if (times > MAX_TIMES) {
            Actions.warn("Repeat 'times' of " + times + " is above the limit of "
                    + MAX_TIMES + "; using " + MAX_TIMES + ".");
            times = MAX_TIMES;
        }

        if (times < 0) {
            times = 0;
        }

        List<Action> actions = data.containsKey("actions")
                ? List.copyOf(data.getAsList("actions", Action.class))
                : List.of();

        if (actions.isEmpty()) {
            Actions.warn("Repeat action has no 'actions'; it will do nothing.");
        }

        return new RepeatAction(times, actions);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {
        return Flow.enter(flattened);
    }

    @Override
    public void write(final SerializationData data) {
        data.add("times", times);
        data.addCollection("actions", actions, Action.class);
    }

    /**
     * @return how many passes
     */
    public int times() {
        return times;
    }

    /**
     * @return the actions of one pass
     */
    public List<Action> actions() {
        return actions;
    }
}
