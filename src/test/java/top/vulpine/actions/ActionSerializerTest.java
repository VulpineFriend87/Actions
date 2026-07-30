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
import top.vulpine.actions.action.ActionSerdes;
import top.vulpine.actions.action.UnknownAction;
import top.vulpine.actions.action.impl.DelayAction;
import top.vulpine.actions.action.impl.MessageAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionSerializerTest {

    // Never: okaeri keeps the bound file open, so on Windows the automatic delete
    // fails and reports as a test error even when the test itself passed.
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

    @Test
    @DisplayName("reads the block form")
    void readsBlockForm() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: message
                    target: all
                    text: "<green>hello"
                """);

        assertEquals(1, config.actions.size());

        MessageAction action = assertInstanceOf(MessageAction.class, config.actions.get(0));
        assertEquals("all", action.target().raw());
        assertEquals(List.of("<green>hello"), action.lines());
        assertNull(action.shorthand(), "a block-form action has no one-liner");
    }

    @Test
    @DisplayName("reads the one-liner form")
    void readsShorthandForm() throws IOException {

        TestConfig config = load("""
                actions:
                  - "[message] all; <green>hello"
                """);

        MessageAction action = assertInstanceOf(MessageAction.class, config.actions.get(0));
        assertEquals("all", action.target().raw());
        assertEquals(List.of("<green>hello"), action.lines());
        assertEquals("[message] all; <green>hello", action.shorthand());
    }

    @Test
    @DisplayName("both forms coexist in one list")
    void bothFormsInOneList() throws IOException {

        // The whole reason this is a single serializer: an existing config keeps
        // working while new entries use the richer form, in the same list.
        TestConfig config = load("""
                actions:
                  - "[message] self; old style"
                  - type: message
                    target: all
                    text: "new style"
                  - "[delay] 3s"
                """);

        assertEquals(3, config.actions.size());
        assertInstanceOf(MessageAction.class, config.actions.get(0));
        assertInstanceOf(MessageAction.class, config.actions.get(1));
        assertEquals(60L, assertInstanceOf(DelayAction.class, config.actions.get(2)).ticks());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("saving writes one-liners back as one-liners")
    void shorthandRoundTrips() throws IOException {

        TestConfig config = load("""
                actions:
                  - "[message] self; hello"
                """);

        String written = save(config, "roundtrip.yml");

        assertTrue(written.contains("[message] self; hello"), written);
        assertTrue(written.contains("- '[message]") || written.contains("- \"[message]"),
                "should still be a scalar, not expanded into a block:\n" + written);
    }

    @Test
    @DisplayName("saving writes block-form actions as blocks")
    void blockRoundTrips() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: message
                    target: world:lobby
                    text: "hi"
                """);

        String written = save(config, "block.yml");

        assertTrue(written.contains("type: message"), written);
        // The parameterised target must survive; it cannot describe itself from a
        // lambda, so this is what proves it carries its source text.
        assertTrue(written.contains("world:lobby"), written);
    }

    @Test
    @DisplayName("a list of lines is preserved")
    void multipleLines() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: message
                    target: self
                    text:
                      - "one"
                      - "two"
                """);

        MessageAction action = assertInstanceOf(MessageAction.class, config.actions.get(0));
        assertEquals(List.of("one", "two"), action.lines());
    }

    @Test
    @DisplayName("an unknown type warns, does nothing, and the rest still loads")
    void unknownTypeIsKeptInert() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: teleport_to_the_moon
                    distance: far
                  - "[message] self; still here"
                """);

        assertEquals(2, config.actions.size());
        assertInstanceOf(UnknownAction.class, config.actions.get(0));
        assertInstanceOf(MessageAction.class, config.actions.get(1));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("teleport_to_the_moon")), warnings.toString());
    }

    @Test
    @DisplayName("a broken entry survives a save instead of being erased")
    void brokenEntriesAreNotLostOnSave() throws IOException {

        // The operator almost certainly meant this to work. Loading must not quietly
        // delete their line the next time the config is written.
        TestConfig config = load("""
                actions:
                  - type: teleport_to_the_moon
                    distance: far
                  - "message] self; missing bracket"
                """);

        String written = save(config, "broken.yml");

        assertTrue(written.contains("teleport_to_the_moon"), written);
        assertTrue(written.contains("distance: far"), written);
        assertTrue(written.contains("message] self; missing bracket"), written);
    }

    @Test
    @DisplayName("a malformed one-liner warns and stays inert")
    void malformedShorthandIsKeptInert() throws IOException {

        TestConfig config = load("""
                actions:
                  - "message] self; missing bracket"
                  - "[message] self; fine"
                """);

        assertEquals(2, config.actions.size());
        assertInstanceOf(UnknownAction.class, config.actions.get(0));
        assertInstanceOf(MessageAction.class, config.actions.get(1));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("[type]")), warnings.toString());
    }

    @Test
    @DisplayName("legacy target and delay spellings still mean what they used to")
    void legacyCompatibility() throws IOException {

        TestConfig config = load("""
                actions:
                  - "[message] global; broadcast"
                  - "[delay] 200"
                """);

        assertEquals("all", assertInstanceOf(MessageAction.class, config.actions.get(0)).target().raw(),
                "'global' should still mean everyone");

        // A bare number in the inline format is milliseconds, so 200 must stay 4 ticks
        // rather than becoming 200 and retiming every config that uses it.
        assertEquals(4L, assertInstanceOf(DelayAction.class, config.actions.get(1)).ticks());
    }
}
