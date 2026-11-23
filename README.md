# Cobblemon Economy Plus

English · [日本語](README.ja.md) · [Changelog](CHANGELOG.md)

An economy add-on for Cobblemon on Minecraft 1.21.1. It supports **both Fabric and NeoForge** and is designed
mainly for multiplayer servers.

- Requires: Minecraft 1.21.1, Cobblemon 1.8.4 or newer, and Fabric (with Fabric API) or NeoForge.
- **Install on both the server and the clients** (the shop screens live on the client).
  **Keep the mod version identical on both sides** — the network format is tied to the version.
- Cobblemon must be installed separately; this mod's JAR does not include it.

## Features

- Per-player currency stored persistently in the world (not an item, so it cannot be dropped, stolen or duplicated).
- A **shop screen** opened by right-clicking an NPC: buy / sell, category pager, quantity picker, 11 visual themes.
- **Selling:** items are bought back at an admin-defined *base value* multiplied by a per-shop *multiplier*.
- **Cobblemon rewards:** coins for winning wild and trainer battles (more for alphas), captures and Pokédex milestones, each switchable.
- A **daily login bonus** with a calendar screen: coins and items for each day, all configurable.
- A **Trainer Card** with your Pokédex count, play time, balance rank and balance (and Battle Points with
  Cobblemon Battle Plus).
- **Placeholders** for scoreboards, tab lists and holograms (Fabric, with Text Placeholder API).
- **Purchase limits and stock** per item: daily / weekly / once per player, and shared stock that restocks over time.
- An **in-game admin screen** to edit shops, items and sell values without touching the files.
- A **balance ranking** (`/cep top`).
- **Player-to-player payments** (`/pay`); balances of offline players can be managed too.
- **Permission support** for LuckPerms and similar mods, and a public **API** for other mods.
- A **transaction log** of every balance change.
- A top-right balance display (default: only when the balance changes) with count-up animation and sound.
- English and Japanese (shop names and subtitles can be set per language).

## Playing

- Right-click a **shopkeeper** (e.g. a villager spawned with the Shopkeeper Spawn Egg) to open the shop.
- Use the **Buy / Sell** switch at the top of the screen (only for shops that buy items).
  - Buy: pick an item, set the quantity (`-` `+`, Shift = steps of 5, mouse wheel), then press *Buy* (or Enter).
  - Sell: the items you can sell are listed. Click the quantity box to jump to the maximum / back to 1.
  - Only **unmodified items in your main inventory** can be sold (items with a custom name, enchantments,
    damage, stored contents, etc. are excluded).
- **Checking your balance:** by default it appears in the top-right corner for a few seconds when it changes.
  - Hold `B` (rebindable in Controls) to show it at any time.
  - `Shift + B` cycles the display mode: *on change* → *always* → *hidden* (saved in
    `config/cobblemon_economy_plus_client.json`).
- **Trainer Card** (creative-tab item): right-click to open your card. It shows the number of species in your
  Pokédex, your play time, your place in the balance ranking (`/cep top`) and your balance. With
  Cobblemon Battle Plus (3.0.0 or newer) installed on the server it also shows your
  Battle Points.

## Commands

| Command | Purpose | Permission node (default) |
|---|---|---|
| `/cep balance` | Show your own balance | `cobblemon_economy_plus.balance` (everyone) |
| `/cep balance <player>` | Show someone else's balance | `cobblemon_economy_plus.balance.others` (OP 2) |
| `/cep pay <player> <amount>`, `/pay <player> <amount>` | Pay another player | `cobblemon_economy_plus.pay` (everyone) |
| `/cep daily` | Show the login bonus calendar; also pays today's bonus if you have not received it yet (e.g. you stayed online past midnight) | `cobblemon_economy_plus.daily` (everyone) |
| `/cep top [page]` | Balance ranking, 10 players per page, with your own rank (offline players included) | `cobblemon_economy_plus.top` (everyone) |
| `/cep money get <player>` | Show a balance | `cobblemon_economy_plus.money` (OP 2) |
| `/cep money set <players> <amount>` | Set balances | same |
| `/cep money add <players> <amount>` | Add coins | same |
| `/cep money take <players> <amount>` | Remove coins (unchanged if the player cannot afford it) | same |
| `/cep history <player> [count]` | Show the player's latest transactions (default 10, max 50, UTC) | `cobblemon_economy_plus.history` (OP 2) |
| `/cep shop reload` / `/cep shop list` | Reload the config files (shops, item values, rewards, settings) / list shops. A file with errors keeps its previous settings | `cobblemon_economy_plus.shop` (OP 2) |
| `/cep shop validate` | Check shops.json, item_values.json, rewards.json and config.json without applying them (errors with line numbers, skipped entries, buy/sell loops) | same |
| `/cep shop restock` / `/cep shop limits reset <player>` | Refill every stock / clear a player's purchase counts | same |
| `/cep shopkeeper set <npc> <shop_id>` / `clear <npc>` | Link / unlink an NPC to a shop | `cobblemon_economy_plus.shopkeeper` (OP 2) |
| `/cep shopkeeper remove <npcs>` | Delete shopkeeper NPCs (other entities in the selection are skipped) | `cobblemon_economy_plus.shopkeeper.remove` (OP 2) |
| `/cep admin [shop]` | Open the in-game admin screen (shops, items, limits, sell values) | `cobblemon_economy_plus.admin` (OP 2) |

