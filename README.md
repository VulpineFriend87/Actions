# Actions

A configurable action system for Paper plugins. Server owners describe what should
happen in YAML — messages, titles, sounds, commands, delays, conditions, branching —
and the plugin runs it.

Built on [okaeri-configs](https://github.com/OkaeriPoland/okaeri-configs), so action
lists are ordinary typed config fields.

**Requires Paper 1.18.2 or newer.**

---

## Contents

- [Install](#install)
- [Quick start](#quick-start)
- [Config format](#config-format)
- [Actions](#actions)
- [Targets](#targets)
- [Conditions](#conditions)
- [Placeholders](#placeholders)
- [Named sequences](#named-sequences)
- [Declaring defaults in Java](#declaring-defaults-in-java)
- [Custom actions](#custom-actions)
- [Scheduling](#scheduling)
- [Upgrading from the inline format](#upgrading-from-the-inline-format)

---

## Install

```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.okaeri.cloud/releases")
    maven("https://repo.tcoded.com/releases")
    maven("https://repo.vulpine.top/repository/maven-open/")
}

dependencies {
    implementation("top.vulpine:actions:0.1.0")
    implementation("eu.okaeri:okaeri-configs-yaml-bukkit:5.0.13")
    implementation("eu.okaeri:okaeri-configs-serdes-bukkit:5.0.13")
    implementation("com.tcoded:FoliaLib:0.5.1")

    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
}
```

Shade and relocate the libraries into your plugin:

```kotlin
tasks.shadowJar {
    val base = "com.example.myplugin.libs"
    relocate("top.vulpine.actions", "$base.actions")
    relocate("top.vulpine.commons", "$base.commons")
    relocate("eu.okaeri", "$base.okaeri")
    relocate("com.tcoded.folialib", "$base.foliaLib")
}
```

> Do **not** relocate `net.kyori.adventure`. Paper provides it unrelocated, and a
> relocated `Component` will not satisfy Paper's own method signatures.

---

## Quick start

**1. Declare an action list in your config class.**

```java
public class Config extends OkaeriConfig {

    public List<Action> join = new ArrayList<>(List.of(
            MessageAction.builder()
                    .target(Target.SELF)
                    .text("<green>Welcome, %player%")
                    .build()
    ));
}
```

**2. Register the serdes pack when loading the config.**

```java
ActionRegistry registry = BuiltinActions.registry();

Config config = ConfigManager.create(Config.class, it -> {
    it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit(),
            new ActionSerdes(registry));
    it.withBindFile(new File(getDataFolder(), "config.yml"));
    it.saveDefaults();
    it.load(true);
});
```

**3. Run a list.**

```java
FoliaLib foliaLib = new FoliaLib(this);
ActionScheduler scheduler = new FoliaLibScheduler(foliaLib);

ActionContext context = ActionContext.builder(scheduler)
        .player(player)
        .value("player", player.getName())
        .build();

ActionExecutor executor = ActionExecutor.run(config.join, context);
```

Keep the returned executor if the list can contain a `delay`, and call
`executor.cancel()` when the run should be abandoned — a player quitting, for example.

---

## Config format

An action is a YAML block with a `type`:

```yaml
join:
  - type: message
    target: all
    text: "<aqua>%player% <gray>joined"

  - type: delay
    time: 3s

  - type: title
    target: self
    title: "<green>Welcome"
    subtitle: "<gray>Enjoy your stay"
```

Every key except `type` is optional and has a default.

An action that cannot be understood — an unknown `type`, a malformed value — logs a
warning at startup, does nothing at runtime, and is preserved unchanged when the file
is saved. The rest of the config still loads.

---

## Actions

### `message`

Sends chat lines.

```yaml
- type: message
  target: self
  text: "<green>Hello"

- type: message
  target: all
  text:
    - "<gray>First line"
    - "<gray>Second line"
```

| Key | Default | |
|---|---|---|
| `target` | `self` | see [Targets](#targets) |
| `text` | — | a string or a list of strings |

### `title`

Shows a title, a subtitle, or both.

```yaml
- type: title
  target: self
  title: "<gradient:#00ff87:#60efff>Welcome"
  subtitle: "<gray>Enjoy your stay"
  fade_in: 20t
  stay: 3s
  fade_out: 20t
```

| Key | Default | |
|---|---|---|
| `target` | `self` | |
| `title` | empty | leave blank for a subtitle-only effect |
| `subtitle` | empty | |
| `fade_in` | `10t` | see [durations](#durations) |
| `stay` | `40t` | |
| `fade_out` | `10t` | |

### `actionbar`

Sends text above the hotbar.

```yaml
- type: actionbar
  target: self
  text: "<yellow>Teleporting in %time%s"
```

| Key | Default | |
|---|---|---|
| `target` | `self` | |
| `text` | empty | |

### `sound`

Plays a sound.

```yaml
- type: sound
  target: self
  key: "entity.player.levelup"
  volume: 1.0
  pitch: 1.4
  source: master
```

| Key | Default | |
|---|---|---|
| `target` | `self` | |
| `key` | empty | a namespaced key; blank disables the sound |
| `volume` | `1.0` | |
| `pitch` | `1.0` | |
| `source` | `master` | `master`, `music`, `record`, `weather`, `block`, `hostile`, `neutral`, `player`, `ambient`, `voice` — decides which volume slider applies |

**Keys are lowercase and dotted**, as the client knows them: `entity.player.levelup`,
`block.note_block.pling`, `ui.button.click`. Resource pack sounds work too.

Uppercase names such as `ENTITY_PLAYER_LEVELUP` are rejected with a warning at
startup. They are not convertible by lowercasing — many sounds keep an underscore
inside a segment, so `BLOCK_NOTE_BLOCK_PLING` is `block.note_block.pling` and not
`block.note.block.pling`. The full list is in the
[vanilla sounds.json](https://minecraft.wiki/w/Sounds.json).

### `command`

Runs a command.

```yaml
- type: command
  as: console
  command: "give %player% diamond 1"

- type: command
  as: player
  target: all
  command: "spawn"
```

| Key | Default | |
|---|---|---|
| `as` | `console` | `console` runs it once; `player` runs it once per targeted player |
| `target` | `self` | only used when `as: player` |
| `command` | empty | a leading `/` is optional |

### `gamemode`

```yaml
- type: gamemode
  target: self
  mode: adventure
```

| Key | Default | |
|---|---|---|
| `target` | `self` | |
| `mode` | — | `survival`, `creative`, `adventure`, `spectator` |

### `teleport`

Teleports to fixed coordinates.

```yaml
- type: teleport
  target: self
  world: lobby
  x: 0.5
  y: 100
  z: 0.5
  yaw: 90
  pitch: 0
```

| Key | Default | |
|---|---|---|
| `target` | `self` | |
| `world` | — | the world name |
| `x` `y` `z` | `0` | |
| `yaw` `pitch` | `0` | |

The world is looked up when the action runs, so a world loaded later by a world
manager still works. If it is missing, the action logs a warning and is skipped.

### `delay`

Pauses the list. Everything after it continues once the wait is over, including inside
nested branches.

```yaml
- type: delay
  time: 3s
```

| Key | Default | |
|---|---|---|
| `time` | none | see [durations](#durations) |

### `if`

Runs one of two lists depending on a condition.

```yaml
- type: if
  condition: "%player_level% >= 10"
  then:
    - type: message
      target: self
      text: "<green>Unlocked"
  else:
    - type: message
      target: self
      text: "<gray>You need level 10"
```

| Key | Default | |
|---|---|---|
| `condition` | always true | see [Conditions](#conditions) |
| `then` | empty | |
| `else` | empty | |

### `random`

Runs exactly one of several alternatives.

```yaml
- type: random
  options:
    - weight: 3
      actions:
        - type: message
          target: self
          text: "<gray>Tip: try /spawn"
    - weight: 1
      actions:
        - type: message
          target: self
          text: "<gray>Tip: try /kit"
```

Weights are relative, not percentages — `3` and `1` mean three times out of four.
Omitting `weight` means `1`, so equally likely alternatives need no weights.

### `repeat`

Runs a list several times.

```yaml
- type: repeat
  times: 3
  actions:
    - type: sound
      target: self
      key: "block.note_block.pling"
    - type: delay
      time: 10t
```

To space the passes out, put a `delay` inside `actions`. Maximum 1000 passes.

### `stop`

Abandons the rest of the run, including any enclosing lists. Useful as an early exit:

```yaml
- type: if
  condition: "permission: lobby.bypass"
  then:
    - type: stop

- type: gamemode
  target: self
  mode: adventure
```

### `set`

Stores a value that later actions in the same run can read as `%name%`.

```yaml
- type: set
  key: balance_before
  value: "%vault_eco_balance%"

- type: command
  as: console
  command: "eco take %player% 100"

- type: message
  target: self
  text: "<gray>You had %balance_before% coins"
```

| Key | Default | |
|---|---|---|
| `key` | — | the variable name, without percent signs |
| `value` | empty | may contain placeholders |

The value lives for the length of the run. Two players triggering the same list each
have their own.

### `run`

Runs a [named sequence](#named-sequences).

```yaml
- type: run
  sequence: vip_welcome
```

---

## Durations

Anywhere a duration is accepted:

| | |
|---|---|
| `20t` | ticks |
| `500ms` | milliseconds, rounded up to at least one tick |
| `3s` | seconds |
| `2m` | minutes |

---

## Targets

Who an action applies to.

| | |
|---|---|
| `self` | the player who triggered the run |
| `all` | everyone online |
| `others` | everyone except the triggering player |
| `world:<name>` | everyone in that world |
| `radius:<blocks>` | everyone within that distance of the triggering player |
| `permission:<node>` | everyone holding that permission |

An unrecognised target logs a warning and falls back to `self`.

---

## Conditions

A condition is a single expression, a list, or a block of `all` / `any` / `not`.

```yaml
condition: "%player_level% >= 10"          # one expression
condition: [ A, B ]                        # a list means all of them
condition:
  all: [ A, B ]                            # every one true
  any: [ A, B ]                            # at least one true
  not: A                                   # A false
```

**Keys next to each other combine with AND.** So this reads as *"(vip or rich) and not
in the arena"*:

```yaml
condition:
  any:
    - "permission: lobby.vip"
    - "%vault_eco_balance% >= 10000"
  not: "world: arena"
```

Any element of `all` or `any` can itself be a block, so nesting is unlimited:

```yaml
condition:
  any:
    - "permission: lobby.vip"
    - not: "world: arena"
    - all:
        - "%player_level% >= 10"
        - any: [ "world: hub", "world: lobby" ]
```

`not` takes a single condition. To negate a group, nest it:

```yaml
not:
  any: [ "permission: a", "permission: b" ]     # holds neither permission
```

### Comparisons

| | Example | |
|---|---|---|
| `==` | `"%player_world% == lobby"` | case-insensitive |
| `!=` | `"%player_gamemode% != CREATIVE"` | case-insensitive |
| `>` `>=` `<` `<=` | `"%player_level% >= 10"` | numeric |
| `contains` | `"%player_name% contains admin"` | case-insensitive |
| `starts_with` | `"%player_world% starts_with arena_"` | case-insensitive |
| `ends_with` | `"%player_name% ends_with _alt"` | case-insensitive |
| `matches` | `"%player_name% matches ^[A-Z][a-z]+$"` | regex, case-sensitive |

If both sides are numbers, the comparison is numeric — `10 > 9` is true. If either
side is not a number, `>` `>=` `<` `<=` log a warning and evaluate to false, since
ordering text alphabetically is rarely what was meant.

### Named checks

| | Example |
|---|---|
| `permission:` | `"permission: lobby.vip"` |
| `world:` | `"world: lobby"` |
| `gamemode:` | `"gamemode: adventure"` |
| `sneaking:` | `"sneaking: true"` |
| `chance:` | `"chance: 25%"` or `"chance: 0.25"` |

---

## Placeholders

`%name%` tokens are expanded from two sources: values the plugin puts in the context,
and anything the plugin has hooked up externally.

For PlaceholderAPI:

```java
Actions.placeholders(PlaceholderAPI::setPlaceholders);
```

If a placeholder does not expand — usually a missing expansion — conditions that use
it evaluate to false and log a warning naming the placeholder. The warning appears
once, not on every event.

Values supplied through the context are escaped before being rendered as text, so a
player whose name contains MiniMessage syntax cannot alter the formatting of a message
or inject a clickable tag. Values are **not** escaped inside commands or condition
operands, where the raw text is what is wanted.

---

## Named sequences

A sequence is an action list with a name, callable from anywhere. Define them in a
config of your own and register them:

```yaml
# actions.yml
sequences:
  vip_welcome:
    - type: message
      target: self
      text: "<gold>Welcome back"
    - type: sound
      target: self
      key: "entity.player.levelup"
```

```java
SequenceRegistry sequences = new SequenceRegistry();
sequences.putAll(actionsConfig.sequences);

ActionContext context = ActionContext.builder(scheduler)
        .player(player)
        .sequences(sequences)
        .build();
```

Then anywhere:

```yaml
- type: run
  sequence: vip_welcome
```

Sequences are resolved when the action runs, so one may be defined after another that
calls it. A sequence that eventually calls itself is abandoned after 32 levels of
nesting, with a warning.

---

## Declaring defaults in Java

Every action has a builder. `target` defaults to `self` and can be omitted.

```java
public List<Action> join = new ArrayList<>(List.of(

        TitleAction.builder()
                .title("<green>Welcome, %player%")
                .subtitle("<gray>Enjoy your stay")
                .stay("3s")
                .build(),

        MessageAction.builder()
                .target(Target.ALL)
                .text("<aqua>%player% <gray>joined")
                .build(),

        SoundAction.builder()
                .key("entity.player.levelup")
                .pitch(1.4F)
                .build(),

        DelayAction.builder()
                .time("1s")
                .build(),

        IfAction.builder()
                .condition("permission: lobby.vip")
                .then(CommandAction.builder()
                        .asConsole()
                        .command("give %player% diamond 1")
                        .build())
                .otherwise(MessageAction.builder()
                        .text("<gray>No perks")
                        .build())
                .build(),

        RandomAction.builder()
                .choice(3, MessageAction.builder().text("<gray>Try /spawn").build())
                .choice(1, MessageAction.builder().text("<gray>Try /kit").build())
                .build()
));
```

Conditions accept the same shapes as YAML — a string, a `List`, or a `Map`:

```java
IfAction.builder()
        .condition(Map.of(
                "any", List.of("permission: lobby.vip", "%vault_eco_balance% >= 10000"),
                "not", "world: arena"))
        .then(/* … */)
        .build();
```

---

## Custom actions

Implement `Action` and register it. The registry maps a `type` id to two functions:
one that reads a config block, and optionally one that reads an inline string.

```java
public final class FlagAction implements Action {

    public static final String TYPE = "flag";

    private final String flag;

    private FlagAction(final String flag) {
        this.flag = flag;
    }

    public static FlagAction read(final DeserializationData data) {
        return new FlagAction(data.get("flag", String.class));
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Flow execute(final ActionContext context) {
        // … do the work …
        return Flow.CONTINUE;
    }

    @Override
    public void write(final SerializationData data) {
        data.add("flag", flag);
    }
}
```

```java
ActionRegistry registry = BuiltinActions.registry();
registry.register(FlagAction.TYPE, FlagAction::read);
```

`execute` returns what the executor should do next:

| | |
|---|---|
| `Flow.CONTINUE` | move to the next action |
| `Flow.STOP` | abandon the whole run |
| `Flow.enter(list)` | run a nested list, then carry on |
| `Flow.delay(ticks)` | pause, resuming at this position later |

Scheduling and resumption are the executor's job, so an action never needs a reference
to it. `Flow.enter` on an empty list and `Flow.delay` on zero both mean `CONTINUE`.

To start from an empty vocabulary instead of the built-ins, use
`new ActionRegistry()` directly.

---

## Scheduling

`ActionScheduler` has two methods. A FoliaLib-backed implementation ships with the
library and covers Folia, Paper, Spigot and legacy Bukkit:

```java
ActionScheduler scheduler = new FoliaLibScheduler(new FoliaLib(this));
```

FoliaLib is a `compileOnly` dependency here, so your plugin declares and relocates it
(see [Install](#install)). If your plugin already uses FoliaLib, pass in the instance
you already have.

To use something else, implement the interface:

```java
public interface ActionScheduler {
    void run(Entity entity, Runnable task);
    Cancellable runLater(Entity entity, Runnable task, long ticks);
}
```

A null entity means the work is not tied to one, and belongs on the global region.

---

## Logging

By default warnings go through the logger from `top.vulpine:commons`. To route them
elsewhere:

```java
Actions.logger(message -> getLogger().warning(message));
```

---

## Upgrading from the inline format

An action can also be written as a single line:

```yaml
- "[message] self; <green>Welcome"
- "[delay] 3s"
```

This form is **deprecated**. It cannot express nesting, and its `;` separator has no
escape, so a value containing a semicolon silently shifts the remaining fields.

Both forms load, and can be mixed in the same list. To convert a config in place,
enable migration:

```java
new ActionSerdes(registry).migrateShorthand(true)
```

Inline entries are then written back as blocks the next time the config is saved.

> **Back up the config file first.** okaeri regenerates the file from the schema, so
> comments added by hand are lost and key order may change. Tell the server owner
> where the backup went.

For reference while migrating:

| Inline | Fields |
|---|---|
| `[message] <target>; <text>` | |
| `[title] <target>; <title>; <subtitle>; <fade_in>; <stay>; <fade_out>` | bare numbers are **ticks** |
| `[actionbar] <target>; <text>` | |
| `[sound] <target>; <key>; <volume>; <pitch>` | |
| `[command] <console\|player>; <command>` | |
| `[gamemode] <target>; <mode>` | |
| `[delay] <duration>` | a bare number is **milliseconds** |
| `[stop]` | |
| `[set] <key>; <value>` | |
| `[run] <sequence>` | |

`player` and `global` are accepted as older names for `self` and `all`.

`if`, `random`, `repeat` and `teleport` have no inline form.

---

## Licence

MIT
