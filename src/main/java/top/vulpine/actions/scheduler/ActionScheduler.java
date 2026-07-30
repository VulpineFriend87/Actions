package top.vulpine.actions.scheduler;

import org.bukkit.entity.Entity;

/**
 * The scheduling this library needs, and nothing else.
 *
 * <p>Four methods rather than a dependency on FoliaLib directly. The implementation
 * of region-aware scheduling is genuinely hard and worth taking from a library; the
 * <em>interface</em> is trivial and worth owning, because it means only one class in
 * the consuming plugin is exposed if that library changes its API. Renovate bumping
 * a scheduler dependency should be a compile error in one adapter, not a
 * {@link NoSuchMethodError} in whichever plugin happens to fire an action first.</p>
 *
 * <p>A null entity means the work is not tied to one — on Folia it belongs on the
 * global region.</p>
 */
public interface ActionScheduler {

    /**
     * Runs on the entity's region now, or on the main thread on non-regionised
     * servers.
     *
     * @param entity the entity the work concerns; null for global
     * @param task the work
     */
    void run(Entity entity, Runnable task);

    /**
     * Runs on the entity's region after a delay.
     *
     * @param entity the entity the work concerns; null for global
     * @param task the work
     * @param ticks how long to wait
     * @return a handle that can cancel the pending task
     */
    Cancellable runLater(Entity entity, Runnable task, long ticks);
}
