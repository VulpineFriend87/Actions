package top.vulpine.actions.scheduler;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.entity.Entity;

import java.util.Objects;

/**
 * Runs actions through <a href="https://github.com/TechnicallyCoded/FoliaLib">FoliaLib</a>,
 * which picks the right scheduler for Folia, Paper, Spigot or legacy Bukkit.
 *
 * <pre>{@code
 * FoliaLib foliaLib = new FoliaLib(this);
 * ActionScheduler scheduler = new FoliaLibScheduler(foliaLib);
 * }</pre>
 *
 * <h2>FoliaLib is not bundled</h2>
 * <p>It is a {@code compileOnly} dependency here, so this library does not drag it
 * into plugins that do not want it, and — more importantly — so a plugin that
 * <em>does</em> use FoliaLib for its own scheduling passes its own instance in rather
 * than ending up with two relocated copies that cannot talk to each other.</p>
 *
 * <p>The consuming plugin therefore declares and relocates FoliaLib itself:</p>
 *
 * <pre>{@code
 * implementation(libs.folialib)
 * relocate("com.tcoded.folialib", "your.plugin.libs.foliaLib")
 * }</pre>
 *
 * <p>This class is only loaded when something references it, so a plugin that supplies
 * its own {@link ActionScheduler} never needs FoliaLib on the classpath at all.</p>
 *
 * <h2>Why an interface in between</h2>
 * <p>{@link ActionScheduler} has two methods and this adapter is the only thing that
 * touches FoliaLib. If FoliaLib changes its API, the break is a compile error in one
 * file rather than a {@code NoSuchMethodError} in whichever plugin fires an action
 * first. It also keeps the executor testable without a running server.</p>
 */
public final class FoliaLibScheduler implements ActionScheduler {

    private final PlatformScheduler scheduler;

    /**
     * @param foliaLib the plugin's FoliaLib instance
     */
    public FoliaLibScheduler(final FoliaLib foliaLib) {
        this.scheduler = Objects.requireNonNull(foliaLib, "foliaLib").getScheduler();
    }

    /**
     * @param scheduler FoliaLib's platform scheduler directly
     */
    public FoliaLibScheduler(final PlatformScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void run(final Entity entity, final Runnable task) {

        if (entity == null) {
            // No entity to anchor to, so this belongs on the global region. Note this
            // is next tick, not immediately: on a regionised server there is no
            // "right now" that is safe from an arbitrary thread.
            scheduler.runNextTick(wrapped -> task.run());
            return;
        }

        scheduler.runAtEntity(entity, wrapped -> task.run());
    }

    @Override
    public Cancellable runLater(final Entity entity, final Runnable task, final long ticks) {

        WrappedTask wrapped = entity == null
                ? scheduler.runLater(task, ticks)
                : scheduler.runAtEntityLater(entity, task, ticks);

        // FoliaLib can hand back null when the entity is already gone, in which case
        // there is nothing pending and nothing to cancel.
        return wrapped == null ? Cancellable.NOOP : wrapped::cancel;
    }
}
