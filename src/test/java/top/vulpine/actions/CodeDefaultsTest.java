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
import top.vulpine.actions.action.Choice;
import top.vulpine.actions.action.impl.CommandAction;
import top.vulpine.actions.action.impl.DelayAction;
import top.vulpine.actions.action.impl.GamemodeAction;
import top.vulpine.actions.action.impl.IfAction;
import top.vulpine.actions.action.impl.MessageAction;
import top.vulpine.actions.action.impl.RandomAction;
import top.vulpine.actions.action.impl.SoundAction;
import top.vulpine.actions.action.impl.TitleAction;
import top.vulpine.actions.target.Target;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Config defaults declared in code, the way a plugin actually writes them.
 *
 * <p>The point being checked throughout: an action built in code reports no
 * {@code shorthand()}, so it is written to the file as a <strong>block</strong>. If
 * defaults were one-liners they would be rewritten on the first start of any plugin
 * with migration enabled, which makes the shipped file differ from the shipped
 * source for no reason.</p>
 */
class CodeDefaultsTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path folder;

    private final List<String> warnings = new ArrayList<>();

    /** What a plugin's config class looks like. */
    public static class JoinConfig extends OkaeriConfig {

        public List<Action> actions = new ArrayList<>(List.of(

                TitleAction.builder()
                        .target(Target.SELF)
                        .title("<green>Benvenuto, %player%")
                        .subtitle("<gray>Buon divertimento")
                        .fadeIn("20t")
                        .stay("3s")
                        .fadeOut("20t")
                        .build(),

                MessageAction.builder()
                        .target(Target.ALL)
                        .text("<aqua>%player% <gray>è entrato")
                        .build(),

                SoundAction.builder()
                        .key("entity.player.levelup")
                        .pitch(1.4F)
                        .build(),

                GamemodeAction.builder()
                        .mode(GameMode.ADVENTURE)
                        .build(),

                DelayAction.builder()
                        .time("1s")
                        .build(),

                IfAction.builder()
                        .condition("permission: lobby.vip")
                        .then(CommandAction.builder()
                                .asConsole()
                                .command("give %player% diamond 1")
                                .build())
                        .otherwise(MessageAction.builder()
                                .text("<gray>Nessun perk")
                                .build())
                        .build(),

                RandomAction.builder()
                        .choice(3, MessageAction.builder().text("<gray>Prova /spawn").build())
                        .choice(1, MessageAction.builder().text("<gray>Prova /kit").build())
                        .build()
        ));
    }

    @BeforeEach
    void setUp() {
        warnings.clear();
        Actions.logger(warnings::add);
        Actions.placeholders(null);
    }

    private JoinConfig create(final Path file) {
        return ConfigManager.create(JoinConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer(), new ActionSerdes(BuiltinActions.registry()));
            it.withBindFile(file);
        });
    }

    @Test
    @DisplayName("defaults declared in code build without warnings")
    void defaultsAreValid() {

        JoinConfig config = new JoinConfig();

        assertEquals(7, config.actions.size());
        assertTrue(warnings.isEmpty(), warnings.toString());

        for (Action action : config.actions) {
            assertNull(action.shorthand(),
                    action.type() + " built in code must not claim a one-liner form");
        }
    }

    @Test
    @DisplayName("saveDefaults writes blocks, not one-liners")
    void defaultsSaveAsBlocks() throws IOException {

        Path file = folder.resolve("defaults.yml");
        create(file).saveDefaults();

        String written = Files.readString(file);

        assertFalse(written.contains("[message]"), "no one-liners should appear:\n" + written);
        assertFalse(written.contains("[title]"), written);
        assertFalse(written.contains("[sound]"), written);

        assertTrue(written.contains("type: title"), written);
        assertTrue(written.contains("type: message"), written);
        assertTrue(written.contains("type: sound"), written);
        assertTrue(written.contains("type: if"), written);
        assertTrue(written.contains("type: random"), written);
    }

    @Test
    @DisplayName("what is written back reads as the same actions")
    void defaultsRoundTrip() throws IOException {

        Path file = folder.resolve("roundtrip.yml");
        create(file).saveDefaults();

        JoinConfig reloaded = create(file);
        reloaded.load();

        assertEquals(7, reloaded.actions.size());
        assertTrue(warnings.isEmpty(), warnings.toString());

        TitleAction title = assertInstanceOf(TitleAction.class, reloaded.actions.get(0));
        assertEquals("<green>Benvenuto, %player%", title.title());
        assertEquals(java.time.Duration.ofMillis(3000), title.times().stay());

        assertEquals("all", assertInstanceOf(MessageAction.class, reloaded.actions.get(1)).target().raw());
        assertTrue(assertInstanceOf(SoundAction.class, reloaded.actions.get(2)).valid());
        assertEquals(GameMode.ADVENTURE,
                assertInstanceOf(GamemodeAction.class, reloaded.actions.get(3)).mode());
        assertEquals(20L, assertInstanceOf(DelayAction.class, reloaded.actions.get(4)).ticks());

        IfAction branch = assertInstanceOf(IfAction.class, reloaded.actions.get(5));
        assertEquals(1, branch.then().size());
        assertEquals(1, branch.otherwise().size());

        RandomAction random = assertInstanceOf(RandomAction.class, reloaded.actions.get(6));
        assertEquals(3, random.options().get(0).weight());
    }

    @Test
    @DisplayName("migration leaves code-built defaults alone")
    void migrationIsANoOpForBlocks() throws IOException {

        Path first = folder.resolve("migrate-a.yml");

        JoinConfig config = ConfigManager.create(JoinConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer(),
                    new ActionSerdes(BuiltinActions.registry()).migrateShorthand(true));
            it.withBindFile(first);
        });

        config.saveDefaults();
        String before = Files.readString(first);

        Path second = folder.resolve("migrate-b.yml");
        config.withBindFile(second);
        config.save();

        // Nothing to migrate: the defaults were never one-liners to begin with.
        assertEquals(before, Files.readString(second));
    }
}