- Player arguments are **player names (offline players work too)**; selectors such as `@a` match online players.
- Using the Shop Editor item requires `cobblemon_economy_plus.editor` (OP 2). Removing shopkeepers with it
  (Shift + left-click twice) requires `cobblemon_economy_plus.shopkeeper.remove`.
- The defaults in parentheses apply when no permission manager has an opinion (OP level 2 = a command-capable admin).

## Permissions (LuckPerms etc.)

The nodes above can be granted or denied with LuckPerms or any compatible permission manager.

- **NeoForge:** uses the built-in permission API (nothing extra to install).
- **Fabric:** uses `fabric-permissions-api` (implemented by LuckPerms) automatically when present; otherwise OP levels apply.
- Command blocks and `execute as ...` keep working as in vanilla.

## Server administration

### Config files

Created in `config/cobblemon_economy_plus/` on first start. Apply edits with `/cep shop reload`.

**`shops.json`** — shop definitions

```json
{
  "shops": [
    {
      "id": "trainer_boutique",
      "name": { "en_us": "Trainer Boutique", "ja_jp": "トレーナーブティック" },
      "subtitle": { "en_us": "Trainer's Choice", "ja_jp": "ポケモントレーナー御用達" },
      "theme": "pokeball",
      "sell_multiplier": 1.2,
      "entries": [
        { "item": "cobblemon:poke_ball", "amount": 1, "price": 200 },
        { "item": "cobblemon:oran_berry", "amount": 4, "price": 80 }
      ]
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `id` | Shop id (lowercase letters, digits, `_`, `-`; up to 32 characters) |
| `name` / `subtitle` | A plain string, or per-language `{ "en_us": "...", "ja_jp": "..." }`, shown in the viewer's language. A name missing for the viewer's language uses another listed one; a subtitle missing for it (or omitted) uses the theme's default tagline in that language. A plain string is shown to everyone |
| `theme` | Screen look (see below). Defaults to `pokeball` |
| `sell_multiplier` | Multiplier for buy-back prices (0–100, default 1.0). **0 means the shop does not buy items** |
| `entries` | What the shop sells: `item` (item id), `amount` (items per bundle, 1–64), `price` (price per bundle) |

**Purchase limits and stock** (optional, per entry, 1.1.0+)

```json
{ "item": "cobblemon:master_ball", "amount": 1, "price": 50000,
  "limit": { "per_player": 1, "period": "weekly" },
  "stock": { "max": 3, "restock_minutes": 1440 } }
