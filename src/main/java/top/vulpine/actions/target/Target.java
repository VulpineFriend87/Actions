package top.vulpine.actions.target;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.ActionContext;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Who an action applies to.
 *
 * <p>Resolved once and shared by every action, so adding a selector is one change
 * rather than an edit to each action.</p>
 *
 * <p>A target keeps the text it was parsed from, because a lambda cannot describe
 * itself: without it, saving the config would turn a selector like
 * {@code world:lobby} into something else.</p>
 *
 * <p>{@code player} and {@code global} are accepted as older names for {@code self}
 * and {@code all}.</p>
 */
public final class Target {

    /** The triggering player alone. */
    public static final Target SELF = new Target("self", context -> {
        Player player = context.player();
        return player == null ? List.of() : List.of(player);
    });

    /** Everyone online. */
    public static final Target ALL = new Target("all",
            context -> List.copyOf(Bukkit.getOnlinePlayers()));

    /** Everyone online except the triggering player. */
    public static final Target OTHERS = new Target("others", context -> online()
            .filter(player -> !player.equals(context.player()))
            .toList());

    private final String raw;
    private final Function<ActionContext, List<Player>> resolver;

    private Target(final String raw, final Function<ActionContext, List<Player>> resolver) {
        this.raw = raw;
        this.resolver = resolver;
    }

    /**
     * Parses a configured selector.
     *
     * <p>Accepts {@code self}, {@code all}, {@code others}, {@code world:<name>},
     * {@code radius:<blocks>} and {@code permission:<node>}, plus the legacy
     * {@code player} and {@code global}. An unknown selector warns and falls back to
     * {@code self}, so a typo costs one action rather than the whole config.</p>
     *
     * @param raw the configured value; null or blank means {@code self}
     * @return the target
     */
    public static Target parse(final String raw) {

        if (raw == null || raw.isBlank()) {
            return SELF;
        }

        String trimmed = raw.trim();
        String value = trimmed.toLowerCase(Locale.ROOT);

        int colon = value.indexOf(':');

        if (colon > 0) {

            String argument = value.substring(colon + 1).trim();

            switch (value.substring(0, colon).trim()) {

                case "world" -> {
                    return new Target(trimmed, context -> online()
                            .filter(player -> player.getWorld().getName().equalsIgnoreCase(argument))
                            .toList());
                }

                case "radius" -> {
                    return radius(trimmed, argument);
                }

                case "permission", "perm" -> {
                    return new Target(trimmed, context -> online()
                            .filter(player -> player.hasPermission(argument))
                            .toList());
                }

                default -> {
                }
            }
        }

        return switch (value) {
            case "self", "player" -> SELF;
            case "all", "global" -> ALL;
            case "others" -> OTHERS;
            default -> {
                Actions.warn("Unknown target '" + trimmed + "'; using 'self'. "
                        + "Valid: self, all, others, world:<name>, radius:<blocks>, permission:<node>");
                yield SELF;
            }
        };
    }

    private static Target radius(final String raw, final String argument) {

        double blocks;

        try {
            blocks = Double.parseDouble(argument);

        } catch (NumberFormatException e) {
            Actions.warn("Target '" + raw + "' needs a number of blocks; using 'self'.");
            return SELF;
        }

        double squared = blocks * blocks;

        return new Target(raw, context -> {

            Player source = context.player();

            if (source == null) {
                return List.of();
            }

            return online()
                    .filter(player -> player.getWorld().equals(source.getWorld()))
                    .filter(player -> player.getLocation().distanceSquared(source.getLocation()) <= squared)
                    .toList();
        });
    }

    private static java.util.stream.Stream<Player> online() {
        return Bukkit.getOnlinePlayers().stream().map(Player.class::cast);
    }

    /**
     * Works out who this action affects.
     *
     * @param context the run's context
     * @return the players, possibly empty, never null
     */
    public List<Player> resolve(final ActionContext context) {
        return resolver.apply(context);
    }

    /**
     * @return the text this was parsed from, for writing back to config
     */
    public String raw() {
        return raw;
    }

    @Override
    public String toString() {
        return raw;
    }
}
