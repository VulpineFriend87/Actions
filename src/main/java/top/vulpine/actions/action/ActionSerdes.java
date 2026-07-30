package top.vulpine.actions.action;

import eu.okaeri.configs.serdes.OkaeriSerdesPack;
import eu.okaeri.configs.serdes.SerdesRegistry;

import java.util.Objects;

/**
 * Registers action support with an okaeri config.
 *
 * <pre>{@code
 * ConfigManager.create(Config.class, it -> {
 *     it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit(),
 *             new ActionSerdes(registry).migrateShorthand(true));
 *     ...
 * });
 * }</pre>
 *
 * <p>Once registered, a config field can be declared as {@code List<Action>} and
 * both config forms load into it.</p>
 */
public final class ActionSerdes implements OkaeriSerdesPack {

    private final ActionRegistry registry;
    private boolean migrate;

    /**
     * @param registry where action types are looked up
     */
    public ActionSerdes(final ActionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Upgrades one-liners to the block form the next time the config is saved.
     *
     * <p><strong>This rewrites the operator's file.</strong> okaeri regenerates it
     * from the schema, so any comments they added by hand are lost, and key order and
     * formatting change. Back the file up before loading a config with this enabled,
     * and say in the console where the backup went — "migrated" on its own is not
     * something anyone can act on.</p>
     *
     * @param value true to migrate
     * @return this pack
     */
    public ActionSerdes migrateShorthand(final boolean value) {
        this.migrate = value;
        return this;
    }

    @Override
    public void register(final SerdesRegistry serdesRegistry) {
        serdesRegistry.register(new ActionSerializer(registry, migrate));
        serdesRegistry.register(new Choice.Serializer());
    }
}