```

- `limit`: each player can buy at most `per_player` bundles per `period`: `daily`, `weekly` (weeks start on Monday) or
  `never` (once ever, the default). Days start at midnight server time.
- `stock`: `max` bundles shared by all players. `restock_minutes` after the first sale since the last restock, the stock
  is full again (0 = never restocks). With both, both apply.
- Counts are in bundles (the quantity picked in the shop). The shop screen shows what is left ("This week 1/2 left",
  "Stock 3", "Sold out (restock in 2h05m)"); the server checks every purchase.
- An entry whose `limit` or `stock` is invalid is skipped (rather than sold without it) and reported by reload / validate.
- Switch all limits off with `"purchase_limits_enabled": false` in `config.json` (counts are kept and apply again when
  switched back on). `/cep shop restock` refills every stock; `/cep shop limits reset <player>` clears a player's counts.

**`config.json`** — general settings

```json
{ "purchase_limits_enabled": true }
```

**`item_values.json`** — base values of sellable items

```json
{ "values": { "minecraft:diamond": 250, "cobblemon:poke_ball": 100 } }
```

Buy-back price = **base value × the shop's `sell_multiplier`** (rounded down). Items not listed here cannot be sold.
There are no per-item overrides — tune prices per shop with the multiplier.
**If any item sells for more than it costs to buy, a warning is written to the server log** on start/reload
(players could profit by buying and selling back).

### Cobblemon rewards (`rewards.json`)

Players earn coins for winning battles, catching Pokémon and filling the Pokédex (1.1.0+). The amount is shown above
the hotbar (action bar) and logged with a reason such as `reward:wild_battle`.

| Reward | Key | Default amount (level = the opposing Pokémon's level) |
|---|---|---|
| Wild battle won | `wild_battle` | 10 + level × 1 |
| Wild battle won (alpha) | `wild_battle_alpha` | 100 + level × 5 |
| Trainer (Cobblemon NPC) defeated | `trainer_battle` | 100 + highest level in the trainer's team × 5 |
| Capture | `capture` | 5 + level × 1 |
| Capture (alpha) | `capture_alpha` | 50 + level × 3 |
| Pokédex milestones (species caught) | `pokedex_milestones` | 500 at 10 species up to 50,000 at 1000 (see below) |
| Daily login bonus (1.2.0+) | `daily_login` | A 4-week calendar: 100 to 1,000 coins, balls and medicine mid-week, candies and a bigger prize every 7th day (see below) |

```json
{
  "enabled": true,
  "daily_cap": 0,
  "wild_battle":       { "enabled": true, "base": 10,  "per_level": 1 },
  "wild_battle_alpha": { "enabled": true, "base": 100, "per_level": 5 },
  "trainer_battle":    { "enabled": true, "base": 100, "per_level": 5 },
  "capture":           { "enabled": true, "base": 5,   "per_level": 1 },
  "capture_alpha":     { "enabled": true, "base": 50,  "per_level": 3 },
  "pokedex_milestones": {
    "enabled": true,
    "milestones": {
      "10": 500, "25": 1000, "50": 2500, "100": 5000, "150": 7500, "200": 10000,
      "300": 15000, "400": 20000, "500": 25000, "600": 30000, "700": 35000,
      "800": 40000, "900": 45000, "1000": 50000
    }
  },
  "daily_login": { ... see "Daily login bonus" below ... }
}
```

- Amount = `base + level × per_level`. Each reward has its own `enabled` switch; the top-level `enabled` turns them all off.
  Anything left out uses its default.
- Setting an alpha entry's `enabled` to `false` pays alphas like ordinary Pokémon (set `base` and `per_level` to 0 to pay nothing for them).
- `daily_cap`: the most a player can earn per day from battles and captures (0 = no limit). Resets at midnight server time.
  Pokédex milestones do not count towards it.
- Each Pokédex milestone is paid once per player. Milestones already passed before installing 1.1.0 are paid at the next login.
- Not rewarded: player-versus-player battles, lost or fled battles, and the battle a Pokémon was caught in (only the capture pays).
- NPCs with the tag `cep_no_reward` pay no trainer reward (e.g. `/tag @e[type=cobblemon:npc,limit=1,sort=nearest] add cep_no_reward`).
- If your server has NPCs that can be battled again and again, use `daily_cap` or `cep_no_reward` to prevent farming.
- Other mods can cancel a reward or change its amount before it is paid (see *Events* below).

#### Daily login bonus (1.2.0+)

The first login of each day (server time) pays the next day of a calendar you define. Each day can pay coins, items,
or both; the default is four weeks (28 days). A calendar screen then shows every day's rewards, ticks off the days already received and highlights today;
players can open it again with `/cep daily`. The example shows the first week of the default calendar:

```json
"daily_login": {
  "enabled": true,
  "show_screen": true,
  "reset_on_miss": true,
  "repeat": true,
  "days": [
    { "coins": 100 },
    { "coins": 150 },
    { "coins": 200, "items": [ { "item": "cobblemon:poke_ball", "count": 5 } ] },
    { "coins": 250 },
    { "coins": 300, "items": [ { "item": "cobblemon:great_ball", "count": 5 } ] },
    { "coins": 400 },
    { "coins": 500, "items": [ { "item": "cobblemon:ultra_ball", "count": 3 },
                               { "item": "cobblemon:rare_candy", "count": 1 } ] }
  ]
}
```

- `days`: 1 to 60 days. Each day has `coins` (optional) and `items` (optional, up to 8; `count` 1–6400, default 1).
  Any item id works, including other mods' items. Unknown ids are reported by `/cep shop reload` and `validate` and
  are not given.
- `reset_on_miss`: `true` = missing a day starts again from day 1; `false` = every login day counts, gaps or not.
- `repeat`: `true` = after the last day, start again from day 1; `false` = keep paying the last day.
- `show_screen`: open the calendar when the bonus is paid (`false` = only the chat message; `/cep daily` still works).
- Items that do not fit the inventory are dropped at the player's feet. The bonus does not count towards `daily_cap`.
- It is paid when the player joins. A player who stays online past midnight can get it with `/cep daily` (or at their
  next login).

### Admin screen (1.1.0+)

Edit shops and sell values in game instead of editing the JSON files. Open it with `/cep admin [shop]`, or put the
**Shop Editor** in *Admin* mode (right-click in the air to cycle modes) and right-click a shopkeeper (opens its shop)
or Shift + right-click in the air. Requires `cobblemon_economy_plus.admin` (OP 2).

- **Shops tab:** pick a shop on the left, or create one. Edit the id (new shops only), theme, buy-back multiplier,
  name and subtitle (English column on the left, Japanese on the right; a blank name uses the other language, or the
  shop id when both are blank; a blank subtitle uses the theme's default in that language — shown in grey in the box), and the items' price and quantity. *Add held item* adds what you hold; ↑ ↓ reorder; *Save*
  applies the shop.
- **Advanced:** shows each item's purchase limit (count + daily / weekly / once) and stock (count + restock minutes),
  and the Restock / Reset limits tools. Items that already have a limit or stock show it in the list either way.
- **Sell values tab:** search the item values, pick one (or the held item) and set or remove its value.
- **Restock** and **Reset limits** (with a player name; both under *Advanced*) run `/cep shop restock` and `/cep shop limits reset`.
- Destructive buttons (delete, restock, reset, discard changes) need a second press within 3 seconds.

How saving works:
- The server checks every change with the same rules as the files and refuses anything the file loader would skip or
  change (unknown item, bad number, unknown theme ...).
- The file is rewritten with the edited shop only; other shops stay as they are, including ones the loader skipped.
  The previous file is kept as `shops.json.bak` / `item_values.json.bak`, and the file is reloaded right away.
  Saved files are re-formatted (indentation, key order); the content, including per-language names, is kept.
- If someone else changed the shops after you opened the screen, your save is refused, the latest shops are loaded
  and your edits stay on screen so you can check them and save again.
- Open shop screens are refreshed. Every edit is logged to `logs/cobblemon_economy_plus/admin.log`
  (time / admin / UUID / action / detail).
- Shop ids cannot be renamed (shopkeepers are linked by id). NPCs linked to a deleted shop show "shop not found" until
  they are given another shop.

### Themes

`pokeball`, `greatball`, `ultraball`, `masterball`, `tm`, `berry`, `stone`, `wood`, `dirt`, `ore`, `iron`

A theme can also be overridden per NPC with the **Shop Editor** (creative tab): right-click in the air to switch
between *Change shop*, *Change theme* and *Admin* mode, then right-click an NPC for the next option, Shift + right-click for the previous
(in *Admin* mode, right-clicking an NPC opens the admin screen on its shop).

### Shopkeepers

- The **Shopkeeper Spawn Egg** (creative tab) spawns a villager already linked to the `general` shop; it stays in place
  and cannot be hurt.
- To turn any living NPC into a shopkeeper:

```mcfunction
/cep shopkeeper set @e[type=minecraft:villager,limit=1,sort=nearest] trainer_boutique
/cep shopkeeper clear @e[type=minecraft:villager,limit=1,sort=nearest]
```

- Any shopkeeper (including NPCs linked with the command) ignores attacks and damage; only `/kill` and the void bypass this.
- To delete a shopkeeper, Shift + left-click it twice with the Shop Editor (within 5 seconds), or run
  `/cep shopkeeper remove @e[tag=cep_shop:general]` and similar. `clear` only unlinks the shop and keeps the NPC.

Shopkeepers are linked through scoreboard tags.

### Transaction log

Every balance change (purchase, sale, payment, command, other mods' API calls) is appended to:

`logs/cobblemon_economy_plus/transactions.log`

One tab-separated line per change: `timestamp (UTC) / type / UUID / player name / delta / balance after / source / detail`.
The file rotates to `transactions.log.1`–`.5` once it exceeds 8 MB.
In game, `/cep history <player> [count]` shows a player's latest transactions (offline player names work too).
The player-name column is only filled when that player was online at the time; if a name search finds nothing, search by UUID (look it up in `usercache.json` in the server folder).

### Placeholders (Fabric, 1.2.0+)

With [Text Placeholder API](https://modrinth.com/mod/placeholder-api) installed, mods that use it (scoreboards, tab
lists, chat formats, holograms) can show:

| Placeholder | Shows |
|---|---|
| `%cobblemon_economy_plus:balance%` | The player's balance (`1,234`) |
| `%cobblemon_economy_plus:balance_raw%` | The same as a plain number (`1234`) |
| `%cobblemon_economy_plus:rank%` | The player's place in `/cep top` (`-` with no coins) |
| `%cobblemon_economy_plus:top_name 1%` | The name of the 1st player in `/cep top` (any position) |
| `%cobblemon_economy_plus:top_balance 1%` | Their balance |

The ranking behind `rank` and `top_*` is refreshed every few seconds. NeoForge has no common placeholder mod, so
these are Fabric only.

### Saved data

Balances are stored per UUID in the world data (`data/cobblemon_economy_plus_balances.dat`).
Reward bookkeeping (Pokédex milestones already paid, today's reward total, login streak) is in `data/cobblemon_economy_plus_rewards.dat`.
Purchase counts and stock are in `data/cobblemon_economy_plus_purchases.dat`.
The format carries a version number so future updates can migrate existing worlds.
**Back up your world before updating.**

## Using it from other mods (API)

Server-side API: `com.diamo.cobblemoneconomyplus.api.CobblemonEconomyApi` (call from the server thread only).

```java
long balance = CobblemonEconomyApi.getBalance(server, playerUuid);
CobblemonEconomyApi.deposit(server, playerUuid, 500, "mymod:quest_reward");
boolean ok = CobblemonEconomyApi.withdraw(server, playerUuid, 200, "mymod:entry_fee");
boolean moved = CobblemonEconomyApi.transfer(server, fromUuid, toUuid, 100, "mymod:trade");
```

- Methods take UUIDs, so offline players work; changes are synced to online clients automatically.
- The trailing `reason` is written to the transaction log (defaults to `api`).
- Command blocks and datapacks can use `/cep money ...` and `/cep pay ...`.
  Mods that run reward commands from their config can pay coins with `cep money add <player> <amount>`.

### Events (1.1.0+, `CobblemonEconomyApi.API_VERSION >= 2`)

```java
// After every balance change, whatever caused it (shops, rewards, commands, other mods).
CobblemonEconomyApi.onBalanceChanged(change ->
        LOGGER.info("{}: {} -> {} ({})", change.playerId(), change.previousBalance(), change.newBalance(), change.reason()));

