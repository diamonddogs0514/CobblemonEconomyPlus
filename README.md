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
- **Trainer Card** (creative-tab item, draft): right-click to open a card with your balance.

## Commands

| Command | Purpose | Permission node (default) |
|---|---|---|
| `/cep balance` | Show your own balance | `cobblemon_economy_plus.balance` (everyone) |
| `/cep balance <player>` | Show someone else's balance | `cobblemon_economy_plus.balance.others` (OP 2) |
| `/cep pay <player> <amount>`, `/pay <player> <amount>` | Pay another player | `cobblemon_economy_plus.pay` (everyone) |
| `/cep money get <player>` | Show a balance | `cobblemon_economy_plus.money` (OP 2) |
| `/cep money set <players> <amount>` | Set balances | same |
| `/cep money add <players> <amount>` | Add coins | same |
| `/cep money take <players> <amount>` | Remove coins (unchanged if the player cannot afford it) | same |
| `/cep history <player> [count]` | Show the player's latest transactions (default 10, max 50, UTC) | `cobblemon_economy_plus.history` (OP 2) |
| `/cep shop reload` / `/cep shop list` | Reload shop config / list shops | `cobblemon_economy_plus.shop` (OP 2) |
| `/cep shopkeeper set <npc> <shop_id>` / `clear <npc>` | Link / unlink an NPC to a shop | `cobblemon_economy_plus.shopkeeper` (OP 2) |

- Player arguments are **player names (offline players work too)**; selectors such as `@a` match online players.
- Using the Shop Editor item requires `cobblemon_economy_plus.editor` (OP 2).
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
| `name` / `subtitle` | A plain string, or per-language `{ "en_us": "...", "ja_jp": "..." }`, shown in the viewer's language. Omit `subtitle` for the theme's default tagline |
| `theme` | Screen look (see below). Defaults to `pokeball` |
| `sell_multiplier` | Multiplier for buy-back prices (0–100, default 1.0). **0 means the shop does not buy items** |
| `entries` | What the shop sells: `item` (item id), `amount` (items per bundle, 1–64), `price` (price per bundle) |

**`item_values.json`** — base values of sellable items

```json
{ "values": { "minecraft:diamond": 250, "cobblemon:poke_ball": 100 } }
```

Buy-back price = **base value × the shop's `sell_multiplier`** (rounded down). Items not listed here cannot be sold.
There are no per-item overrides — tune prices per shop with the multiplier.
**If any item sells for more than it costs to buy, a warning is written to the server log** on start/reload
(players could profit by buying and selling back).

### Themes

`pokeball`, `greatball`, `ultraball`, `masterball`, `tm`, `berry`, `stone`, `wood`, `dirt`, `ore`, `iron`

A theme can also be overridden per NPC with the **Shop Editor** (creative tab): right-click in the air to switch
between *Change shop* and *Change theme* mode, then right-click an NPC for the next option, Shift + right-click for the previous.

### Shopkeepers

- The **Shopkeeper Spawn Egg** (creative tab) spawns a villager already linked to the `general` shop; it stays in place
  and survives ordinary damage.
- To turn any living NPC into a shopkeeper:

```mcfunction
/cep shopkeeper set @e[type=minecraft:villager,limit=1,sort=nearest] trainer_boutique
/cep shopkeeper clear @e[type=minecraft:villager,limit=1,sort=nearest]
```

Shopkeepers are linked through scoreboard tags.

### Transaction log

Every balance change (purchase, sale, payment, command, other mods' API calls) is appended to:

`logs/cobblemon_economy_plus/transactions.log`

One tab-separated line per change: `timestamp (UTC) / type / UUID / player name / delta / balance after / source / detail`.
The file rotates to `transactions.log.1`–`.5` once it exceeds 8 MB.
In game, `/cep history <player> [count]` shows a player's latest transactions (offline player names work too).
The player-name column is only filled when that player was online at the time; if a name search finds nothing, search by UUID (look it up in `usercache.json` in the server folder).

### Saved data

Balances are stored per UUID in the world data (`data/cobblemon_economy_plus_balances.dat`).
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

## Building

Requires JDK 21.

```powershell
.\gradlew.bat build
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```

Output: `build/libs_fabric/` and `build/libs_neoforge/`.

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
