# Pacifica-II

## Crew System — current implementation

The current Crew System stage provides persisted crews, core membership management, a basic member menu, a public read API, and an independent Crew XP foundation. It does not yet implement every subsystem in the full Crew System specification.

### Player commands

- `/crew create <name>` — create a crew. The player must meet the configured Pacifica-Core level requirement and pay the configured fee.
- `/crew invite <player>`, `/crew accept <crew>`, `/crew deny <crew>` — manage invitations. Invitations are stored with an expiration time and survive restarts.
- `/crew kick <player>`, `/crew promote <player>`, `/crew demote <player>` — manage members with server-side rank checks.
- `/crew transfer <player>` then `/crew transfer <player> confirm` — confirm a leadership transfer.
- `/crew leave` — leave unless the player is the Leader.
- `/crew info [crew|player]`, `/crew top`, `/crew menu`, `/crew help` — view crew data and the current menu.
- `/crew reload` — reload Pacifica-II configuration; requires `pacifica.crew.admin`.

The ranks are Leader, Co-Leader, Officer, and Member. Leaders control leadership transfer; Co-Leaders cannot appoint or modify Co-Leaders. Kicks, promotions, and demotions validate the actor's and target's ranks in the database transaction.

### Configuration

The defaults are in `config.yml` under `crew`:

```yaml
crew:
  creation-cost: 50000
  minimum-player-level: 5
  base-member-limit: 10
  invitation-expiration-hours: 24
  name-min-length: 3
  name-max-length: 16
  name-pattern: '[A-Za-z0-9_]+'
  reserved-names:
    - admin
    - staff
    - owner
  progression:
    maximum-level: 50
    total-xp: 413250
    player-kill-xp: 10
    kill-cooldown-hours: 24
```

Crew XP is separate from Pacifica-Core Player XP and Minecraft experience. The default level curve distributes 413,250 total XP across the 49 level-ups: the first 33 steps require 8,434 XP each and the remaining 16 require 8,433 XP each. The configured maximum and total are applied by the Crew progression service.

Eligible player kills grant the configured base Crew XP, or 2.5 times that amount for a kill against a different crew. Same-crew kills, self-kills, environmental deaths, non-player projectile sources, NPC-marked players, and repeated killer/victim pairs inside the configured cooldown do not grant Crew XP. Cooldown records are persisted. Rival and active-war multipliers are not active until those systems are implemented.

Crew records and member records are stored in `crew.db`. Database mutations run on a dedicated single-thread executor and update immutable in-memory snapshots after successful transactions. The API is registered with Bukkit's services manager as `CrewAPI`.

### Java API example

```java
import com.districtx.pacifica.api.crew.CrewAPI;
import com.districtx.pacifica.api.crew.Crew;

CrewAPI crews = getServer().getServicesManager().load(CrewAPI.class);
if (crews != null) {
    Crew crew = crews.getCrewForPlayer(player.getUniqueId()).orElse(null);
    if (crew != null) {
        player.sendMessage("Crew: " + crew.name() + " (level " + crew.level() + ")");
        crews.awardCrewExperience(player.getUniqueId(), 25)
                .thenAccept(result -> getLogger().info("Crew XP result: " + result));
    }
}
```

API lookups read the current immutable cache. `awardCrewExperience` returns a `CompletableFuture<CrewResult>`; callers should not block the server thread waiting for it.

### Verified integration findings and remaining work

The Pacifica-Core repository's public Player Level API was inspected. It provides `PlayerLevelService.getLevel(UUID)`, which Pacifica-II already resolves for the creation-level check. The Core also exposes a mutable pre-grant `PlayerExperienceGainEvent`, suitable for a future opt-in player-XP perk integration. Crew XP does not call or modify either player XP path.

The current Pacifica-Core SpawnShop API has `purchaseDetailed(Player, UUID, int)`, and its `SpawnShopManager` calculates and validates purchases from the stored unit price. There is no discount callback or price modifier. A Crew gun-shop discount therefore requires a backward-compatible Core-side purchase extension and is not currently applied. This Pacifica-II workspace cannot change the separate Core repository.

Not yet implemented: disband flow and audit history; bank deposits, withdrawals, and transaction ledger; crew chat; seasonal Street Cred and rewards; leaderboard history; perk ownership/upgrades; Player XP, TrashCan, Backpack, and gun-shop perks; wars/wagers; territories; weekly quests; PlaceholderAPI placeholders; administrator record-repair tools; and automated acceptance tests. The menu currently displays persisted crew/member information and pagination only. No build or runtime test result is claimed.