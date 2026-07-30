package top.vulpine.actions.scheduler;

/**
 * A handle on scheduled work that has not run yet.
 */
@FunctionalInterface
public interface Cancellable {

    /** A handle for work that cannot or need not be cancelled. */
    Cancellable NOOP = () -> {
    };

    /**
     * Cancels the task if it has not started. Calling this more than once, or after
     * the task has run, must be harmless.
     */
    void cancel();
}
