# Pacifica-II Developer API

The public API is packaged in the Pacifica-II plugin under `com.districtx.pacifica.api`. It exposes Taxi/teleport, Combat Tag, Tag, and job-menu services, plus their public events and GUI extension contracts. The API version in this release is **1.0.0**.

## Installation and optional dependency

Compile against a copy of the Pacifica-II plugin JAR, but do not bundle that JAR in your plugin. For example, keep it in a local `libs/` directory:

```groovy
dependencies {
    compileOnly files('libs/Pacifica-II.jar')
}
```

For Maven, install the local plugin JAR into your local Maven repository and declare it as `provided`:

```sh
mvn install:install-file \
  -Dfile=libs/Pacifica-II.jar \
  -DgroupId=com.districtx \
  -DartifactId=pacifica-ii \
  -Dversion=1.21.8 \
  -Dpackaging=jar
```

```xml
<dependency>
  <groupId>com.districtx</groupId>
  <artifactId>pacifica-ii</artifactId>
  <version>1.21.8</version>
  <scope>provided</scope>
</dependency>
```

Declare Pacifica-II as an optional ordering dependency in your plugin's `plugin.yml`:

```yaml
softdepend:
  - Pacifica-II
```

Because Pacifica-II is optional, put code that references `com.districtx.pacifica.api` in a dedicated integration class and only load that class after checking that Pacifica-II is enabled. This keeps unrelated plugin functionality available when Pacifica-II is absent:

```java
if (getServer().getPluginManager().isPluginEnabled("Pacifica-II")) {
    new PacificaIntegration(this).hook();
}
```

The API entry point and services return safe unavailable values when their provider is not registered. `PacificaAPI.isAvailable()` reports whether at least one Pacifica-II service is available.

## Initialization and compatibility

```java
import com.districtx.pacifica.api.PacificaAPI;

if (PacificaAPI.isAvailable()) {
    // Use PacificaAPI.taxi(), PacificaAPI.combatTag(), or PacificaAPI.tags().
}
```

`PacificaAPI.API_VERSION` follows semantic versioning. Compatible additions and fixes retain the major version; breaking public contract changes require a major version increase. The API targets the modern Paper/Purpur 1.21.8+ runtime and Java 21 used by Pacifica-II.

## Taxi and teleport API

```java
import com.districtx.pacifica.api.PacificaAPI;
import com.districtx.pacifica.api.taxi.TaxiResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

void sendPlayerTo(Player player, Location destination) {
    TaxiResult result = PacificaAPI.taxi().requestTaxi(player, destination);
    if (result == TaxiResult.SUCCESS) {
        player.sendMessage("Taxi countdown started.");
    }
}

PacificaAPI.taxi().getTeleportRequest(player).ifPresent(request -> {
    int seconds = request.getRemainingSeconds();
    Location destinationCopy = request.getDestination();
});
```

`TaxiResult` represents normal outcomes such as combat blocking, invalid destinations, an already active countdown, cancellation by an event, or an unavailable feature. Request snapshots are read-only and return a copy of their destination location.

## Taxi GUI state and extensions

```java
PacificaAPI.taxi().getOpenTaxiGUI(player).ifPresent(gui -> {
    if (gui.isOpen()) gui.close();
});

PacificaAPI.taxi().registerGUIProvider(new TaxiGUIProvider() {
    @Override public String getId() { return "example-home"; }
    @Override public String getDisplayName() { return "&aMy Home"; }
    @Override public ItemStack getIcon(Player player) { return new ItemStack(Material.COMPASS); }
    @Override public void execute(Player player) { player.performCommand("home"); }
});
```

Provider IDs are validated and unique without regard to case. Pacifica-II clones returned icons before inserting them into its menu. Taxi provider actions are placed into available interior slots; built-in Taxi buttons retain their configured slots. Remove a provider with `unregisterGUIProvider("example-home")`.

## Combat Tag API

```java
PacificaAPI.combatTag().getCombatSession(player).ifPresent(session -> {
    int secondsLeft = session.getRemainingSeconds();
    session.getLastOpponent().ifPresent(opponentId -> {
        // Use the UUID without retaining Pacifica-II internal combat state.
    });
});

if (PacificaAPI.combatTag().isInCombat(player)) {
    player.sendMessage("You are currently in combat.");
}
```

`CombatSession` is a read-only snapshot. The API also supports supported tag/untag operations and exposes `CombatTagStartEvent`, `CombatTagCancelEvent`, and `CombatTagEndEvent` in `com.districtx.pacifica.api.combat.event`.

## Tag API and Tag GUI

```java
PacificaAPI.tags().getEquippedTag(player).ifPresent(tag -> {
    String id = tag.getId();
    String displayName = tag.getDisplayName();
});

if (PacificaAPI.tags().hasTag(player, "champion")) {
    PacificaAPI.tags().equipTag(player, "champion");
}

PacificaAPI.tags().getOpenGUI(player).ifPresent(gui -> {
    if (gui.isOpen()) gui.close();
});
```

