package top.vulpine.actions.action;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Named action lists that can be invoked from anywhere, so a sequence written once is
 * shared instead of copied into every event that needs it.
 *
 * <pre>{@code
 * # actions.yml
 * sequences:
 *   vip_welcome:
 *     - "[message] self; <gold>Benvenuto VIP"
 *     - type: sound
 *       key: "entity.player.levelup"
 * }</pre>
 *
 * <p>Lookup happens when the action runs, not when the config loads: a sequence may
 * be defined after the one that calls it, or in a different file, and refusing a
 * forward reference would make the order of keys in a config meaningful.</p>
 */
public final class SequenceRegistry {

    private final Map<String, List<Action>> sequences = new HashMap<>();

    /**
     * Registers or replaces a named sequence.
     *
     * @param name the name; case insensitive
     * @param actions the actions
     */
    public void put(final String name, final List<Action> actions) {

        if (name == null || name.isBlank()) {
            return;
        }

        sequences.put(name.toLowerCase(Locale.ROOT), List.copyOf(actions));
    }

    /**
     * Registers every entry of a map, as read from config.
     *
     * @param entries name to actions
     */
    public void putAll(final Map<String, List<Action>> entries) {

        if (entries != null) {
            entries.forEach(this::put);
        }
    }

    /**
     * @param name the name; case insensitive
     * @return the actions, or null if nothing is registered under it
     */
    public List<Action> get(final String name) {
        return name == null ? null : sequences.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * @return every registered name, sorted, for error messages
     */
    public Set<String> names() {
        return new TreeSet<>(sequences.keySet());
    }

    /**
     * @return how many sequences are registered
     */
    public int size() {
        return sequences.size();
    }
}
