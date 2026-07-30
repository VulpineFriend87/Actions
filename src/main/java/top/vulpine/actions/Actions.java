package top.vulpine.actions;

import org.bukkit.entity.Player;
import top.vulpine.commons.log.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Where the library reports problems, and where a plugin plugs in placeholder
 * expansion.
 *
 * <p>Bad configuration is warned about and skipped rather than thrown, because the
 * alternative is one typo in one action stopping a plugin from loading. The sink is
 * replaceable so the library can be exercised without a running server, and so a
 * consuming plugin can route messages through its own prefix.</p>
 */
public final class Actions {

    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private static volatile Consumer<String> sink = Logger::warn;
    private static volatile BiFunction<Player, String, String> placeholders = (player, text) -> text;

    private Actions() {
    }

    /**
     * Redirects warnings.
     *
     * @param consumer where to send them; null restores the default
     */
    public static void logger(final Consumer<String> consumer) {
        sink = consumer == null ? Logger::warn : consumer;
        SEEN.clear();
    }

    /**
     * Registers external placeholder expansion, applied after the context's own
     * values.
     *
     * <p>Kept as a hook so the library does not depend on PlaceholderAPI. A plugin
     * that has it wires it up in one line:</p>
     *
     * <pre>{@code
     * Actions.placeholders(PlaceholderAPI::setPlaceholders);
     * }</pre>
     *
     * <p>Values expanded this way are <strong>not</strong> escaped — PAPI expands a
     * whole string at once, so there is no seam at which to escape only what it
     * substituted. Treat its output as trusted; treat anything a player can set as
     * not.</p>
     *
     * @param resolver takes the player (may be null) and the template; null restores
     *        the no-op
     */
    public static void placeholders(final BiFunction<Player, String, String> resolver) {
        placeholders = resolver == null ? (player, text) -> text : resolver;
    }

    /**
     * Applies the registered placeholder expansion.
     *
     * @param player the player, may be null
     * @param text the template
     * @return the expanded text
     */
    public static String expand(final Player player, final String text) {

        if (text == null || text.isEmpty()) {
            return text;
        }

        try {
            return placeholders.apply(player, text);

        } catch (Exception e) {
            warnOnce("Placeholder expansion failed: " + e);
            return text;
        }
    }

    /**
     * Reports a configuration or execution problem.
     *
     * @param message the message
     */
    public static void warn(final String message) {
        sink.accept(message);
    }

    /**
     * Reports a problem the first time only.
     *
     * <p>For things asked per player per event: an unresolved placeholder in a join
     * condition would otherwise print on every join forever, which buries the one
     * line that matters under thousands of copies of itself.</p>
     *
     * @param message the message
     */
    public static void warnOnce(final String message) {

        if (SEEN.add(message)) {
            sink.accept(message);
        }
    }
}
