package top.vulpine.actions.action;

import eu.okaeri.configs.serdes.DeserializationData;
import top.vulpine.actions.Actions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maps a {@code type} id to the code that builds that action.
 *
 * <p>Adding an action is a registration rather than an edit to a switch, so a
 * consuming plugin can add its own vocabulary without forking the library.</p>
 */
public final class ActionRegistry {

    private final Map<String, Entry> entries = new HashMap<>();

    /**
     * Builds an action from a config block.
     */
    @FunctionalInterface
    public interface FromConfig {

        /**
         * @param data the block's keys
         * @return the action
         */
        Action read(DeserializationData data);
    }

    /**
     * Builds an action from the text after {@code [type]} in a one-liner.
     */
    @FunctionalInterface
    public interface FromShorthand {

        /**
         * @param params the text after the tag, trimmed
         * @param raw the whole original line, to be reported by {@link Action#shorthand()}
         * @return the action
         */
        Action parse(String params, String raw);
    }

    /**
     * Registers an action that can only be written as a block.
     *
     * @param type the id; case insensitive
     * @param fromConfig builds it from a block
     */
    public void register(final String type, final FromConfig fromConfig) {
        register(type, fromConfig, null);
    }

    /**
     * Registers an action, optionally with a one-liner form.
     *
     * @param type the id; case insensitive
     * @param fromConfig builds it from a block
     * @param fromShorthand builds it from a one-liner, or null if it has none
     */
    public void register(final String type, final FromConfig fromConfig, final FromShorthand fromShorthand) {

        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fromConfig, "fromConfig");

        entries.put(type.toLowerCase(Locale.ROOT), new Entry(fromConfig, fromShorthand));
    }

    /**
     * @param type the id; case insensitive
     * @return true if something is registered under it
     */
    public boolean has(final String type) {
        return type != null && entries.containsKey(type.toLowerCase(Locale.ROOT));
    }

    /**
     * @return every registered id, sorted, for error messages and tab completion
     */
    public Set<String> types() {
        return new TreeSet<>(entries.keySet());
    }

    /**
     * Builds an action from a config block.
     *
     * @param type the id
     * @param data the block's keys
     * @return the action, or null if the type is unknown or it failed to build
     */
    public Action read(final String type, final DeserializationData data) {

        if (type == null || type.isBlank()) {
            Actions.warn("Action block has no 'type'. Known types: " + types());
            return null;
        }

        Entry entry = entries.get(type.toLowerCase(Locale.ROOT));

        if (entry == null) {
            Actions.warn("Unknown action type '" + type + "'. Known types: " + types());
            return null;
        }

        try {
            return entry.fromConfig.read(data);

        } catch (Exception e) {
            Actions.warn("Could not read action '" + type + "': " + e);
            return null;
        }
    }

    /**
     * Builds an action from a one-liner such as {@code "[message] self; hello"}.
     *
     * @param raw the whole line
     * @return the action, or null if it is malformed or the type is unknown
     */
    public Action parse(final String raw) {

        if (raw == null) {
            return null;
        }

        String line = raw.trim();

        if (!line.startsWith("[")) {
            Actions.warn("Action one-liner must start with '[type]': " + line);
            return null;
        }

        int close = line.indexOf(']');

        if (close < 0) {
            Actions.warn("Action one-liner is missing the closing ']': " + line);
            return null;
        }

        String type = line.substring(1, close).trim().toLowerCase(Locale.ROOT);
        String params = line.substring(close + 1).trim();

        Entry entry = entries.get(type);

        if (entry == null) {
            Actions.warn("Unknown action type '" + type + "'. Known types: " + types());
            return null;
        }

        if (entry.fromShorthand == null) {
            Actions.warn("Action '" + type + "' has no one-liner form; write it as a block.");
            return null;
        }

        try {
            return entry.fromShorthand.parse(params, line);

        } catch (Exception e) {
            Actions.warn("Could not parse action one-liner '" + line + "': " + e);
            return null;
        }
    }

    private record Entry(FromConfig fromConfig, FromShorthand fromShorthand) {
    }
}
