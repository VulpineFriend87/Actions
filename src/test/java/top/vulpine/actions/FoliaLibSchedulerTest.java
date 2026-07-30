package top.vulpine.actions;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.vulpine.actions.scheduler.Cancellable;
import top.vulpine.actions.scheduler.FoliaLibScheduler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the adapter routes to the right FoliaLib method.
 *
 * <p>FoliaLib's scheduler is a 40-method interface, so it is stood in for with a
 * dynamic proxy that records which method was called. That keeps the test about the
 * one thing this class decides — entity-bound versus global, immediate versus later —
 * without hand-writing forty empty overrides that would need updating every time
 * FoliaLib grows one.</p>
 *
 * <p>What this cannot check is that the work lands on the correct region on a real
 * Folia server. That needs a server.</p>
 */
class FoliaLibSchedulerTest {

    private final List<String> calls = new ArrayList<>();
    private final List<Runnable> delayed = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private boolean cancelled;
    private boolean returnNullTask;

    /** Records cancellation so the returned handle can be checked. */
    private final WrappedTask task = new WrappedTask() {

        @Override
        public void cancel() {
            cancelled = true;
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
    };

    @SuppressWarnings("unchecked")
    private PlatformScheduler proxy() {

        InvocationHandler handler = (instance, method, args) -> {

            calls.add(method.getName());

            switch (method.getName()) {

                case "runAtEntity", "runNextTick" -> {
                    // These take a Consumer<WrappedTask>, not a Runnable — the detail
                    // most likely to be got wrong from memory.
                    ((Consumer<WrappedTask>) args[args.length - 1]).accept(task);
                    return CompletableFuture.completedFuture(null);
                }

                case "runAtEntityLater", "runLater" -> {

                    for (Object argument : args) {
                        if (argument instanceof Runnable runnable) {
                            delayed.add(runnable);
                        }
                        if (argument instanceof Long ticks) {
                            delays.add(ticks);
                        }
                    }

                    return returnNullTask ? null : task;
                }

                default -> {
                    return null;
                }
            }
        };

        return (PlatformScheduler) Proxy.newProxyInstance(
                PlatformScheduler.class.getClassLoader(),
                new Class<?>[]{PlatformScheduler.class},
                handler);
    }

    private Entity entity() {
        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (instance, method, args) -> null);
    }

    @Test
    @DisplayName("immediate work with an entity goes to runAtEntity")
    void immediateWithEntity() {

        boolean[] ran = {false};

        new FoliaLibScheduler(proxy()).run(entity(), () -> ran[0] = true);

        assertEquals(List.of("runAtEntity"), calls);
        assertTrue(ran[0], "the task should have been invoked");
    }

    @Test
    @DisplayName("immediate work without an entity goes to runNextTick")
    void immediateWithoutEntity() {

        boolean[] ran = {false};

        new FoliaLibScheduler(proxy()).run(null, () -> ran[0] = true);

        assertEquals(List.of("runNextTick"), calls);
        assertTrue(ran[0]);
    }

    @Test
    @DisplayName("delayed work with an entity goes to runAtEntityLater, in ticks")
    void delayedWithEntity() {

        new FoliaLibScheduler(proxy()).runLater(entity(), () -> {
        }, 60L);

        assertEquals(List.of("runAtEntityLater"), calls);
        assertEquals(List.of(60L), delays, "the delay must be passed through unchanged");
        assertEquals(1, delayed.size());
    }

    @Test
    @DisplayName("delayed work without an entity goes to runLater")
    void delayedWithoutEntity() {

        new FoliaLibScheduler(proxy()).runLater(null, () -> {
        }, 20L);

        assertEquals(List.of("runLater"), calls);
        assertEquals(List.of(20L), delays);
    }

    @Test
    @DisplayName("the returned handle cancels the underlying task")
    void cancelDelegates() {

        Cancellable handle = new FoliaLibScheduler(proxy()).runLater(null, () -> {
        }, 20L);

        assertFalse(cancelled);
        handle.cancel();
        assertTrue(cancelled, "cancel should reach FoliaLib's task");
    }

    @Test
    @DisplayName("a null task from FoliaLib yields a no-op handle, not an NPE")
    void nullTaskIsSafe() {

        returnNullTask = true;

        // FoliaLib returns null when the entity is already gone. Calling cancel on the
        // result must not throw — the run is over either way.
        Cancellable handle = new FoliaLibScheduler(proxy()).runLater(entity(), () -> {
        }, 20L);

        assertSame(Cancellable.NOOP, handle);
        handle.cancel();
    }
}
