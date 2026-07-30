package top.vulpine.actions.action;

import top.vulpine.actions.Actions;
import top.vulpine.actions.scheduler.Cancellable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Runs a list of actions in order, keeping its place across nesting and delays.
 *
 * <h2>Why a stack rather than an index</h2>
 * <p>The obvious design remembers "I was at action 4" and resumes at 5. That works
 * for a flat list and breaks the moment lists nest: after
 * {@code if → then → delay}, no single number describes "action 2 of the then-block
 * of action 5". The position is a path, so the executor keeps one frame per open
 * list and a cursor in each. Resuming after a delay restores the whole path, and
 * {@link Flow#STOP} clearing the stack means stop really does mean stop, not "stop
 * this branch".</p>
 *
 * <p>Not thread safe: a run belongs to whichever region or thread the scheduler
 * put it on, and resumes there. {@link #cancel()} is the exception and may be
 * called from anywhere.</p>
 */
public final class ActionExecutor {

    /**
     * How deep nesting may go before the run is abandoned. Guards against a named
     * sequence that invokes itself, directly or through a cycle, which would
     * otherwise grow the stack until the JVM gives up.
     */
    private static final int MAX_DEPTH = 32;

    private final Deque<Frame> stack = new ArrayDeque<>();
    private final ActionContext context;

    private volatile boolean cancelled;
    private Cancellable pending = Cancellable.NOOP;

    /**
     * @param actions the list to run
     * @param context the run's context
     */
    public ActionExecutor(final List<Action> actions, final ActionContext context) {

        this.context = Objects.requireNonNull(context, "context");

        if (actions != null && !actions.isEmpty()) {
            stack.push(new Frame(actions));
        }
    }

    /**
     * Runs a list to completion, or until it delays or stops.
     *
     * @param actions the list to run
     * @param context the run's context
     * @return the executor, so a delayed run can still be cancelled
     */
    public static ActionExecutor run(final List<Action> actions, final ActionContext context) {

        ActionExecutor executor = new ActionExecutor(actions, context);
        executor.run();
        return executor;
    }

    /**
     * Abandons the run. Safe to call at any time, from any thread, more than once —
     * intended for a player quitting while a delayed list is still pending.
     */
    public void cancel() {
        cancelled = true;
        pending.cancel();
        pending = Cancellable.NOOP;
    }

    /**
     * @return true once the run has finished or been cancelled
     */
    public boolean finished() {
        return cancelled || stack.isEmpty();
    }

    /**
     * Executes until the list is exhausted, or an action stops or delays it.
     */
    public void run() {

        while (!cancelled && !stack.isEmpty()) {

            Frame frame = stack.peek();

            if (!frame.hasNext()) {
                stack.pop();
                continue;
            }

            Flow flow = execute(frame.next());

            switch (flow.kind()) {

                case CONTINUE -> {
                }

                case STOP -> {
                    stack.clear();
                    return;
                }

                case ENTER -> {

                    if (stack.size() >= MAX_DEPTH) {
                        Actions.warn("Action nesting went deeper than " + MAX_DEPTH
                                + "; check for a sequence that runs itself. Run abandoned.");
                        stack.clear();
                        return;
                    }

                    stack.push(new Frame(flow.branch()));
                }

                case DELAY -> {
                    schedule(flow.ticks());
                    return;
                }
            }
        }
    }

    private Flow execute(final Action action) {

        try {
            return action.execute(context);

        } catch (Throwable thrown) {
            // Throwable, not Exception, on purpose. A jar compiled against an older
            // API can throw a LinkageError from a single action — that is an Error,
            // so catching Exception would let one misconfigured line abandon every
            // action after it. One action failing should cost one action.
            Actions.warn("Action '" + action.type() + "' failed: " + thrown);
            return Flow.CONTINUE;
        }
    }

    private void schedule(final long ticks) {

        pending = context.scheduler().runLater(context.player(), () -> {

            pending = Cancellable.NOOP;

            // A player can log out during the wait. Their region is gone and most
            // actions would fail one by one, so drop the rest of the list quietly.
            if (cancelled || (context.player() != null && !context.player().isOnline())) {
                stack.clear();
                return;
            }

            run();

        }, ticks);
    }

    /**
     * One open list and how far into it the run has got.
     */
    private static final class Frame {

        private final List<Action> actions;
        private int cursor;

        private Frame(final List<Action> actions) {
            this.actions = actions;
        }

        private boolean hasNext() {
            return cursor < actions.size();
        }

        private Action next() {
            return actions.get(cursor++);
        }
    }
}
