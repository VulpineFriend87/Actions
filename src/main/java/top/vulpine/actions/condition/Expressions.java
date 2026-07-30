package top.vulpine.actions.condition;

import org.bukkit.entity.Player;
import top.vulpine.actions.Actions;
import top.vulpine.actions.action.ActionContext;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles a single condition expression into a predicate.
 *
 * <p>Two shapes are accepted: a comparison ({@code %player_level% >= 10}) and a
 * named check ({@code permission: lobby.vip}).</p>
 */
final class Expressions {

    /** A leftover %placeholder% after expansion, i.e. one nothing resolved. */
    private static final Pattern UNRESOLVED = Pattern.compile("%[a-zA-Z0-9_]+%");

    /** Longest first, so {@code >=} is not read as {@code >}. */
    private static final String[] SYMBOLS = {">=", "<=", "!=", "==", ">", "<"};

    /** Padded so they cannot match inside an operand. */
    private static final String[] WORDS = {" contains ", " starts_with ", " ends_with ", " matches "};

    private Expressions() {
    }

    static Predicate<ActionContext> compile(final String raw) {

        if (raw == null || raw.isBlank()) {
            Actions.warn("Empty condition; treating as false.");
            return context -> false;
        }

        String text = raw.trim();

        Predicate<ActionContext> named = named(text);

        if (named != null) {
            return named;
        }

        return comparison(text, raw);
    }

    // --- named checks --------------------------------------------------------

    private static Predicate<ActionContext> named(final String text) {

        int colon = text.indexOf(':');

        if (colon <= 0) {
            return null;
        }

        String name = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String argument = text.substring(colon + 1).trim();

        return switch (name) {

            case "permission", "perm" -> context -> {
                Player player = context.player();
                return player != null && player.hasPermission(argument);
            };

            case "world" -> context -> {
                Player player = context.player();
                return player != null && player.getWorld().getName().equalsIgnoreCase(argument);
            };

            case "gamemode" -> context -> {
                Player player = context.player();
                return player != null && player.getGameMode().name().equalsIgnoreCase(argument);
            };

            case "sneaking" -> {
                boolean expected = Boolean.parseBoolean(argument);
                yield context -> {
                    Player player = context.player();
                    return player != null && player.isSneaking() == expected;
                };
            }

            case "chance" -> chance(argument);

            // Not a known name: it is probably a comparison whose operand contains a
            // colon, so fall through rather than claiming it.
            default -> null;
        };
    }

    private static Predicate<ActionContext> chance(final String argument) {

        String value = argument.endsWith("%")
                ? argument.substring(0, argument.length() - 1).trim()
                : argument.trim();

        double probability;

        try {
            probability = Double.parseDouble(value);

        } catch (NumberFormatException e) {
            Actions.warn("chance: '" + argument + "' is not a number; treating as false.");
            return context -> false;
        }

        // Both "25%" and "0.25" read naturally, so accept either.
        double fraction = argument.endsWith("%") ? probability / 100D : probability;

        return context -> java.util.concurrent.ThreadLocalRandom.current().nextDouble() < fraction;
    }

    // --- comparisons ---------------------------------------------------------

    private static Predicate<ActionContext> comparison(final String text, final String raw) {

        for (String word : WORDS) {

            int at = text.toLowerCase(Locale.ROOT).indexOf(word);

            if (at > 0) {
                return build(text.substring(0, at), word.trim(), text.substring(at + word.length()), raw);
            }
        }

        for (String symbol : SYMBOLS) {

            int at = text.indexOf(symbol);

            if (at > 0) {
                return build(text.substring(0, at), symbol, text.substring(at + symbol.length()), raw);
            }
        }

        Actions.warn("Could not read condition '" + raw + "'. Expected something like "
                + "'%placeholder% >= 10' or 'permission: node'.");

        return context -> false;
    }

    private static Predicate<ActionContext> build(final String leftRaw, final String operator,
                                                  final String rightRaw, final String raw) {

        String left = leftRaw.trim();
        String right = rightRaw.trim();

        boolean ordering = operator.equals(">") || operator.equals("<")
                || operator.equals(">=") || operator.equals("<=");

        if (operator.equals("matches")) {
            return regex(left, right, raw);
        }

        return context -> {

            String a = resolve(left, context, raw);
            String b = resolve(right, context, raw);

            if (a == null || b == null) {
                return false;
            }

            Double x = number(a);
            Double y = number(b);

            if (x != null && y != null) {
                int compared = Double.compare(x, y);
                return switch (operator) {
                    case "==" -> compared == 0;
                    case "!=" -> compared != 0;
                    case ">" -> compared > 0;
                    case ">=" -> compared >= 0;
                    case "<" -> compared < 0;
                    case "<=" -> compared <= 0;
                    default -> textual(a, b, operator);
                };
            }

            if (ordering) {
                // Comparing non-numbers with < or > sorts alphabetically, which is
                // almost never what an operator meant by "greater than".
                Actions.warnOnce("Condition '" + raw + "' compares non-numeric values with '"
                        + operator + "'; treating as false. Did a placeholder fail to resolve?");
                return false;
            }

            return textual(a, b, operator);
        };
    }

    private static Predicate<ActionContext> regex(final String left, final String right, final String raw) {

        Pattern pattern;

        try {
            pattern = Pattern.compile(right);

        } catch (PatternSyntaxException e) {
            Actions.warn("Condition '" + raw + "' has an invalid regex: " + e.getDescription());
            return context -> false;
        }

        return context -> {
            String value = resolve(left, context, raw);
            return value != null && pattern.matcher(value).matches();
        };
    }

    private static boolean textual(final String a, final String b, final String operator) {
        return switch (operator) {
            case "==" -> a.equalsIgnoreCase(b);
            case "!=" -> !a.equalsIgnoreCase(b);
            case "contains" -> a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
            case "starts_with" -> a.toLowerCase(Locale.ROOT).startsWith(b.toLowerCase(Locale.ROOT));
            case "ends_with" -> a.toLowerCase(Locale.ROOT).endsWith(b.toLowerCase(Locale.ROOT));
            default -> false;
        };
    }

    /**
     * Expands an operand, or returns null if a placeholder in it did not resolve.
     *
     * <p>Without this check an unresolved {@code %player_level%} is compared as the
     * literal text, so the condition is quietly false forever — the most expensive
     * kind of bug, because nothing reports it. Usually it means PlaceholderAPI is
     * missing or the expansion is not installed.</p>
     */
    private static String resolve(final String operand, final ActionContext context, final String raw) {

        String expanded = context.expandRaw(operand);

        if (expanded != null && UNRESOLVED.matcher(expanded).find()) {
            Actions.warnOnce("Condition '" + raw + "' has an unresolved placeholder ('"
                    + expanded + "'); treating as false. Is PlaceholderAPI installed "
                    + "and the expansion downloaded?");
            return null;
        }

        return expanded;
    }

    private static Double number(final String value) {

        try {
            return Double.valueOf(value);

        } catch (NumberFormatException e) {
            return null;
        }
    }
}
