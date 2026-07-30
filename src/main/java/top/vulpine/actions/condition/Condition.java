package top.vulpine.actions.condition;

import top.vulpine.actions.Actions;
import top.vulpine.actions.action.ActionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A yes/no question asked of a run's context.
 *
 * <h2>Compiled once</h2>
 * <p>The configured text is turned into a tree of predicates when the config loads,
 * not when the condition is asked. At runtime only the operands are expanded and
 * compared — nothing is re-tokenised per player per join.</p>
 *
 * <h2>Keeps its source</h2>
 * <p>Like {@link top.vulpine.actions.target.Target}, a condition holds the config
 * node it came from. Predicates are lambdas and cannot describe themselves, so
 * without the original node saving the config would rewrite every condition into
 * something else — or lose it.</p>
 *
 * <h2>Shape</h2>
 * <pre>{@code
 * condition: "%player_level% >= 10"        # one expression
 * condition: [ A, B ]                      # a list means all of them
 * condition:
 *   all: [ A, B ]                          # every one true
 *   any: [ A, B ]                          # at least one true
 *   not: A                                 # A false
 * }</pre>
 *
 * <p>Several keys in one block combine with <strong>AND</strong>, so
 * {@code any: [...]} beside {@code not: X} reads as "(one of those) and (not X)".
 * Every element of {@code all} and {@code any} may itself be a block, so nesting is
 * unlimited.</p>
 */
public final class Condition {

    /** Always true — what an absent condition means. */
    public static final Condition ALWAYS = new Condition(null, context -> true);

    private final Object source;
    private final Predicate<ActionContext> predicate;

    private Condition(final Object source, final Predicate<ActionContext> predicate) {
        this.source = source;
        this.predicate = predicate;
    }

    /**
     * Compiles a configured condition node.
     *
     * @param node a String, a List, or a Map of {@code all}/{@code any}/{@code not};
     *        null means {@link #ALWAYS}
     * @return the condition
     */
    public static Condition parse(final Object node) {

        if (node == null) {
            return ALWAYS;
        }

        return new Condition(node, compile(node));
    }

    /**
     * @param context the run's context
     * @return whether the condition holds
     */
    public boolean test(final ActionContext context) {
        return predicate.test(context);
    }

    /**
     * @return the config node this was compiled from, for writing back; null for
     *         {@link #ALWAYS}
     */
    public Object source() {
        return source;
    }

    // --- compilation ---------------------------------------------------------

    private static Predicate<ActionContext> compile(final Object node) {

        if (node instanceof String text) {
            return Expressions.compile(text);
        }

        if (node instanceof List<?> list) {
            return and(compileEach(list));
        }

        if (node instanceof Map<?, ?> map) {
            return compileBlock(map);
        }

        Actions.warn("Condition must be text, a list, or a block of all/any/not, got: " + node);
        return context -> false;
    }

    private static Predicate<ActionContext> compileBlock(final Map<?, ?> map) {

        List<Predicate<ActionContext>> parts = new ArrayList<>();

        for (Map.Entry<?, ?> entry : map.entrySet()) {

            String key = String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
            Object value = entry.getValue();

            switch (key) {

                case "all" -> parts.add(and(compileEach(asList(value, "all"))));

                case "any" -> parts.add(or(compileEach(asList(value, "any"))));

                case "not" -> {

                    if (value instanceof List) {
                        // "none of these" and "not all of these" both read naturally
                        // from a list, so the author has to say which one they mean.
                        Actions.warn("'not' takes a single condition, not a list. "
                                + "Wrap it: not: { any: [...] } for 'none of these'.");
                        parts.add(context -> false);
                        break;
                    }

                    Predicate<ActionContext> inner = compile(value);
                    parts.add(context -> !inner.test(context));
                }

                default -> Actions.warn("Unknown condition key '" + entry.getKey()
                        + "'. Valid: all, any, not.");
            }
        }

        if (parts.isEmpty()) {
            Actions.warn("Empty condition block; treating as false.");
            return context -> false;
        }

        // Keys sitting side by side are ANDed — the reading everyone expects, and it
        // keeps the common case one level shallower than nesting under 'all'.
        return and(parts);
    }

    private static List<?> asList(final Object value, final String key) {

        if (value instanceof List<?> list) {
            return list;
        }

        if (value == null) {
            Actions.warn("'" + key + "' has no entries.");
            return List.of();
        }

        // A single entry under all/any is harmless and reads fine.
        return List.of(value);
    }

    private static List<Predicate<ActionContext>> compileEach(final List<?> nodes) {

        List<Predicate<ActionContext>> compiled = new ArrayList<>(nodes.size());

        for (Object node : nodes) {
            compiled.add(compile(node));
        }

        return compiled;
    }

    private static Predicate<ActionContext> and(final List<Predicate<ActionContext>> parts) {

        if (parts.isEmpty()) {
            return context -> false;
        }

        if (parts.size() == 1) {
            return parts.get(0);
        }

        List<Predicate<ActionContext>> copy = List.copyOf(parts);

        return context -> {
            for (Predicate<ActionContext> part : copy) {
                if (!part.test(context)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static Predicate<ActionContext> or(final List<Predicate<ActionContext>> parts) {

        if (parts.isEmpty()) {
            return context -> false;
        }

        if (parts.size() == 1) {
            return parts.get(0);
        }

        List<Predicate<ActionContext>> copy = List.copyOf(parts);

        return context -> {
            for (Predicate<ActionContext> part : copy) {
                if (part.test(context)) {
                    return true;
                }
            }
            return false;
        };
    }
}
