package top.vulpine.actions;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.bukkit.GameMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionSerdes;
import top.vulpine.actions.action.Ticks;
import top.vulpine.actions.action.impl.ActionBarAction;
import top.vulpine.actions.action.impl.CommandAction;
import top.vulpine.actions.action.impl.GamemodeAction;
import top.vulpine.actions.action.impl.SoundAction;
import top.vulpine.actions.action.impl.TeleportAction;
import top.vulpine.actions.action.impl.TitleAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleActionsTest {

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

    private Action first(final String yaml) throws IOException {
        return load(yaml).actions.get(0);
    }

    // --- title ----------------------------------------------------------------

    @Test
    @DisplayName("title reads both lines and the timings")
    void title() throws IOException {

        TitleAction action = assertInstanceOf(TitleAction.class, first("""
                actions:
                  - type: title
                    target: all
                    title: "<green>Benvenuto"
                    subtitle: "<gray>Buon divertimento"
                    fade_in: 20t
                    stay: 3s
                    fade_out: 500ms
                """));

        assertEquals("<green>Benvenuto", action.title());
        assertEquals("<gray>Buon divertimento", action.subtitle());
        assertEquals(Duration.ofMillis(1000), action.times().fadeIn());
        assertEquals(Duration.ofMillis(3000), action.times().stay());
        assertEquals(Duration.ofMillis(500), action.times().fadeOut());
    }

    @Test
    @DisplayName("bare numbers in a title one-liner are ticks, not milliseconds")
    void titleShorthandUnits() throws IOException {

        // Bare numbers in a title are ticks, unlike a delay. Reading them as
        // milliseconds would make every title configured this way flash by.
        TitleAction action = assertInstanceOf(TitleAction.class, first("""
                actions:
                  - "[title] self; Ciao; Sottotitolo; 20; 60; 20"
                """));

        assertEquals(Duration.ofMillis(1000), action.times().fadeIn());
        assertEquals(Duration.ofMillis(3000), action.times().stay());
        assertEquals("Ciao", action.title());
        assertEquals("Sottotitolo", action.subtitle());
    }

    @Test
    @DisplayName("a title with only a subtitle is allowed")
    void subtitleOnly() throws IOException {

        TitleAction action = assertInstanceOf(TitleAction.class, first("""
                actions:
                  - type: title
                    subtitle: "solo sotto"
                """));

        assertEquals("", action.title());
        assertEquals("solo sotto", action.subtitle());
    }

    @Test
    @DisplayName("durations are written back as configured, not normalised to ticks")
    void titleRoundTrip() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: title
                    title: "x"
                    stay: 3s
                """);

        assertTrue(save(config, "title.yml").contains("stay: 3s"));
    }

    // --- actionbar -------------------------------------------------------------

    @Test
    @DisplayName("actionbar, both forms")
    void actionBar() throws IOException {

        assertEquals("<yellow>ciao", assertInstanceOf(ActionBarAction.class, first("""
                actions:
                  - type: actionbar
                    text: "<yellow>ciao"
                """)).text());

        assertEquals("<yellow>ciao", assertInstanceOf(ActionBarAction.class, first("""
                actions:
                  - "[actionbar] self; <yellow>ciao"
                """)).text());
    }

    // --- sound -----------------------------------------------------------------

    @Test
    @DisplayName("a valid namespaced key is accepted")
    void soundValid() throws IOException {

        SoundAction action = assertInstanceOf(SoundAction.class, first("""
                actions:
                  - type: sound
                    key: "entity.player.levelup"
                    volume: 0.5
                    pitch: 1.4
                    source: player
                """));

        assertTrue(action.valid());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("an old enum name is refused with a message that says what to use")
    void soundEnumName() throws IOException {

        SoundAction action = assertInstanceOf(SoundAction.class, first("""
                actions:
                  - type: sound
                    key: "ENTITY_PLAYER_LEVELUP"
                """));

        assertFalse(action.valid(), "must not pretend to work");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("enum name")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("entity.player.levelup")),
                "the warning should show the correct form: " + warnings);
    }

    @Test
    @DisplayName("a blank key disables the sound without complaint")
    void soundBlank() throws IOException {

        SoundAction action = assertInstanceOf(SoundAction.class, first("""
                actions:
                  - type: sound
                    key: ""
                """));

        assertFalse(action.valid());
        assertTrue(warnings.isEmpty(), "clearing a key is how you turn a sound off: " + warnings);
    }

    @Test
    @DisplayName("an unknown source falls back to master and says why it matters")
    void soundSource() throws IOException {

        first("""
                actions:
                  - type: sound
                    key: "ui.button.click"
                    source: nonsense
                """);

        assertTrue(warnings.stream().anyMatch(w -> w.contains("volume slider")), warnings.toString());
    }

    // --- command ---------------------------------------------------------------

    @Test
    @DisplayName("command defaults to console and keeps the old one-liner order")
    void command() throws IOException {

        CommandAction action = assertInstanceOf(CommandAction.class, first("""
                actions:
                  - "[command] console; give %player% diamond"
                """));

        assertTrue(action.asConsole());
        assertEquals("give %player% diamond", action.command());
    }

    @Test
    @DisplayName("as: player is honoured")
    void commandAsPlayer() throws IOException {

        assertFalse(assertInstanceOf(CommandAction.class, first("""
                actions:
                  - type: command
                    as: player
                    command: "spawn"
                """)).asConsole());
    }

    @Test
    @DisplayName("an unknown 'as' warns and falls back to console")
    void commandBadAs() throws IOException {

        assertTrue(assertInstanceOf(CommandAction.class, first("""
                actions:
                  - type: command
                    as: wizard
                    command: "spawn"
                """)).asConsole());

        assertTrue(warnings.stream().anyMatch(w -> w.contains("wizard")), warnings.toString());
    }

    // --- gamemode --------------------------------------------------------------

    @Test
    @DisplayName("gamemode is case-insensitive")
    void gamemode() throws IOException {

        assertEquals(GameMode.ADVENTURE, assertInstanceOf(GamemodeAction.class, first("""
                actions:
                  - "[gamemode] self; adventure"
                """)).mode());

        assertEquals(GameMode.CREATIVE, assertInstanceOf(GamemodeAction.class, first("""
                actions:
                  - type: gamemode
                    mode: CREATIVE
                """)).mode());
    }

    @Test
    @DisplayName("an unknown gamemode lists the valid ones")
    void gamemodeUnknown() throws IOException {

        assertEquals(null, assertInstanceOf(GamemodeAction.class, first("""
                actions:
                  - type: gamemode
                    mode: godmode
                """)).mode());

        assertTrue(warnings.stream().anyMatch(w -> w.contains("adventure")),
                "the warning should list what is valid: " + warnings);
    }

    // --- teleport --------------------------------------------------------------

    @Test
    @DisplayName("teleport reads coordinates and round-trips")
    void teleport() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: teleport
                    target: self
                    world: lobby
                    x: 0.5
                    y: 100
                    z: -12.5
                    yaw: 90
                """);

        assertEquals("lobby", assertInstanceOf(TeleportAction.class, config.actions.get(0)).world());

        String written = save(config, "tp.yml");
        assertTrue(written.contains("world: lobby"), written);
        assertTrue(written.contains("-12.5"), written);
    }

    @Test
    @DisplayName("teleport without a world warns at load rather than at run time")
    void teleportNoWorld() throws IOException {

        first("""
                actions:
                  - type: teleport
                    x: 0
                """);

        assertTrue(warnings.stream().anyMatch(w -> w.contains("no 'world'")), warnings.toString());
    }

    // --- duration parsing ------------------------------------------------------

    @Test
    @DisplayName("the two duration readings differ only for bare numbers")
    void durationUnits() {

        // Same input, different answers: the inline format uses milliseconds for
        // delays and ticks for titles.
        assertEquals(4L, Ticks.parse("200", 0L));
        assertEquals(200L, Ticks.parseTicks("200", 0L));

        // With a unit they agree.
        assertEquals(60L, Ticks.parse("3s", 0L));
        assertEquals(60L, Ticks.parseTicks("3s", 0L));
        assertEquals(20L, Ticks.parse("20t", 0L));
        assertEquals(20L, Ticks.parseTicks("20t", 0L));
    }

    @Test
    @DisplayName("a sub-tick duration waits one tick instead of none")
    void subTick() {
        // The original did integer division by 50, so 20ms became no delay at all.
        assertEquals(1L, Ticks.parse("20ms", 0L));
        assertEquals(0L, Ticks.parse("0", 5L));
    }

    // --- everything together ---------------------------------------------------

    @Test
    @DisplayName("a realistic config loads with no warnings")
    void realisticConfig() throws IOException {

        TestConfig config = load("""
                actions:
                  - type: title
                    target: self
                    title: "<green>Benvenuto, %player%"
                    subtitle: "<gray>Buon divertimento"
                    stay: 3s
                  - type: sound
                    target: self
                    key: "entity.player.levelup"
                    pitch: 1.4
                  - "[actionbar] self; <yellow>Caricamento"
                  - type: delay
                    time: 1s
                  - type: if
                    condition:
                      any:
                        - "permission: lobby.vip"
                        - "%balance% >= 10000"
                      not: "world: arena"
                    then:
                      - type: command
                        as: console
                        command: "give %player% diamond 1"
                      - type: gamemode
                        target: self
                        mode: adventure
                    else:
                      - "[message] self; <gray>Nessun perk"
                """);

        assertEquals(5, config.actions.size());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
