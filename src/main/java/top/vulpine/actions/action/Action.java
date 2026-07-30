package top.vulpine.actions.action;

import eu.okaeri.configs.serdes.SerializationData;

/**
 * One configured thing to do.
 *
 * <p>Actions are built once when the config loads and executed many times, so
 * anything derivable from configuration — parsed durations, compiled conditions,
 * resolved targets — belongs in the constructor rather than in
 * {@link #execute(ActionContext)}.</p>
 *
 * <h2>Two config forms</h2>
 * <p>The same action can be written either as a block or as a one-liner:</p>
 * <pre>{@code
 * - type: message
 *   target: self
 *   text: "<green>Welcome"
 *
 * - "[message] self; <green>Welcome"
 * }</pre>
 * <p>An action created from the second form reports it via {@link #shorthand()} so
 * that saving the config writes the one-liner back, instead of quietly expanding
 * every one of a user's terse lines into a block.</p>
 */
public interface Action {

    /**
     * The registered id, written as the {@code type} key.
     *
     * @return the id, lowercase
     */
    String type();

    /**
     * Runs the action.
     *
     * @param context who and what this run is for
     * @return what the executor should do next
     */
    Flow execute(ActionContext context);

    /**
     * Writes this action's own keys. The executor writes {@code type} itself, so
     * implementations must not.
     *
     * <p>Only called when {@link #shorthand()} is null.</p>
     *
     * @param data the target
     */
    void write(SerializationData data);

    /**
     * The one-liner this action was parsed from, if it was.
     *
     * @return the original text, or null if it came from a block
     */
    default String shorthand() {
        return null;
    }
}