// Before a battle / capture / Pokédex reward is paid: cancel it or change the amount.
CobblemonEconomyApi.onReward(event -> {
    if (event.battleId() != null && MyFacility.isFacilityBattle(event.battleId())) {
        event.cancel(); // the facility pays its own rewards, so do not pay twice
    }
});
```

- Register listeners once, during mod initialisation. They run on the server thread; one that throws is logged and skipped.
- `RewardEvent` gives the reward type, player, amount, whether the Pokémon was an alpha, the level, the battle id and
  the NPC trainer entity (for trainer battles).
- 1.2.0 (`API_VERSION >= 3`) adds the `DAILY_LOGIN` reward type: `milestone()` is which login day it is, the amount
  is the day's coins, and cancelling it also withholds the day's items.

### Optional dependency

To work with or without this mod installed, only touch `com.diamo.cobblemoneconomyplus` classes from a separate class
that you load after checking that the mod is present (mod id `cobblemon_economy_plus`), for example with
`FabricLoader.getInstance().isModLoaded(...)` or NeoForge's `ModList.get().isLoaded(...)`.

## Building

Requires JDK 21.

```powershell
.\gradlew.bat build
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```

Output: `build/libs_fabric/` and `build/libs_neoforge/`.

Development runs also load Cobblemon Battle Plus to test the Trainer Card integration. They use the newest jar built in
the sibling folder `../cobblemonbattleplus`, so build that project first. Pass `-Pdev_battle_plus=false` to run
without it (see `gradle.properties`). On Fabric, Text Placeholder API is loaded too.

### Testing multiplayer locally

You can run a dedicated server and up to two clients on one machine (use the same loader for all of them):

```powershell
.\gradlew.bat :neoforge:runServer     # or :fabric:runServer
.\gradlew.bat :neoforge:runClient     # first player
.\gradlew.bat :neoforge:runClient2    # second player "Player001", own folder run/client2/
```

Set `online-mode=false` in `run/server/1.21.1/server.properties` (and accept the EULA in `eula.txt`), then join
`localhost` from each client. The second client has its own game directory and a 3 GB heap, so the two clients
do not interfere with each other.

**If you change any network payload, bump `EconomyPayloads.PROTOCOL_VERSION`.**
NeoForge rejects clients with a different version; Fabric has no equivalent check, so keep versions in sync there.

## License

Released under the MIT License - see [LICENSE](LICENSE).
