package top.vulpine.actions.action;

import org.bukkit.entity.Player;
import top.vulpine.actions.Actions;
import top.vulpine.actions.scheduler.ActionScheduler;
import top.vulpine.commons.text.Colorize;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who and what a run of actions is for: the triggering player, the scheduler, and
 * a bag of {@code %placeholder%} values.
 *
 * <h2>Escaping happens at substitution, not at storage</h2>
 * <p>A value going into a chat line must be made literal first: a nickname holding
 * {@code <red>} would otherwise restyle the message, and one holding a
 * {@code <click:run_command:…>} tag would turn it into something that runs a command
 * when clicked. But the same escaping is wrong in a command or a comparison, where
 * {@code <} must stay {@code <}. So values are kept as given, and
 * {@link #expand(String)} escapes while {@link #expandRaw(String)} does not.</p>
 *
 * <p>PlaceholderAPI output is not escaped either way, because PAPI expands a whole
 * string at once and there is no seam at which to escape only the substituted parts.
 * Treat PAPI as trusted; treat anything a player can set as not.</p>
 */
public final class ActionContext {

    private final Player player;
    private final ActionScheduler scheduler;
    private final Map<String, String> values;
    private final SequenceRegistry sequences;

    /**
     * Values written by {@code set} while the list runs.
     *
     * <p>Separate from the fixed values, and concurrent: a run that delays resumes on
     * whichever thread or region the scheduler hands it back on, so a variable set
     * before the wait is read after it from somewhere else.</p>
     */
    private final Map<String, String> variables = new ConcurrentHashMap<>();

    private ActionContext(final Builder builder) {
        this.player = builder.player;
        this.scheduler = builder.scheduler;
        this.values = Map.copyOf(builder.values);
        this.sequences = builder.sequences;
    }

    /**
     * @param scheduler how delayed and region-bound work is dispatched
     * @return a builder
     */
    public static Builder builder(final ActionScheduler scheduler) {
        return new Builder(scheduler);
    }

    /**
     * @return the triggering player, or null for a run with no player
     */
    public Player player() {
        return player;
    }

    /**
     * @return the scheduler
     */
    public ActionScheduler scheduler() {
        return scheduler;
    }

    /**
     * @return the placeholder values, already escaped, keyed without percent signs
     */
    public Map<String, String> values() {
        return values;
    }

    /**
     * Expands every known {@code %name%} in a template.
     *
     * @param template the raw string; may be null
     * @return the expanded string, or null if the template was null
     */
    public String expand(final String template) {
        return substitute(template, true);
    }

    /**
     * Expands every known {@code %name%} <em>without</em> escaping.
     *
     * <p>For anything that is not about to be parsed as MiniMessage: commands and
     * condition operands. Escaping there would be actively wrong — a value containing
     * {@code <} becomes {@code \<}, which is right in a chat line and nonsense in a
     * command or a string comparison.</p>
     *
     * @param template the raw string; may be null
     * @return the expanded string, or null if the template was null
     */
    public String expandRaw(final String template) {
        return substitute(template, false);
    }

    /**
     * @return the sequence registry for this run, or null if none was given
     */
    public SequenceRegistry sequences() {
        return sequences;
    }

    /**
     * Sets a variable readable as {@code %name%} by later actions in this run.
     *
     * @param key the name, without percent signs
     * @param value the value; null clears it
     */
    public void set(final String key, final String value) {

        if (key == null) {
            return;
        }

        if (value == null) {
            variables.remove(key);
        } else {
            variables.put(key, value);
        }
    }

    /**
     * @return the variables set during this run
     */
    public Map<String, String> variables() {
        return Map.copyOf(variables);
    }

    private String substitute(final String template, final boolean escape) {

        if (template == null || template.isEmpty()) {
            return template;
        }

        String result = template;

        // Variables first, so a value set during the run wins over the fixed one it
        // shadows — otherwise 'set' could never override anything.
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = escape ? Colorize.escape(entry.getValue()) : entry.getValue();
            result = result.replace('%' + entry.getKey() + '%', value);
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = escape ? Colorize.escape(entry.getValue()) : entry.getValue();
            result = result.replace('%' + entry.getKey() + '%', value);
        }

        // The context's own values win, then whatever the plugin registered — so a
        // value set here is never overwritten by an external expansion.
        return Actions.expand(player, result);
    }

    /**
     * Derives a context carrying additional values, leaving this one untouched.
     *
     * @param extra values to add, keyed without percent signs
     * @return the derived context
     */
    public ActionContext with(final Map<String, String> extra) {

        Builder builder = new Builder(scheduler).player(player).sequences(sequences);
        builder.values.putAll(values);

        if (extra != null) {
            extra.forEach(builder::value);
        }

        return builder.build();
    }

    /**
     * Collects the parts of a context.
     */
    public static final class Builder {

        private final ActionScheduler scheduler;
        private final Map<String, String> values = new LinkedHashMap<>();
        private Player player;
        private SequenceRegistry sequences;

        private Builder(final ActionScheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        }

        /**
         * Makes named sequences available to the {@code run} action.
         *
         * @param registry the registry; may be null if nothing uses {@code run}
         * @return this builder
         */
        public Builder sequences(final SequenceRegistry registry) {
            this.sequences = registry;
            return this;
        }

        /**
         * @param value the triggering player; may be null
         * @return this builder
         */
        public Builder player(final Player value) {
            this.player = value;
            return this;
        }

        /**
         * Adds a placeholder value.
         *
         * <p>Stored as given. Escaping happens at substitution time, because whether
         * it is correct depends on where the value is going: escaped for text bound
         * for MiniMessage, raw for commands and comparisons.</p>
         *
         * @param key the name, without percent signs
         * @param value the value; null becomes an empty string
         * @return this builder
         */
        public Builder value(final String key, final Object value) {
            values.put(key, value == null ? "" : String.valueOf(value));
            return this;
        }

        /**
         * @return the context
         */
        public ActionContext build() {
            return new ActionContext(this);
        }
    }
}
