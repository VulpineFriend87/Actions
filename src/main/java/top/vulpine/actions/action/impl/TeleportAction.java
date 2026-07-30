package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;
import top.vulpine.actions.target.Target;

import java.util.List;

/**
 * Teleports to fixed coordinates.
 *
 * <pre>{@code
 * - type: teleport
 *   target: self
 *   world: lobby
 *   x: 0.5
 *   y: 100
 *   z: 0.5
 *   yaw: 90
 *   pitch: 0
 * }</pre>
 *
 * <p>Only fixed coordinates: a plugin with its own notion of a destination — a spawn
 * point, an arena, a saved warp — registers its own action for that, because the
 * library has no way to know where "spawn" is.</p>
 *
 * <p>Uses {@code teleportAsync}, which is the form that works on regionised servers.
 * A plain {@code teleport} across regions fails on Folia.</p>
 *
 * <p>The world is resolved at run time rather than at load: world managers such as
 * Multiverse may not have loaded it yet when the config is read, and refusing a
 * destination that becomes valid two seconds later would be wrong.</p>
 */
public final class TeleportAction implements Action {

    /** The registered id. */
    public static final String TYPE = "teleport";

    private final Target target;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    private TeleportAction(final Target target, final String world, final double x, final double y,
                           final double z, final float yaw, final float pitch) {

        this.target = target;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Starts building one in code, for a config default.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the settings of a teleport. Named coordinates rather than five
     * positional doubles, which is the whole reason this action has no one-liner form.
     */
    public static final class Builder {

        private Target target = Target.SELF;
        private String world;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        private Builder() {
        }

        /** @param value who is teleported @return this builder */
        public Builder target(final Target value) {
            this.target = value == null ? Target.SELF : value;
            return this;
        }

        /** @param value the destination world name @return this builder */
        public Builder world(final String value) {
            this.world = value;
            return this;
        }

        /** @param value the x coordinate @return this builder */
        public Builder x(final double value) {
            this.x = value;
            return this;
        }

        /** @param value the y coordinate @return this builder */
        public Builder y(final double value) {
            this.y = value;
            return this;
        }

        /** @param value the z coordinate @return this builder */
        public Builder z(final double value) {
            this.z = value;
            return this;
        }

        /** @param value the yaw @return this builder */
        public Builder yaw(final float value) {
            this.yaw = value;
            return this;
        }

        /** @param value the pitch @return this builder */
        public Builder pitch(final float value) {
            this.pitch = value;
            return this;
        }

        /** @return the action */
        public TeleportAction build() {
            return new TeleportAction(target, world, x, y, z, yaw, pitch);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static TeleportAction read(final DeserializationData data) {

        String world = data.containsKey("world") ? data.get("world", String.class) : null;

        if (world == null || world.isBlank()) {
            Actions.warn("Teleport action has no 'world'; it will do nothing.");
        }

        return new TeleportAction(
                Target.parse(data.containsKey("target") ? data.get("target", String.class) : null),
                world,
                number(data, "x", 0D),
                number(data, "y", 0D),
                number(data, "z", 0D),
                (float) number(data, "yaw", 0D),
                (float) number(data, "pitch", 0D));
    }

    private static double number(final DeserializationData data, final String key, final double fallback) {

        if (!data.containsKey(key)) {
            return fallback;
        }

        String raw = data.get(key, String.class);

        try {
            return Double.parseDouble(raw.trim());

        } catch (NumberFormatException | NullPointerException e) {
            Actions.warn("Teleport '" + key + "' is not a number: " + raw);
            return fallback;
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (world == null || world.isBlank()) {
            return Flow.CONTINUE;
        }

        World resolved = Bukkit.getWorld(world);

        if (resolved == null) {
            Actions.warnOnce("Teleport target world '" + world + "' is not loaded; skipping. "
                    + "Is the name right, and is the world manager loading it?");
            return Flow.CONTINUE;
        }

        List<Player> players = target.resolve(context);

        if (players.isEmpty()) {
            return Flow.CONTINUE;
        }

        Location destination = new Location(resolved, x, y, z, yaw, pitch);

        for (Player player : players) {
            // teleportAsync rather than teleport: the latter throws across regions on
            // Folia, and this is exactly the call that crosses them.
            context.scheduler().run(player, () -> player.teleportAsync(destination));
        }

        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {

        data.add("target", target.raw());
        data.add("world", world == null ? "" : world);
        data.add("x", String.valueOf(x));
        data.add("y", String.valueOf(y));
        data.add("z", String.valueOf(z));
        data.add("yaw", String.valueOf(yaw));
        data.add("pitch", String.valueOf(pitch));
    }

    /**
     * @return the destination world name as configured
     */
    public String world() {
        return world;
    }
}
