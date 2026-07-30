package top.vulpine.actions.action;

import top.vulpine.actions.Actions;

import java.util.Locale;

/**
 * Parses configured durations into ticks.
 *
 * <p>Accepts {@code 20t}, {@code 500ms}, {@code 3s} and {@code 2m}. A bare number is
 * read as milliseconds, which is what the original format meant, so existing
 * configs keep their timing.</p>
 *
 * <p>The original code did {@code milliseconds / 50} with integer division, so
 * anything under 50ms silently became no delay at all. Here a positive duration
 * always waits at least one tick — 20ms cannot be honored exactly, but rounding it
 * to "no delay" is the more surprising of the two answers.</p>
 */
public final class Ticks {

    private Ticks() {
    }

    /**
     * Like {@link #parse}, but a bare number means <strong>ticks</strong>.
     *
     * <p>The two entry points exist because the old format was inconsistent:
     * {@code [DELAY] 200} was milliseconds, while {@code [TITLE] …; 20; 60; 20} was
     * ticks, because those numbers went straight to {@code sendTitle}. Reading a bare
     * number the same way everywhere would silently retime every existing config —
     * either titles 20× too fast or delays 20× too slow.</p>
     *
     * @param raw the configured duration
     * @param fallback what to return when it cannot be read
     * @return the duration in ticks, never negative
     */
    public static long parseTicks(final String raw, final long fallback) {

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        String value = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");

        // A unit was given, so the shared parser already knows what to do with it.
        if (value.endsWith("ms") || value.endsWith("t") || value.endsWith("s") || value.endsWith("m")) {
            return parse(value, fallback);
        }

        try {
            double amount = Double.parseDouble(value);
            return amount <= 0D ? 0L : Math.max(1L, Math.round(amount));

        } catch (NumberFormatException e) {
            Actions.warn("Could not read duration '" + raw + "'; expected 20t, 500ms, 3s or 2m.");
            return fallback;
        }
    }

    /**
     * @param raw the configured duration
     * @param fallback what to return when it cannot be read
     * @return the duration in ticks, never negative
     */
    public static long parse(final String raw, final long fallback) {

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        String value = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");

        String digits;
        double perUnit;

        if (value.endsWith("ms")) {
            digits = value.substring(0, value.length() - 2);
            perUnit = 1D / 50D;

        } else if (value.endsWith("t")) {
            digits = value.substring(0, value.length() - 1);
            perUnit = 1D;

        } else if (value.endsWith("s")) {
            digits = value.substring(0, value.length() - 1);
            perUnit = 20D;

        } else if (value.endsWith("m")) {
            digits = value.substring(0, value.length() - 1);
            perUnit = 20D * 60D;

        } else {
            // ms
            digits = value;
            perUnit = 1D / 50D;
        }

        double amount;

        try {
            amount = Double.parseDouble(digits);

        } catch (NumberFormatException e) {
            Actions.warn("Could not read duration '" + raw + "'; expected 20t, 500ms, 3s or 2m.");
            return fallback;
        }

        if (amount <= 0D) {
            return 0L;
        }

        return Math.max(1L, Math.round(amount * perUnit));
    }
}
