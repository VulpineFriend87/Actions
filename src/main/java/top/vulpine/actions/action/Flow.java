package top.vulpine.actions.action;

import java.util.List;
import java.util.Objects;

/**
 * What an {@link Action} tells the executor to do next.
 *
 * <p>Control flow travels in the return value rather than through a reference to
 * the executor. An action that branches returns {@link #enter(List)} and an action
 * that waits returns {@link #delay(long)}; neither needs to know a stack or a
 * scheduler exists. That keeps every action a pure function of its context and
 * makes them testable without a server.</p>
 */
public final class Flow {

    /** Which of the four outcomes this is. */
    public enum Kind {

        /** Move on to the next action in the current list. */
        CONTINUE,

        /** Abandon everything, including enclosing lists. */
        STOP,

        /** Run a nested list, then carry on after this action. */
        ENTER,

        /** Pause, and resume at exactly this position later. */
        DELAY
    }

    /** Move on to the next action. */
    public static final Flow CONTINUE = new Flow(Kind.CONTINUE, List.of(), 0L);

    /** Abandon the whole run. */
    public static final Flow STOP = new Flow(Kind.STOP, List.of(), 0L);

    private final Kind kind;
    private final List<Action> branch;
    private final long ticks;

    private Flow(final Kind kind, final List<Action> branch, final long ticks) {
        this.kind = kind;
        this.branch = branch;
        this.ticks = ticks;
    }

    /**
     * Runs a nested list before continuing after the current action.
     *
     * <p>An empty or null list is the same as {@link #CONTINUE}, which lets
     * conditional actions return the untaken branch without a special case.</p>
     *
     * @param actions the nested list
     * @return the flow
     */
    public static Flow enter(final List<Action> actions) {

        if (actions == null || actions.isEmpty()) {
            return CONTINUE;
        }

        return new Flow(Kind.ENTER, List.copyOf(actions), 0L);
    }

    /**
     * Pauses execution, resuming at the next action after the given delay.
     *
     * <p>Zero or negative is {@link #CONTINUE}, so a configured delay of {@code 0}
     * does not cost a scheduler round trip.</p>
     *
     * @param ticks how long to wait
     * @return the flow
     */
    public static Flow delay(final long ticks) {

        if (ticks <= 0L) {
            return CONTINUE;
        }

        return new Flow(Kind.DELAY, List.of(), ticks);
    }

    /**
     * @return which outcome this is
     */
    public Kind kind() {
        return kind;
    }

    /**
     * @return the nested list for {@link Kind#ENTER}, otherwise empty
     */
    public List<Action> branch() {
        return branch;
    }

    /**
     * @return the wait for {@link Kind#DELAY}, otherwise zero
     */
    public long ticks() {
        return ticks;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case ENTER -> "Flow[ENTER " + branch.size() + " action(s)]";
            case DELAY -> "Flow[DELAY " + ticks + " ticks]";
            default -> "Flow[" + kind + "]";
        };
    }

    @Override
    public boolean equals(final Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof Flow flow)) {
            return false;
        }

        return kind == flow.kind && ticks == flow.ticks && branch.equals(flow.branch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, branch, ticks);
    }
}
