# Changelog

## 1.0.0 (unreleased)

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
