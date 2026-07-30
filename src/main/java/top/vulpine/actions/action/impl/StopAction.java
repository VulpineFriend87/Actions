package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.SerializationData;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;

/**
 * Abandons the rest of the run, including any enclosing lists.
 *
 * <pre>{@code
 * - type: if
 *   condition: "permission: lobby.bypass"
 *   then:
 *     - type: stop
 *
 * - "[stop]"
 * }</pre>
 *
 * <p>Stop means stop: nested inside a {@code then} branch it still ends everything,
 * not just the branch. That is what makes it useful as an early exit — the alternative
 * would be wrapping the whole remainder of the list in an {@code else}.</p>
 */
public final class StopAction implements Action {

    /** The registered id. */
    public static final String TYPE = "stop";

    private final String shorthand;

    private StopAction(final String shorthand) {
        this.shorthand = shorthand;
    }

    /**
     * Starts building one in code, for a config default.
     *
     * <p>There is nothing to configure, so {@code builder().build()} is all there is.
     * It exists anyway so every action is created the same way — one pattern to learn
     * across fourteen actions is worth a redundant call on the one that takes no
     * settings.</p>
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a stop, which has no settings.
     */
    public static final class Builder {

        private Builder() {
        }

        /** @return the action */
        public StopAction build() {
            return new StopAction(null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data ignored; the action has no settings
     * @return the action
     */
    public static StopAction read(final eu.okaeri.configs.serdes.DeserializationData data) {
        return new StopAction(null);
    }

    /**
     * Builds from {@code [stop]}.
     *
     * @param params ignored
     * @param raw the whole line
     * @return the action
     */
    public static StopAction parse(final String params, final String raw) {
        return new StopAction(raw);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {
        return Flow.STOP;
    }

    @Override
    public void write(final SerializationData data) {
        // Nothing to write; the type alone says everything.
    }

    @Override
    public String shorthand() {
        return shorthand;
    }
}
