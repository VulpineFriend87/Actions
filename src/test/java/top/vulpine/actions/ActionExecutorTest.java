package top.vulpine.actions;

import eu.okaeri.configs.serdes.SerializationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.ActionExecutor;
import top.vulpine.actions.action.Flow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionExecutorTest {

    private final List<String> ran = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private FakeScheduler scheduler;

    @BeforeEach
    void setUp() {
        ran.clear();
        warnings.clear();
        scheduler = new FakeScheduler();
        Actions.logger(warnings::add);
    }

    private ActionContext context() {
        return ActionContext.builder(scheduler).build();
    }

    /** Records that it ran, then returns whatever flow the test asked for. */
    private Action action(final String name, final Function<ActionContext, Flow> behaviour) {

        return new Action() {

            @Override
            public String type() {
                return name;
            }

            @Override
            public Flow execute(final ActionContext context) {
                ran.add(name);
                return behaviour.apply(context);
            }

            @Override
            public void write(final SerializationData data) {
            }
        };
    }

    private Action step(final String name) {
        return action(name, context -> Flow.CONTINUE);
    }

    @Test
    @DisplayName("runs a flat list in order")
    void flatOrder() {

        ActionExecutor.run(List.of(step("a"), step("b"), step("c")), context());

        assertEquals(List.of("a", "b", "c"), ran);
    }

    @Test
    @DisplayName("a nested list runs, then the parent continues after it")
    void nestedThenParent() {

        Action branch = action("if", context -> Flow.enter(List.of(step("x"), step("y"))));

        ActionExecutor.run(List.of(step("a"), branch, step("b")), context());

        assertEquals(List.of("a", "if", "x", "y", "b"), ran);
    }

    @Test
    @DisplayName("STOP inside a nested list abandons the parent too")
    void stopEscapesNesting() {

        Action inner = action("inner", context -> Flow.STOP);
        Action branch = action("if", context -> Flow.enter(List.of(inner, step("never"))));

        ActionExecutor.run(List.of(step("a"), branch, step("alsoNever")), context());

        assertEquals(List.of("a", "if", "inner"), ran);
    }

    @Test
    @DisplayName("a delay nested two levels deep resumes at exactly the right place")
    void delayResumesInsideNesting() {

        // The case a single resume index cannot express: after the delay, execution
        // must continue at 'y' inside the inner list, then 'z' in the outer one,
        // then 'b' in the root.
        Action inner = action("inner", context -> Flow.enter(
                List.of(step("x"), action("delay", c -> Flow.delay(20L)), step("y"))));

        Action outer = action("outer", context -> Flow.enter(List.of(inner, step("z"))));

        ActionExecutor executor = ActionExecutor.run(List.of(step("a"), outer, step("b")), context());

        assertEquals(List.of("a", "outer", "inner", "x", "delay"), ran, "should pause at the delay");
        assertFalse(executor.finished());
        assertEquals(1, scheduler.pending());

        assertTrue(scheduler.tick());

        assertEquals(List.of("a", "outer", "inner", "x", "delay", "y", "z", "b"), ran);
        assertTrue(executor.finished());
    }

    @Test
    @DisplayName("several delays in one list each resume correctly")
    void repeatedDelays() {

        ActionExecutor.run(List.of(
                step("a"),
                action("d1", c -> Flow.delay(5L)),
                step("b"),
                action("d2", c -> Flow.delay(5L)),
                step("c")), context());

        assertEquals(List.of("a", "d1"), ran);
        scheduler.tick();
        assertEquals(List.of("a", "d1", "b", "d2"), ran);
        scheduler.tick();
        assertEquals(List.of("a", "d1", "b", "d2", "c"), ran);
    }

    @Test
    @DisplayName("cancelling a pending run stops it resuming")
    void cancelStopsResume() {

        ActionExecutor executor = ActionExecutor.run(
                List.of(action("delay", c -> Flow.delay(20L)), step("never")), context());

        executor.cancel();
        scheduler.tick();

        assertEquals(List.of("delay"), ran);
        assertTrue(executor.finished());
    }

    @Test
    @DisplayName("one action throwing costs one action, not the rest of the list")
    void throwingActionDoesNotKillTheList() {

        Action broken = action("broken", context -> {
            // An Error, not an Exception. Catching only Exception would let a linkage
            // failure in one action abandon everything after it.
            throw new IncompatibleClassChangeError("simulated linkage failure");
        });

        ActionExecutor.run(List.of(step("a"), broken, step("b")), context());

        assertEquals(List.of("a", "broken", "b"), ran);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("broken"), warnings.get(0));
    }

    @Test
    @DisplayName("a sequence that enters itself is abandoned instead of overflowing")
    void depthGuard() {

        Action[] holder = new Action[1];
        holder[0] = action("loop", context -> Flow.enter(List.of(holder[0])));

        ActionExecutor.run(List.of(holder[0]), context());

        assertTrue(ran.size() < 64, "should stop well before the stack does, ran " + ran.size());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("nesting")), warnings.toString());
    }

    @Test
    @DisplayName("an empty list finishes without complaint")
    void emptyList() {

        ActionExecutor executor = ActionExecutor.run(List.of(), context());

        assertTrue(ran.isEmpty());
        assertTrue(executor.finished());
        assertTrue(warnings.isEmpty());
    }
}