Tag definitions and ownership results are immutable snapshots. `TagEquipEvent` and `TagUnequipEvent` are cancellable; `TagCreatedEvent` and `TagRemovedEvent` report definition changes. A `TagGUIProvider` can append an action to the paginated Tag menu:

```java
PacificaAPI.tags().registerGUIProvider(new TagGUIProvider() {
    @Override public String getId() { return "example-rewards"; }
    @Override public ItemStack createIcon(Player player) { return new ItemStack(Material.CHEST); }
    @Override public void onClick(Player player) { player.performCommand("rewards"); }
});
```

Custom Tag entries follow the existing tag definitions across menu pages; existing tag slots and navigation controls remain unchanged. Provider IDs are case-insensitively unique and can be removed with `unregisterGUIProvider("example-rewards")`.

## Events

Register ordinary Bukkit listeners for public API events. The Taxi start and Combat Tag start events can be cancelled before their action starts:

```java
@EventHandler
public void onTaxiStart(TaxiStartEvent event) {
    if (event.getPlayer().hasPermission("example.blocktaxi")) event.setCancelled(true);
}

@EventHandler
public void onCombatStart(CombatTagStartEvent event) {
    // Cancel only when the integration intentionally prevents a combat tag.
}
```

## Threading

Methods that inspect or modify players, locations, inventories, combat/tag state, or GUI registrations must run on the server main thread. Provider icon creation and click callbacks, and API events, also run on the main thread. Do not access Bukkit entities, `ItemStack`s, or GUI state asynchronously. The API does not expose database or configuration objects.

## Migration notes

Version 1.0.0 is the initial public contract. Consumers should depend on API interfaces and immutable snapshots, not implementation or GUI classes. Check `API_VERSION` if an integration supports multiple Pacifica-II API generations; a future major version may change contracts, while compatible additions belong in a minor release.

Separate gamemode inventories have been removed as a breaking behavior change. The `PacificaPlugin.separateInventories()` accessor and its implementation are no longer available; `/gms`, `/gma`, `/gmc`, and `/gmsp` now only apply the requested Bukkit gamemode, without saving or restoring inventory contents.

## Wanted placeholders and Warden menu

With PlaceholderAPI installed, use `%pacifica_wanted%` for the player's five gold wanted-star symbols and `%pacifica_wantedlevel%` for the numeric wanted level. They can be combined as:

```text
%pacifica_wanted% &f(%pacifica_wantedlevel%)
```

The star placeholder follows `wanted.placeholder.enabled` (default `true`); the numeric placeholder returns the authoritative wanted level, or `0` when the player has no recorded wanted state.

Right-click a linked Warden NPC to open the Warden menu. The configured fallback NPC name is `jobs.npcs.warden` (`warden_npc` by default); administrators can link an existing NPC with `/adminpii job npc warden`. Other plugins can open the same menu through the registered `JobMenuAPI` service:

```java
import com.districtx.pacifica.api.jobs.JobMenuAPI;
import org.bukkit.Bukkit;

JobMenuAPI menus = Bukkit.getServicesManager().load(JobMenuAPI.class);
if (menus != null && menus.isAvailable()) menus.openWardenMenu(player);
```

The Warden charges `wanted.warden.price-per-star` per level (default `10000`). Removing all levels costs that price multiplied by the player's current wanted level. Set `wanted.warden.enabled` to `false` to disable Warden transactions. The transaction messages are under `wanted.warden.messages` in `config.yml`.

## Administrative commands

Administrative operations are available under `/adminpii`:

| Command | Operation |
|---|---|
| `/adminpii spawn set` | Set the Pacifica spawn. |
| `/adminpii spawn safespawn [1\|2]` | Check or set a safe-spawn position. |
| `/adminpii warp set <name>` | Create or update a random warp at your location. |
| `/adminpii taxi link` | Link the targeted taxi NPC. |
| `/adminpii trash set\|unlink\|holo toggle\|addprice <amount>` | Configure trash cans, holograms, or held-item prices. |
| `/adminpii jail <setspawn\|spawn\|release <player>\|status <player>\|reload>` | Use the existing jail administration operations. |
| `/adminpii job npc <type>` | Link a job/Warden NPC; use `clear <type>` to remove the link. |
| `/adminpii tag <create\|edit\|give\|remove\|delete\|source\|rarity> ...` | Manage cosmetic tags. |
| `/adminpii supplydrop ...` | Configure rewards and manage player supply balances and active drops. |
| `/adminpii reload` | Reload Pacifica-II configuration. |

The root permission is `pacifica.adminpii`; feature permissions are `pacifica.adminpii.spawn`, `.warp`, `.taxi`, `.trash`, `.jail`, `.job`, `.tag`, and `.reload`. The root permission grants its feature permissions. Existing permission checks for each operation also remain in effect.

Administrative entry points are consolidated under `/adminpii`; player commands such as `/taxi`, `/trash`, and `/tag` remain available. `/jobmode` has been removed; its similarly named `pacifica.jobmode.delay.*` permissions are still used by the job death-cooldown perks. This release has no existing Wanted Level administration or Combat Tag administration operation to expose under `/adminpii`; `/ct` remains a player-facing status command.