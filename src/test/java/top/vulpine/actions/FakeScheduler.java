package top.vulpine.actions;

import org.bukkit.entity.Entity;
import top.vulpine.actions.scheduler.ActionScheduler;
import top.vulpine.actions.scheduler.Cancellable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A scheduler that runs immediate work inline and holds delayed work until the test
 * releases it, so a delay can be observed as a pause rather than waited on.
 */
class FakeScheduler implements ActionScheduler {

    private final Deque<Runnable> pending = new ArrayDeque<>();

    @Override
    public void run(final Entity entity, final Runnable task) {
        task.run();
    }

    @Override
    public Cancellable runLater(final Entity entity, final Runnable task, final long ticks) {
        pending.add(task);
        return () -> pending.remove(task);
    }

    /** @return true if work was waiting and has now run */
    public boolean tick() {

        Runnable task = pending.poll();

        if (task == null) {
            return false;
        }

        task.run();
        return true;
    }

    /** @return how many delayed tasks are waiting */
    public int pending() {
        return pending.size();
    }
}
