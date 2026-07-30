package top.vulpine.actions.action;

import eu.okaeri.configs.schema.GenericsDeclaration;
import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.ObjectSerializer;
import eu.okaeri.configs.serdes.SerializationData;
import top.vulpine.actions.Actions;

import java.util.List;

/**
 * One weighted alternative: a list of actions and how likely it is to be picked.
 *
 * <pre>{@code
 * - weight: 3
 *   actions:
 *     - "[message] self; comune"
 * }</pre>
 *
 * <p>Lives in the core rather than beside the action that uses it, so
 * {@link ActionSerdes} can register its serializer without the core having to import
 * the implementations.</p>
 */
public final class Choice {

    private final int weight;
    private final List<Action> actions;

    /**
     * @param weight how likely, relative to the other choices; at least 1
     * @param actions what to run
     */
    public Choice(final int weight, final List<Action> actions) {
        this.weight = Math.max(1, weight);
        this.actions = List.copyOf(actions);
    }

    /**
     * @return the relative weight
     */
    public int weight() {
        return weight;
    }

    /**
     * @return the actions
     */
    public List<Action> actions() {
        return actions;
    }

    /**
     * Reads and writes a {@link Choice}.
     */
    public static final class Serializer implements ObjectSerializer<Choice> {

        @Override
        public boolean supports(final Class<? super Choice> type) {
            return Choice.class.isAssignableFrom(type);
        }

        @Override
        public void serialize(final Choice choice, final SerializationData data,
                              final GenericsDeclaration generics) {

            data.add("weight", choice.weight());
            data.addCollection("actions", choice.actions(), Action.class);
        }

        @Override
        public Choice deserialize(final DeserializationData data, final GenericsDeclaration generics) {

            int weight = 1;

            if (data.containsKey("weight")) {

                try {
                    weight = Integer.parseInt(String.valueOf(data.getRaw("weight")).trim());

                } catch (NumberFormatException e) {
                    Actions.warn("Choice weight '" + data.getRaw("weight")
                            + "' is not a whole number; using 1.");
                }
            }

            List<Action> actions = data.containsKey("actions")
                    ? List.copyOf(data.getAsList("actions", Action.class))
                    : List.of();

            if (actions.isEmpty()) {
                Actions.warn("A random choice has no actions; picking it will do nothing.");
            }

            return new Choice(weight, actions);
        }
    }
}
