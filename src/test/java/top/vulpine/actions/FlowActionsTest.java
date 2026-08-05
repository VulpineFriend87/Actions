package top.vulpine.actions;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.ActionExecutor;
import top.vulpine.actions.action.ActionSerdes;
import top.vulpine.actions.action.SequenceRegistry;
import top.vulpine.actions.action.impl.RandomAction;
import top.vulpine.actions.action.impl.RepeatAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowActionsTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path folder;

    private final List<String> warnings = new ArrayList<>();

    public static class TestConfig extends OkaeriConfig {
        public List<Action> actions = new ArrayList<>();
    }

    @BeforeEach
    void setUp() {
        warnings.clear();
        Actions.logger(warnings::add);
        Actions.placeholders(null);
    }

    private TestConfig load(final String yaml) throws IOException {

        Path file = folder.resolve("config-" + System.nanoTime() + ".yml");
        Files.writeString(file, yaml);

        return ConfigManager.create(TestConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer(), new ActionSerdes(BuiltinActions.registry()));
            it.withBindFile(file);
            it.load();
        });
    }

    private String save(final TestConfig config, final String name) throws IOException {
        Path file = folder.resolve(name);
        config.withBindFile(file);
        config.save();
        return Files.readString(file);
    }

    // --- stop ------------------------------------------------------------------

    @Test
    @DisplayName("stop inside a then-branch ends the whole run, not just the branch")
    void stopEscapesBranch() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: if
                    condition: "chance: 100%"
                    then:
                      - "[stop]"
                      - type: delay
                        time: 1s
                  - type: delay
                    time: 5s
                """);

        FakeScheduler scheduler = new FakeScheduler();
        ActionExecutor executor = ActionExecutor.run(config.actions,
                ActionContext.builder(scheduler.platform()).build());

        assertEquals(0, scheduler.pending(), "nothing after the stop should have run");
        assertTrue(executor.finished());
    }

    // --- repeat ----------------------------------------------------------------

    @Test
    @DisplayName("repeat runs the list the given number of times")
    void repeat() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: repeat
                    times: 3
                    actions:
                      - type: set
                        key: n
                        value: "x"
                      - type: delay
                        time: 1t
                """);

        RepeatAction action = assertInstanceOf(RepeatAction.class, config.actions.get(0));
        assertEquals(3, action.times());
        assertEquals(2, action.actions().size());

        FakeScheduler scheduler = new FakeScheduler();
        ActionExecutor.run(config.actions, ActionContext.builder(scheduler.platform()).build());

        // One delay per pass: three pauses, then done.
        int pauses = 0;
        while (scheduler.tick()) {
            pauses++;
        }

        assertEquals(3, pauses, "the delay inside should fire once per repetition");
    }

    @Test
    @DisplayName("an absurd repeat count is capped instead of exhausting memory")
    void repeatCapped() throws IOException {

        RepeatAction action = assertInstanceOf(RepeatAction.class, load("""
                actions:
                  - type: repeat
                    times: 5000000
                    actions:
                      - "[stop]"
                """).actions.get(0));

        assertEquals(1000, action.times());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("limit")), warnings.toString());
    }

    @Test
    @DisplayName("repeat round-trips as one pass, not as the flattened list")
    void repeatRoundTrip() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: repeat
                    times: 4
                    actions:
                      - "[message] self; ciao"
                """);

        String written = save(config, "repeat.yml");

        assertTrue(written.contains("times: 4"), written);
        assertEquals(1, written.split("\\[message]", -1).length - 1,
                "the action should appear once, not four times:\n" + written);
    }

    // --- random ----------------------------------------------------------------

    @Test
    @DisplayName("random reads weighted options")
    void random() throws IOException {

        RandomAction action = assertInstanceOf(RandomAction.class, load("""
                actions:
                  - type: random
                    options:
                      - weight: 3
                        actions:
                          - "[message] self; comune"
                      - actions:
                          - "[message] self; senza peso"
                """).actions.get(0));

        assertEquals(2, action.options().size());
        assertEquals(3, action.options().get(0).weight());
        assertEquals(1, action.options().get(1).weight(), "a missing weight means 1");
    }

    @Test
    @DisplayName("random always picks exactly one option")
    void randomPicksOne() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: random
                    options:
                      - weight: 1
                        actions:
                          - type: delay
                            time: 1t
                      - weight: 1
                        actions:
                          - type: delay
                            time: 1t
                      - weight: 1
                        actions:
                          - type: delay
                            time: 1t
                """);

        for (int attempt = 0; attempt < 50; attempt++) {

            FakeScheduler scheduler = new FakeScheduler();
            ActionExecutor.run(config.actions, ActionContext.builder(scheduler.platform()).build());

            assertEquals(1, scheduler.pending(), "exactly one branch must run");
        }
    }

    @Test
    @DisplayName("weights actually skew the choice")
    void weightsSkew() throws IOException {

        // 99:1 — over 200 runs the heavy option should dominate. Loose enough not to
        // be flaky, tight enough to fail if weights were ignored.
        TestConfig config = load("""
                actions:
                  - type: random
                    options:
                      - weight: 99
                        actions:
                          - type: set
                            key: picked
                            value: heavy
                      - weight: 1
                        actions:
                          - type: set
                            key: picked
                            value: light
                """);

        int heavy = 0;

        for (int attempt = 0; attempt < 200; attempt++) {

            ActionContext context = ActionContext.builder(new FakeScheduler().platform()).build();
            ActionExecutor.run(config.actions, context);

            if ("heavy".equals(context.variables().get("picked"))) {
                heavy++;
            }
        }

        assertTrue(heavy > 150, "expected the heavy option to dominate, got " + heavy + "/200");
    }

    @Test
    @DisplayName("random round-trips its options")
    void randomRoundTrip() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: random
                    options:
                      - weight: 2
                        actions:
                          - "[message] self; a"
                """);

        String written = save(config, "random.yml");

        assertTrue(written.contains("weight: 2"), written);
        assertTrue(written.contains("[message] self; a"), written);
    }

    // --- set -------------------------------------------------------------------

    @Test
    @DisplayName("set stores a value later actions can read")
    void set() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: set
                    key: greeting
                    value: "ciao %name%"
                """);

        ActionContext context = ActionContext.builder(new FakeScheduler().platform())
                .value("name", "Vulpine")
                .build();

        ActionExecutor.run(config.actions, context);

        assertEquals("ciao Vulpine", context.variables().get("greeting"));
    }

    @Test
    @DisplayName("a variable shadows a fixed value of the same name")
    void setOverrides() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: set
                    key: name
                    value: "sovrascritto"
                  - type: set
                    key: copy
                    value: "%name%"
                """);

        ActionContext context = ActionContext.builder(new FakeScheduler().platform())
                .value("name", "originale")
                .build();

        ActionExecutor.run(config.actions, context);

        assertEquals("sovrascritto", context.variables().get("copy"),
                "the variable set during the run should win");
    }

    @Test
    @DisplayName("set is not escaped, so it can be compared afterwards")
    void setIsRaw() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: set
                    key: copied
                    value: "%tricky%"
                """);

        ActionContext context = ActionContext.builder(new FakeScheduler().platform())
                .value("tricky", "a<b")
                .build();

        ActionExecutor.run(config.actions, context);

        assertEquals("a<b", context.variables().get("copied"),
                "escaping here would store 'a\\<b' and break comparisons");
    }

    // --- run -------------------------------------------------------------------

    @Test
    @DisplayName("run enters a named sequence")
    void run() throws IOException {

        TestConfig sequence = load("""
                actions:
                  - type: delay
                    time: 1s
                """);

        SequenceRegistry registry = new SequenceRegistry();
        registry.put("welcome", sequence.actions);

        TestConfig config = load("""
                actions:
                  - "[run] welcome"
                """);

        FakeScheduler scheduler = new FakeScheduler();

        ActionExecutor.run(config.actions, ActionContext.builder(scheduler.platform())
                .sequences(registry)
                .build());

        assertEquals(1, scheduler.pending(), "the sequence's delay should be pending");
    }

    @Test
    @DisplayName("an unknown sequence warns once and lists what exists")
    void runUnknown() throws IOException {

        SequenceRegistry registry = new SequenceRegistry();
        registry.put("welcome", List.of());

        TestConfig config = load("""
                actions:
                  - "[run] nonesiste"
                """);

        ActionContext context = ActionContext.builder(new FakeScheduler().platform()).sequences(registry).build();

        ActionExecutor.run(config.actions, context);
        ActionExecutor.run(config.actions, context);

        assertEquals(1, warnings.stream().filter(w -> w.contains("nonesiste")).count(),
                "should not repeat per run: " + warnings);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("welcome")),
                "the warning should list known sequences: " + warnings);
    }

    @Test
    @DisplayName("run without a registry says how to supply one")
    void runWithoutRegistry() throws IOException {

        ActionExecutor.run(load("""
                actions:
                  - "[run] welcome"
                """).actions, ActionContext.builder(new FakeScheduler().platform()).build());

        assertTrue(warnings.stream().anyMatch(w -> w.contains("sequences(registry)")), warnings.toString());
    }

    @Test
    @DisplayName("a sequence that runs itself is abandoned, not stack-overflowed")
    void selfReferentialSequence() throws IOException {

        TestConfig loop = load("""
                actions:
                  - "[run] loop"
                """);

        SequenceRegistry registry = new SequenceRegistry();
        registry.put("loop", loop.actions);

        ActionExecutor.run(loop.actions, ActionContext.builder(new FakeScheduler().platform())
                .sequences(registry)
                .build());

        assertTrue(warnings.stream().anyMatch(w -> w.contains("nesting")),
                "the executor's depth guard should catch it: " + warnings);
    }
}
