package top.vulpine.actions;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A stand-in for FoliaLib's scheduler that runs immediate work inline and holds
 * delayed work until the test releases it, so a delay can be observed as a pause
 * rather than waited on. It also records which method was asked for, which is how
 * the entity-bound and global routes are told apart.
 *
 * <p>{@link PlatformScheduler} has around forty methods, so it is stood in for with a
 * dynamic proxy rather than forty empty overrides that would need updating every time
 * FoliaLib grows one. Only the four methods this library calls are handled; anything
 * else returns null, which is loud enough if something starts calling it.</p>
 */
class FakeScheduler {

    private final Deque<Runnable> pending = new ArrayDeque<>();
    private final List<String> calls = new ArrayList<>();

    private boolean returnNullTasks;

    @SuppressWarnings("unchecked")
    private final PlatformScheduler platform = (PlatformScheduler) Proxy.newProxyInstance(
            PlatformScheduler.class.getClassLoader(),
            new Class<?>[]{PlatformScheduler.class},
            (instance, method, args) -> {

                calls.add(method.getName());

                switch (method.getName()) {

                    case "runAtEntity", "runNextTick" -> {
                        // These take a Consumer<WrappedTask>, not a Runnable — the
                        // detail most likely to be got wrong from memory.
                        ((Consumer<WrappedTask>) args[args.length - 1]).accept(new Task(() -> {
                        }));
                        return CompletableFuture.completedFuture(null);
                    }

                    case "runAtEntityLater", "runLater" -> {

                        Runnable task = null;

                        for (Object argument : args) {
                            if (argument instanceof Runnable runnable) {
                                task = runnable;
                            }
                        }

                        if (returnNullTasks) {
                            return null;
                        }

                        pending.add(task);

                        Runnable queued = task;
                        return new Task(() -> pending.remove(queued));
                    }

                    default -> {
                        return null;
                    }
                }
            });

    /** @return the scheduler to hand to {@code ActionContext.builder} */
    public PlatformScheduler platform() {
        return platform;
    }

    /**
     * Makes delayed scheduling hand back null, as FoliaLib does when the entity is
     * already gone.
     */
    public void returnNullTasks() {
        returnNullTasks = true;
    }

    /** @return the FoliaLib methods called so far, in order */
    public List<String> calls() {
        return calls;
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

    /** The handle FoliaLib hands back for scheduled work. */
    private static final class Task implements WrappedTask {

        private final Runnable removal;
        private boolean cancelled;

        private Task(final Runnable removal) {
            this.removal = removal;
        }

        @Override
        public void cancel() {
            cancelled = true;
            removal.run();
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public Plugin getOwningPlugin() {
            return null;
        }

        @Override
        public boolean isAsync() {
            return false;
        }
    }
}
