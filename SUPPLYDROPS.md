# Pacifica-II SupplyDrops

SupplyDrops stores its files in `plugins/Pacifica-II/SupplyDrops/`:

- `config.yml` — automatic drops, location rules, expiry, messages, rarity labels, and selection limits.
- `rewards.yml` — UUID-keyed reward entries for each rarity, including the Bukkit-serialized `ItemStack` and its full metadata.
- `players.yml` — virtual amounts keyed by player UUID.

Files are written through a temporary file and atomically replaced when the filesystem supports it. Active drops are intentionally runtime-only: a graceful plugin shutdown removes their visuals, holograms, and chests; player balances and reward definitions persist.

## Configuration

`config.yml` defaults:

```yaml
automatic:
  enabled: true
  interval-seconds: 7200
  warps: []
  area-width: 50
  max-location-retries: 30
  radius-blocks: 50
  location-validation:
    max-attempts: 30
  rarity-selection:
    mode: WEIGHTED
    weights:
      common: 75
      uncommon: 50
      rare: 25
      epic: 10
      legendary: 1
  allowed-worlds: []
player-summon:
  require-flat-4x4: true
drop:
  expiration-seconds: 900
  fall-height: 35
  descent-speed: 0.12
rewards:
  minimum-selections: 4
  maximum-selections: 6
hologram:
  lines:
    - '&2&lSUPPLY AIRDROP'
    - '&7Rarity: %level%'
announcements:
  landing: '&6SupplyDrop &e%rarity% &6landed at &f%world% %x%, %y%, %z%&6.'
rarities:
  common: { display-name: Common, color: '&f' }
  uncommon: { display-name: Uncommon, color: '&a' }
  rare: { display-name: Rare, color: '&9' }
  epic: { display-name: Epic, color: '&5' }
  legendary: { display-name: Legendary, color: '&6' }
```

`automatic.warps` is the persisted eligibility list of configured Taxi warp names. An empty list now skips automatic drops; add eligible entries with `/adminpii supplydrop warp add <warp>`. Each cycle resolves the list through the read-only Taxi API, skips stale or unavailable entries, selects one remaining warp, and tries up to `location-validation.max-attempts` safe landing points. The horizontal target is sampled uniformly by area inside a circle with radius `radius-blocks` (default 50); existing placement rules still enforce world allowlists, ground and headroom safety, world borders, and entity clearance. `area-width` and `max-location-retries` remain in existing files for compatibility; the radius and nested attempt settings drive the current sampler. When the nested attempt setting is first introduced, it inherits the existing `max-location-retries` value. `allowed-worlds: []` allows any world; otherwise player and automatic placement are limited to the listed world names. `minimum-selections` and `maximum-selections` are bounded to 4–6.

Automatic rarity selection is weighted, not treated as independent percentages. The default weights total 161, giving normalized probabilities of Common 46.58%, Uncommon 31.06%, Rare 15.53%, Epic 6.21%, and Legendary 0.62%. A weight of zero disables that rarity; if all weights are zero, the automatic cycle safely skips. Missing weights are seeded with the defaults and invalid weights are logged and reset to the corresponding default. An empty warp list no longer implicitly enables every Taxi warp, so existing installations should explicitly add the desired entries.

Reward entries are stored under `rarities.<rarity>.entries.<uuid>` in `rewards.yml`:

```yaml
rarities:
  common:
    entries:
      62c5ec14-b2e3-48f1-9bd1-7af31c998555:
        rarity: common
        item: <serialized Bukkit ItemStack>
        weight: 1
        chance: 100.0
        minimum-quantity: 1
        maximum-quantity: 1
```

Chance is a percentage rolled once per entry for each drop. Entries that pass are sampled without replacement using positive weight; distinct configured entries may still produce the same item type. Quantity is rolled inclusively and split to valid stack sizes. If fewer than the configured minimum entries pass chance, or their stacks cannot fit the nine slots, that drop is skipped and a warning is logged. The editor adds one item from an administrator's inventory with shift-left-click, opens settings on a normal click, and removes an entry with shift-right-click. Weight and chance are edited in an anvil input; quantity bounds are configurable in `rewards.yml`.

## Commands

- `/supply` — open the 54-slot menu to view balances and summon one owned drop after location and reward validation.
- `/adminpii supplydrop edit <rarity>` — open the 54-slot reward editor.
- `/adminpii supplydrop warp list`
- `/adminpii supplydrop warp add <warp>`
- `/adminpii supplydrop warp remove <warp>`
- `/adminpii supplydrop warp info <warp>`
- `/adminpii supplydrop warp clear`
- `/adminpii supplydrop chance` — show configured weights and normalized selection chances.
- `/adminpii supplydrop grant <player> <rarity> <amount>`
- `/adminpii supplydrop remove <player> <rarity> <amount>`
- `/adminpii supplydrop set <player> <rarity> <amount>`
- `/adminpii supplydrop inspect <player>`
- `/adminpii supplydrop reload`
- `/adminpii supplydrop active` and `/adminpii supplydrop active remove <id>`

Administrative commands use the existing `pacifica.adminpii.supplydrop` permission (or `pacifica.admin`).

## Placement and lifecycle

Player summons require a level 4×4 solid footprint by default. Automatic drops bypass that footprint check but still require solid ground, clear placement blocks, a valid world border, and no blocking entities. Every landed drop has its own ID and inventory. Its expiration countdown starts on the first chest interaction and does not reset; fully claimed drops are removed immediately. Reward withdrawal uses normal inventory capacity and cannot be bypassed with shift-click, drag, number keys, or double-click actions.

Player-summoned drops start 50 blocks above the summoning player's current Y-coordinate, bounded by the world's valid height range. Automatic drops continue to use `drop.starting-height`. The airborne canopy is a 3×3 display arrangement using `drop.canopy-material` (default `CARPET`); four chain-like display connectors track the crate during descent.

The repository has no WorldGuard/claim-protection integration, so SupplyDrops can enforce world borders, configured allowed worlds, block safety, and entity clearance, but cannot inspect third-party protected regions.

## Public API

`PacificaAPI.supplyDrops()` exposes `SupplyDropsAPI`: `getAmount(UUID, SupplyDropRarity)`, `grantAmount(UUID, SupplyDropRarity, int)`, `removeAmount(UUID, SupplyDropRarity, int)`, and `summon(Player, SupplyDropRarity)`. `PacificaAPI.taxi()` now also exposes `TaxiAPI.getWarpNames()` and `getWarpLocation(String)` for read-only warp access; locations are returned as copies, and the methods must be called on the server thread. These default API methods preserve compatibility with existing TaxiAPI implementations. The public API version is now 1.1.0. Bukkit world operations, including `summon`, must be called on the server thread.