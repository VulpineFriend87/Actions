package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;
import top.vulpine.actions.action.SequenceRegistry;

import java.util.List;

/**
 * Runs a named sequence, so a list written once can be invoked from several places.
 *
 * <pre>{@code
 * - type: run
 *   sequence: vip_welcome
 *
 * - "[run] vip_welcome"
 * }</pre>
 *
 * <p>The sequence is looked up when the action runs, not when the config loads, so it
 * may be defined later in the file or in a different one. A sequence that runs itself
 * is caught by the executor's nesting limit rather than growing the stack until the
 * JVM gives up.</p>
 */
public final class RunAction implements Action {

    /** The registered id. */
    public static final String TYPE = "run";

    private final String sequence;
    private final String shorthand;

    private RunAction(final String sequence, final String shorthand) {
        this.sequence = sequence;
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
     * Collects the settings of a sequence call.
     */
    public static final class Builder {

        private String sequence;

        private Builder() {
        }

        /** @param name the sequence name @return this builder */
        public Builder sequence(final String name) {
            this.sequence = name;
            return this;
        }

        /** @return the action */
        public RunAction build() {
            return new RunAction(sequence, null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static RunAction read(final DeserializationData data) {

        String sequence = data.containsKey("sequence") ? data.get("sequence", String.class) : null;

        if (sequence == null || sequence.isBlank()) {
            Actions.warn("Run action has no 'sequence'; it will do nothing.");
        }

        return new RunAction(sequence, null);
    }

    /**
     * Builds from {@code [run] <sequence>}.
     *
     * @param params the sequence name
     * @param raw the whole line
     * @return the action
     */
    public static RunAction parse(final String params, final String raw) {
        return new RunAction(params.trim(), raw);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (sequence == null || sequence.isBlank()) {
            return Flow.CONTINUE;
        }

        SequenceRegistry registry = context.sequences();

        if (registry == null) {
            Actions.warnOnce("Action 'run' needs a sequence registry, but this run has none. "
                    + "Pass one with ActionContext.builder(...).sequences(registry).");
            return Flow.CONTINUE;
        }

        List<Action> actions = registry.get(sequence);

        if (actions == null) {
            Actions.warnOnce("Unknown sequence '" + sequence + "'. Known: " + registry.names());
            return Flow.CONTINUE;
        }

        return Flow.enter(actions);
    }

    @Override
    public void write(final SerializationData data) {
        data.add("sequence", sequence == null ? "" : sequence);
    }

    @Override
    public String shorthand() {
        return shorthand;
    }

    /**
     * @return the sequence name
     */
    public String sequence() {
        return sequence;
    }
}
