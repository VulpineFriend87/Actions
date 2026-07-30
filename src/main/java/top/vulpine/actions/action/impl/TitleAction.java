package top.vulpine.actions.action.impl;

import eu.okaeri.configs.serdes.DeserializationData;
import eu.okaeri.configs.serdes.SerializationData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import top.vulpine.actions.action.Action;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.action.Flow;
import top.vulpine.actions.action.Ticks;
import top.vulpine.actions.target.Target;
import top.vulpine.commons.text.Colorize;

import java.time.Duration;
import java.util.List;

/**
 * Shows a title, a subtitle, or both.
 *
 * <pre>{@code
 * - type: title
 *   target: self
 *   title: "<gradient:#00ff87:#60efff>Benvenuto"
 *   subtitle: "<gray>Buon divertimento"
 *   fade_in: 20t
 *   stay: 3s
 *   fade_out: 20t
 *
 * - "[title] self; Benvenuto; Buon divertimento; 20; 60; 20"
 * }</pre>
 *
 * <p>Either line may be left out; a blank one shows as empty, which is how a
 * subtitle-only or title-only effect is configured.</p>
 *
 * <p>Bare numbers in the durations are <strong>ticks</strong>, matching the old
 * format where those values went straight to {@code sendTitle}.</p>
 */
public final class TitleAction implements Action {

    /** The registered id. */
    public static final String TYPE = "title";

    private static final long DEFAULT_FADE_IN = 10L;
    private static final long DEFAULT_STAY = 40L;
    private static final long DEFAULT_FADE_OUT = 10L;

    private final Target target;
    private final String title;
    private final String subtitle;
    private final Title.Times times;
    private final String fadeIn;
    private final String stay;
    private final String fadeOut;
    private final String shorthand;

    private TitleAction(final Target target, final String title, final String subtitle,
                        final String fadeIn, final String stay, final String fadeOut,
                        final String shorthand) {

        this.target = target;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
        this.shorthand = shorthand;

        this.times = Title.Times.times(
                ticks(fadeIn, DEFAULT_FADE_IN),
                ticks(stay, DEFAULT_STAY),
                ticks(fadeOut, DEFAULT_FADE_OUT));
    }

    private static Duration ticks(final String configured, final long fallback) {
        return Duration.ofMillis(Ticks.parseTicks(configured, fallback) * 50L);
    }

    /**
     * Starts building one in code, for a config default.
     *
     * <pre>{@code
     * TitleAction.builder()
     *         .title("<green>Benvenuto, %player%")
     *         .subtitle("<gray>Buon divertimento")
     *         .stay("3s")
     *         .build()
     * }</pre>
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the six settings of a title, so a default does not need a
     * six-argument call whose order nobody remembers.
     */
    public static final class Builder {

        private Target target = Target.SELF;
        private String title = "";
        private String subtitle = "";
        private String fadeIn;
        private String stay;
        private String fadeOut;

        private Builder() {
        }

        /** @param value who sees it @return this builder */
        public Builder target(final Target value) {
            this.target = value == null ? Target.SELF : value;
            return this;
        }

        /** @param value the title line @return this builder */
        public Builder title(final String value) {
            this.title = value;
            return this;
        }

        /** @param value the subtitle line @return this builder */
        public Builder subtitle(final String value) {
            this.subtitle = value;
            return this;
        }

        /** @param value how long to fade in, e.g. {@code 20t} @return this builder */
        public Builder fadeIn(final String value) {
            this.fadeIn = value;
            return this;
        }

        /** @param value how long to hold, e.g. {@code 3s} @return this builder */
        public Builder stay(final String value) {
            this.stay = value;
            return this;
        }

        /** @param value how long to fade out @return this builder */
        public Builder fadeOut(final String value) {
            this.fadeOut = value;
            return this;
        }

        /** @return the action */
        public TitleAction build() {
            return new TitleAction(target, title, subtitle, fadeIn, stay, fadeOut, null);
        }
    }

    /**
     * Builds from a config block.
     *
     * @param data the block
     * @return the action
     */
    public static TitleAction read(final DeserializationData data) {

        return new TitleAction(
                Target.parse(text(data, "target")),
                text(data, "title"),
                text(data, "subtitle"),
                text(data, "fade_in"),
                text(data, "stay"),
                text(data, "fade_out"),
                null);
    }

    /**
     * Builds from {@code [title] <target>; <title>; [subtitle]; [in]; [stay]; [out]}.
     *
     * @param params the text after the tag
     * @param raw the whole line
     * @return the action
     */
    public static TitleAction parse(final String params, final String raw) {

        String[] parts = params.split(";");

        return new TitleAction(
                Target.parse(at(parts, 0)),
                at(parts, 1),
                at(parts, 2),
                at(parts, 3),
                at(parts, 4),
                at(parts, 5),
                raw);
    }

    private static String at(final String[] parts, final int index) {
        return index < parts.length ? parts[index].trim() : null;
    }

    private static String text(final DeserializationData data, final String key) {
        return data.containsKey(key) ? data.get(key, String.class) : null;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {

        if (title.isBlank() && subtitle.isBlank()) {
            return Flow.CONTINUE;
        }

        List<Player> players = target.resolve(context);

        if (players.isEmpty()) {
            return Flow.CONTINUE;
        }

        Component renderedTitle = title.isBlank()
                ? Component.empty() : Colorize.color(context.expand(title));

        Component renderedSubtitle = subtitle.isBlank()
                ? Component.empty() : Colorize.color(context.expand(subtitle));

        Title composed = Title.title(renderedTitle, renderedSubtitle, times);

        for (Player player : players) {
            context.scheduler().run(player, () -> player.showTitle(composed));
        }

        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {

        data.add("target", target.raw());
        data.add("title", title);
        data.add("subtitle", subtitle);

        // Written back as configured, so "3s" does not become "60t" on save.
        data.add("fade_in", fadeIn == null ? DEFAULT_FADE_IN + "t" : fadeIn);
        data.add("stay", stay == null ? DEFAULT_STAY + "t" : stay);
        data.add("fade_out", fadeOut == null ? DEFAULT_FADE_OUT + "t" : fadeOut);
    }

    @Override
    public String shorthand() {
        return shorthand;
    }

    /**
     * @return the title line, unexpanded
     */
    public String title() {
        return title;
    }

    /**
     * @return the subtitle line, unexpanded
     */
    public String subtitle() {
        return subtitle;
    }

    /**
     * @return the timings
     */
    public Title.Times times() {
        return times;
    }
}
