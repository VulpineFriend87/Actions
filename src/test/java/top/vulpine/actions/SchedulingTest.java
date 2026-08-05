package top.vulpine.actions;

import eu.okaeri.configs.serdes.SerializationData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.ActionExecutor;
import top.vulpine.actions.action.Flow;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Checks which FoliaLib method a delay reaches for.
 *
 * <p>The choice is entity-bound versus global, and it is the one scheduling decision
 * this library still makes: a run belonging to a player must resume on that player's
 * region, and a run belonging to nobody has no region to resume on.</p>
 *
 * <p>What this cannot check is that the work lands on the correct region on a real
 * Folia server. That needs a server.</p>
 */
class SchedulingTest {

    private final FakeScheduler scheduler = new FakeScheduler();

    /** A list that gets one action in and then waits. */
    private static final List<Action> DELAYED = List.of(new Action() {

        @Override
        public String type() {
            return "delay";
        }

        @Override
        public Flow execute(final ActionContext context) {
            return Flow.delay(20L);
        }

        @Override
        public void write(final SerializationData data) {
        }
    });

    private static Player player() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (instance, method, args) -> null);
    }

    @Test
    @DisplayName("a delay in a run with a player resumes on that player's region")
    void delayWithPlayer() {

        ActionExecutor.run(DELAYED, ActionContext.builder(scheduler.platform())
                .player(player())
                .build());

        assertEquals(List.of("runAtEntityLater"), scheduler.calls());
        assertEquals(1, scheduler.pending());
    }

    @Test
    @DisplayName("a delay in a run with no player goes to the global scheduler")
    void delayWithoutPlayer() {

        ActionExecutor.run(DELAYED, ActionContext.builder(scheduler.platform()).build());

        assertEquals(List.of("runLater"), scheduler.calls());
        assertEquals(1, scheduler.pending());
    }

    @Test
    @DisplayName("cancelling is safe when FoliaLib never handed back a task")
    void nullTaskIsSafe() {

        // FoliaLib returns null when the player is already gone. The run is over
        // either way, but cancel() must not throw on the way out.
        scheduler.returnNullTasks();

        ActionExecutor executor = ActionExecutor.run(DELAYED, ActionContext.builder(scheduler.platform())
                .player(player())
                .build());

        assertFalse(executor.finished(), "the run paused at the delay");

        executor.cancel();
    }
}
