# Changelog

## 1.2.0 (unreleased)

### Added
- Cobblemon Battle Plus support (optional): the Trainer Card shows the player's Battle Points under the balance,
  kept up to date as they change (Battle Plus 3.1.0 or newer; older versions refresh with the balance). Without
  Battle Plus the card is unchanged.
- The Trainer Card is no longer a draft: it also shows the number of species in the player's Pokédex, their play time
  and their place in the balance ranking (`/cep top`). The server sends these when the card is opened.
- Daily login bonus, configured as `daily_login` in `rewards.json`: a calendar of 1 to 60 days, each paying coins
  and/or items (any item id). The first login of each day pays the next day and opens a calendar screen showing every
  day's rewards, the days already received and today. Options: `reset_on_miss` (a missed day starts again from day 1),
  `repeat` (start over after the last day) and `show_screen`. The default is a 4-week calendar (100 to 1,000 coins, with
  balls, medicine and candies, and an Ability Capsule on day 28); it is on by default (`"enabled": false` turns it off) and does not count
  towards `daily_cap`. The login count is saved in `data/cobblemon_economy_plus_rewards.dat` (data version 2).
- `/cep daily` shows the calendar, and pays today's bonus if the player has not received it yet (for example after
  staying online past midnight). Permission node `cobblemon_economy_plus.daily` (everyone by default).
- Text Placeholder API support (Fabric, optional): `%cobblemon_economy_plus:balance%`, `balance_raw`, `rank`,
  `top_name N` and `top_balance N`.
- API: the `DAILY_LOGIN` reward type, so other mods can cancel or change the login bonus. `API_VERSION` is now 3.

### Changed
- The balance packet also carries the Battle Points, and the Trainer Card and the login bonus calendar have their own
  packets, so `PROTOCOL_VERSION`
  is now `1.2`: server and client must both run 1.2.0.
- Development runs load Cobblemon Battle Plus (the newest jar built in `../cobblemonbattleplus`) and, on Fabric,
  Text Placeholder API. Pass `-Pdev_battle_plus=false` to run without Battle Plus.

## 1.1.0 - 2026-11-23

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
- Purchase limits in `shops.json`: per entry, `limit` (bundles per player per `daily` / `weekly` / `never` period) and
  `stock` (shared stock that refills `restock_minutes` after the first sale). The shop screen shows what is left and
  when a sold-out stock returns; every purchase is checked on the server. Counts are saved in
  `data/cobblemon_economy_plus_purchases.dat`.
- `config/cobblemon_economy_plus/config.json` with `purchase_limits_enabled` to switch all limits off.
- `/cep shop restock` (refill every stock) and `/cep shop limits reset <player>` (clear a player's purchase counts).
- In-game admin screen (`/cep admin [shop]`, or the Shop Editor's new *Admin* mode): create, edit and delete shops
  (id, theme, buy-back multiplier, English / Japanese name and subtitle), their items (price, quantity, purchase limit,
  stock; add the held item, reorder) and sell values. Changes are validated on the server like the files, written with
  a `.bak` backup, reloaded at once and logged to `logs/cobblemon_economy_plus/admin.log`; saves made on outdated data
  are refused. New permission node `cobblemon_economy_plus.admin` (OP 2).

### Changed
- `/cep shop reload` reports problems in chat (up to 5, the rest in the server log) and how many item values were loaded.
- `/cep shop reload` and `/cep shop validate` also cover `rewards.json` and `config.json`.
- The problems they report (and the admin screen's save errors) are shown in the player's language (English or
  Japanese). The server log stays in English.
- A per-language `subtitle` without the viewer's language now shows the theme's default tagline in that language
  instead of the English (or first) subtitle. Plain-string subtitles and names are unchanged.
- Network protocol version is now `1.1` (shop entries carry purchase limits). **Update the mod on the server and all
  clients together.**

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
