package top.vulpine.actions;

import top.vulpine.actions.action.ActionRegistry;
import top.vulpine.actions.action.impl.ActionBarAction;
import top.vulpine.actions.action.impl.CommandAction;
import top.vulpine.actions.action.impl.DelayAction;
import top.vulpine.actions.action.impl.GamemodeAction;
import top.vulpine.actions.action.impl.IfAction;
import top.vulpine.actions.action.impl.MessageAction;
import top.vulpine.actions.action.impl.RandomAction;
import top.vulpine.actions.action.impl.RepeatAction;
import top.vulpine.actions.action.impl.RunAction;
import top.vulpine.actions.action.impl.SetAction;
import top.vulpine.actions.action.impl.SoundAction;
import top.vulpine.actions.action.impl.StopAction;
import top.vulpine.actions.action.impl.TeleportAction;
import top.vulpine.actions.action.impl.TitleAction;

/**
 * Registers the actions the library ships with.
 *
 * <p>Kept separate from {@link ActionRegistry} so a plugin can start from an empty
 * registry when it wants only its own vocabulary.</p>
 */
public final class BuiltinActions {

    private BuiltinActions() {
    }

    /**
     * @return a registry holding every built-in action
     */
    public static ActionRegistry registry() {
        ActionRegistry registry = new ActionRegistry();
        register(registry);
        return registry;
    }

    /**
     * Adds the built-ins to an existing registry.
     *
     * @param registry the registry
     */
    public static void register(final ActionRegistry registry) {

        registry.register(MessageAction.TYPE, MessageAction::read, MessageAction::parse);
        registry.register(TitleAction.TYPE, TitleAction::read, TitleAction::parse);
        registry.register(ActionBarAction.TYPE, ActionBarAction::read, ActionBarAction::parse);
        registry.register(SoundAction.TYPE, SoundAction::read, SoundAction::parse);
        registry.register(CommandAction.TYPE, CommandAction::read, CommandAction::parse);
        registry.register(GamemodeAction.TYPE, GamemodeAction::read, GamemodeAction::parse);
        registry.register(DelayAction.TYPE, DelayAction::read, DelayAction::parse);
        registry.register(StopAction.TYPE, StopAction::read, StopAction::parse);
        registry.register(SetAction.TYPE, SetAction::read, SetAction::parse);
        registry.register(RunAction.TYPE, RunAction::read, RunAction::parse);

        // No one-liner form: nested lists and named coordinates do not fit
        // "[type] a; b; c".
        registry.register(IfAction.TYPE, IfAction::read);
        registry.register(TeleportAction.TYPE, TeleportAction::read);
        registry.register(RandomAction.TYPE, RandomAction::read);
        registry.register(RepeatAction.TYPE, RepeatAction::read);
    }
}
