# Changelog

## 1.1.0 - Unreleased

### Added
- Remove shopkeeper NPCs with `/cep shopkeeper remove <npcs>` or by Shift + left-clicking one twice with the
  Shop Editor. Both use the new permission node `cobblemon_economy_plus.shopkeeper.remove` (OP 2 by default,
  grantable with LuckPerms). Only entities that are shopkeepers are removed.
- `/cep shop validate` checks `shops.json` and `item_values.json` without applying them: JSON errors with their
  line and column, skipped shops and entries, and buy/sell money loops.
- API: `CobblemonEconomyApi.onBalanceChanged` (notified after every balance change) and `onReward` (cancel a
  battle / capture / Pokédex reward or change its amount before it is paid), plus `API_VERSION` (now 2).
- Cobblemon rewards, configured in the new `config/cobblemon_economy_plus/rewards.json`: coins for winning wild
  battles, defeating Cobblemon NPC trainers, captures (alphas pay more in battles and captures) and Pokédex milestones
  (10 to 1000 species caught, each paid once; milestones passed earlier are paid at the next login). Each reward can be
  switched off, amounts are `base + level × per_level`, and an optional `daily_cap` limits battle and capture rewards per
  day. PvP battles pay nothing, and NPCs tagged `cep_no_reward` pay no trainer reward. Reward bookkeeping is saved in
  `data/cobblemon_economy_plus_rewards.dat`.
- `/cep top [page]`: balance ranking (10 players per page, ties share a rank, offline players shown by name) with the
  viewer's own rank. Permission node `cobblemon_economy_plus.top` (everyone by default).

### Changed
- `/cep shop reload` reports problems in chat (up to 5, the rest in the server log) and how many item values were loaded.
- `/cep shop reload` and `/cep shop validate` also cover `rewards.json`.

### Fixed
- A syntax error in `shops.json` (such as a missing comma or a cut-off file) no longer fails with an unexplained error.
  The file is reported with the error's location, and the shops that were already loaded stay in place instead of being
  replaced by the built-in defaults. The same applies to `item_values.json`.
- A shop without an `id`, or whose `entries` is not a list, is skipped on its own instead of discarding the whole file.
- Shopkeepers can no longer be hit or damaged, including NPCs assigned with `/cep shopkeeper` or the Shop Editor
  and attacks from creative-mode players. Only `/kill` and the void can still remove them.

## 1.0.0 - 2026-11-10

First public version.

### Added
- Persistent per-player currency; `/cep balance`, `/cep money get|set|add|take`, `/cep pay` and `/pay`.
- Shop screen with buy / sell modes, category pager, quantity picker and 11 visual themes
  (Poké Ball variants, TM, berry and block looks).
- Selling: base values in `item_values.json` × a per-shop `sell_multiplier`; only unmodified items from the main inventory are accepted.
- Shopkeeper NPCs (spawn egg, `/cep shopkeeper`), Shop Editor item with *Change shop* / *Change theme* modes.
- Bilingual (`en_us` / `ja_jp`) shop names and subtitles in `shops.json`.
- Balance HUD (on change / always / hidden; `B` to peek, `Shift + B` to cycle) with count-up animation and sounds.
- Trainer Card item (draft).
- Permission nodes for LuckPerms and compatible managers (NeoForge PermissionAPI; Fabric permissions API when present).
- Offline-player support in commands (by player name).
- Public API: `CobblemonEconomyApi`.
- Transaction log at `logs/cobblemon_economy_plus/transactions.log` with rotation.
- `/cep history <player> [count]` to view a player's latest transactions in game.
- Versioned saved-data format for future migrations.
- Network protocol version (`1.0`), enforced by NeoForge.
- The shop and Trainer Card screens can also be closed with the inventory key (E by default), like the vanilla inventory.
- `runClient2` development run configuration (Fabric and NeoForge) for local two-player testing.
- Released under the MIT License.
- Automated tests for the server-side logic (pricing, saved data, config parsing, inventory space, wire formats, log format).

### Safety
- Purchases are validated on the server (balance, inventory space, distance, and that the shop still matches what the client displayed).
- Items that do not fit the inventory after payment are dropped at the player's feet instead of being lost.
- The shop stays usable while open (no time limit); only distance is checked.
