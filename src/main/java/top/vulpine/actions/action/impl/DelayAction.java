package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;
import top.vulpine.actions.action.Ticks;

/**
 * Waits before the rest of the list continues.
 *
 * <pre>{@code
 * - type: delay
 *   time: 3s
 *
 * - "[delay] 3s"
 * - "[delay] 200"     # no unit: milliseconds, in the inline format
 * }</pre>
 *
 * <p>The action only reports how long to wait. Scheduling and resumption belong to
 * the executor, which is the only thing that knows where in a nested list the run
 * had got to.</p>
 */
public final class DelayAction implements Action {

    /** The registered id. */
    public static final String TYPE = "delay";

    private final long ticks;
    private final String configured;
    private final String shorthand;

    private DelayAction(final long ticks, final String configured, final String shorthand) {
        this.ticks = ticks;
        this.configured = configured;
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
     * Collects the settings of a delay.
     */
    public static final class Builder {

        private String time;

        private Builder() {
        }

        /** @param value how long to wait, e.g. {@code 3s} or {@code 20t} @return this builder */
        public Builder time(final String value) {
            this.time = value;
            return this;
        }

        /** @return the action */
        public DelayAction build() {
            return new DelayAction(Ticks.parse(time, 0L), time, null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static DelayAction read(final DeserializationData data) {

        String configured = data.containsKey("time") ? data.get("time", String.class) : null;

        return new DelayAction(Ticks.parse(configured, 0L), configured, null);
    }

    /**
     * Builds from {@code [delay] <duration>}.
     *
     * @param params the duration
     * @param raw the whole line
     * @return the action
     */
    public static DelayAction parse(final String params, final String raw) {
        return new DelayAction(Ticks.parse(params, 0L), params.trim(), raw);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {
        return Flow.delay(ticks);
    }

    @Override
    public void write(final SerializationData data) {
        data.add("time", configured == null ? ticks + "t" : configured);
    }

    @Override
    public String shorthand() {
        return shorthand;
    }

    /**
     * @return the wait, in ticks
     */
    public long ticks() {
        return ticks;
    }
}
