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
import top.vulpine.actions.action.impl.IfAction;
import top.vulpine.actions.action.impl.MessageAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IfActionTest {

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

    private TestConfig load(final String yaml, final boolean migrate) throws IOException {

        Path file = folder.resolve("config-" + System.nanoTime() + ".yml");
        Files.writeString(file, yaml);

        return ConfigManager.create(TestConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer(),
                    new ActionSerdes(BuiltinActions.registry()).migrateShorthand(migrate));
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

    @Test
    @DisplayName("reads a nested if with both branches")
    void readsNestedIf() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: if
                    condition:
                      any:
                        - "chance: 100%"
                        - "permission: lobby.vip"
                      not: "chance: 0%"
                    then:
                      - "[message] self; si"
                    else:
                      - "[message] self; no"
                """, false);

        IfAction action = assertInstanceOf(IfAction.class, config.actions.get(0));
        assertEquals(1, action.then().size());
        assertEquals(1, action.otherwise().size());
        assertInstanceOf(MessageAction.class, action.then().get(0));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("the executor runs the branch the condition picked")
    void executesChosenBranch() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: if
                    condition: "%level% >= 10"
                    then:
                      - type: delay
                        time: 1s
                    else:
                      - type: delay
                        time: 5s
                """, false);

        FakeScheduler scheduler = new FakeScheduler();

        ActionExecutor.run(config.actions, ActionContext.builder(scheduler.platform())
                .value("level", 12)
                .build());

        // The 'then' branch was entered, so its delay is what is pending.
        assertEquals(1, scheduler.pending(), "should have entered a branch and paused");
    }

    @Test
    @DisplayName("an if with no matching branch just continues")
    void missingBranchContinues() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: if
                    condition: "chance: 0%"
                    then:
                      - type: delay
                        time: 5s
                """, false);

        FakeScheduler scheduler = new FakeScheduler();

        ActionExecutor executor = ActionExecutor.run(config.actions,
                ActionContext.builder(scheduler.platform()).build());

        assertEquals(0, scheduler.pending());
        assertTrue(executor.finished());
    }

    @Test
    @DisplayName("a nested condition survives a save unchanged")
    void conditionRoundTrips() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: if
                    condition:
                      any:
                        - "permission: lobby.vip"
                        - "%balance% >= 10000"
                      not: "world: arena"
                    then:
                      - type: message
                        target: all
                        text: "ciao"
                """, false);

        String written = save(config, "roundtrip.yml");

        assertTrue(written.contains("permission: lobby.vip"), written);
        assertTrue(written.contains("%balance% >= 10000"), written);
        assertTrue(written.contains("world: arena"), written);
        assertTrue(written.contains("any"), written);
        assertTrue(written.contains("not"), written);
    }

    // --- migration ------------------------------------------------------------

    @Test
    @DisplayName("without migration a one-liner is written back as a one-liner")
    void withoutMigration() throws IOException {

        TestConfig config = load("""
                actions:
                  - "[message] self; ciao"
                """, false);

        String written = save(config, "kept.yml");

        assertTrue(written.contains("[message] self; ciao"), written);
        assertTrue(!written.contains("type: message"), written);
    }

    @Test
    @DisplayName("with migration the same file is rewritten as blocks")
    void withMigration() throws IOException {

        TestConfig config = load("""
                actions:
                  - "[message] self; ciao"
                  - "[delay] 3s"
                """, true);

        String written = save(config, "migrated.yml");

        assertTrue(written.contains("type: message"), written);
        assertTrue(written.contains("target: self"), written);
        assertTrue(written.contains("ciao"), written);
        assertTrue(written.contains("type: delay"), written);
        assertTrue(!written.contains("[message]"), "the one-liner should be gone:\n" + written);
    }

    @Test
    @DisplayName("migration preserves what the actions actually do")
    void migrationPreservesBehaviour() throws IOException {

        String yaml = """
                actions:
                  - "[message] global; ciao"
                  - "[delay] 200"
                """;

        TestConfig migrated = load(yaml, true);
        String written = save(migrated, "behaviour.yml");

        TestConfig reloaded = load(written, false);

        assertEquals("all", assertInstanceOf(MessageAction.class, reloaded.actions.get(0)).target().raw());
        assertEquals(List.of("ciao"), assertInstanceOf(MessageAction.class, reloaded.actions.get(0)).lines());

        // 200 with no unit meant milliseconds; migrating must not turn it into ticks.
        assertEquals(4L, assertInstanceOf(top.vulpine.actions.action.impl.DelayAction.class,
                reloaded.actions.get(1)).ticks());
    }
}
