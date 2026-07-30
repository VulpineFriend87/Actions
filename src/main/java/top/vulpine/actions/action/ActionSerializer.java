package top.vulpine.actions.action;

import eu.okaeri.configs.schema.GenericsDeclaration;
import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.ObjectSerializer;
import eu.okaeri.configs.serdes.SerializationData;

import java.util.Objects;

/**
 * Reads and writes {@link Action} in either of its two config forms.
 *
 * <h2>How one serializer handles both</h2>
 * <p>When okaeri resolves a value into a type that has a serializer, it branches on
 * the shape of the YAML node: a mapping is handed over as-is, and anything scalar is
 * wrapped as a single-entry map under a reserved key first. Both land here, and
 * {@link DeserializationData#isValue()} distinguishes them. Writing is symmetric —
 * {@link SerializationData#setValue(Object)} produces a plain scalar again.</p>
 *
 * <p>That is what lets a list mix the two forms, and it is why upgrading a config
 * costs nothing: every one-liner written against the old format still loads, and
 * still gets written back as a one-liner.</p>
 *
 * <h2>Unreadable entries are kept, not dropped</h2>
 * <p>A malformed or unknown action is warned about by the registry and becomes an
 * {@link UnknownAction} holding the original text. The rest of the config loads.
 * Throwing here would be tidier to reason about and much worse to operate: one bad
 * line would take down the plugin. Returning null is worse still — okaeri leaves the
 * null in the list rather than removing it, and saving would then erase whatever the
 * operator had written.</p>
 */
public final class ActionSerializer implements ObjectSerializer<Action> {

    private static final String TYPE_KEY = "type";

    private final ActionRegistry registry;
    private final boolean migrate;

    /**
     * @param registry where types are looked up
     * @param migrate true to write one-liners back as blocks, upgrading the file in
     *        place the next time it is saved
     */
    public ActionSerializer(final ActionRegistry registry, final boolean migrate) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.migrate = migrate;
    }

    @Override
    public boolean supports(final Class<? super Action> type) {
        return Action.class.isAssignableFrom(type);
    }

    @Override
    public void serialize(final Action action, final SerializationData data, final GenericsDeclaration generics) {

        String shorthand = action.shorthand();

        // Migration is exactly this: stop honouring the remembered one-liner, and the
        // next save writes the block form instead. Nothing else has to know.
        if (shorthand != null && !migrate) {
            data.setValue(shorthand);
            return;
        }

        data.add(TYPE_KEY, action.type());
        action.write(data);
    }

    @Override
    public Action deserialize(final DeserializationData data, final GenericsDeclaration generics) {

        if (data.isValue()) {

            Object raw = data.getValueRaw();
            String line = raw == null ? "" : String.valueOf(raw);
            Action action = registry.parse(line);

            return action == null ? UnknownAction.ofShorthand(line) : action;
        }

        String type = data.containsKey(TYPE_KEY) ? data.get(TYPE_KEY, String.class) : null;
        Action action = registry.read(type, data);

        return action == null ? UnknownAction.ofBlock(type, data.asMap()) : action;
    }
}
