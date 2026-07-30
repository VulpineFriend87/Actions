package top.vulpine.actions.action;

import eu.okaeri.configs.serdes.SerializationData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stands in for a config entry that could not be understood, keeping the original
 * text so it survives a save.
 *
 * <h2>Why not just drop it</h2>
 * <p>A misspelled type is almost always a typo in something the operator meant to
 * work. Dropping the entry would make the plugin load cleanly and then silently
 * delete their line the next time the config is written — they would lose the work
 * and have no idea why. Keeping it means the warning repeats every start until it is
 * fixed, which is the point.</p>
 *
 * <h2>Why not null</h2>
 * <p>okaeri leaves a null in the list rather than removing it, so returning null
 * hands every consumer a {@code List<Action>} that can throw on iteration. This does
 * nothing at runtime instead.</p>
 */
public final class UnknownAction implements Action {

    private final String type;
    private final Map<String, Object> keys;
    private final String shorthand;

    private UnknownAction(final String type, final Map<String, Object> keys, final String shorthand) {
        this.type = type;
        this.keys = keys;
        this.shorthand = shorthand;
    }

    /**
     * Preserves an unreadable one-liner.
     *
     * @param raw the original line
     * @return the placeholder
     */
    public static UnknownAction ofShorthand(final String raw) {
        return new UnknownAction("unknown", Map.of(), raw);
    }

    /**
     * Preserves an unreadable block.
     *
     * @param type the {@code type} value as written, which may be null or unknown
     * @param keys every key of the original block
     * @return the placeholder
     */
    public static UnknownAction ofBlock(final String type, final Map<String, Object> keys) {
        return new UnknownAction(type == null ? "unknown" : type, new LinkedHashMap<>(keys), null);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public Flow execute(final ActionContext context) {
        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {

        // Written back byte for byte, minus the type key the serializer adds itself.
        keys.forEach((key, value) -> {
            if (!"type".equals(key)) {
                data.addRaw(key, value);
            }
        });
    }

    @Override
    public String shorthand() {
        return shorthand;
    }
}
