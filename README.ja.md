# Cobblemon Economy Plus

[English](README.md) · 日本語 · [更新履歴](CHANGELOG.md)

Cobblemon に経済の仕組みを加える、Minecraft 1.21.1 向けの Mod です。**Fabric と NeoForge の両対応**で、
主にマルチプレイのサーバーでの利用を想定しています。

- 動作環境: Minecraft 1.21.1 / Cobblemon 1.8.4 以上 / Fabric(Fabric API 必須)または NeoForge
- **サーバーとクライアントの両方に導入してください**(ショップ画面などがクライアント側にもあるため)。
  サーバーとクライアントで **Mod の版を揃えてください**(通信形式が版ごとに決まっています)。
- Cobblemon は別途導入が必要です。この Mod の JAR に Cobblemon は含まれません。

## 主な機能

- プレイヤーごとの通貨を、ワールドに永続保存(アイテム化しないので、落とす・盗まれる・複製される心配がありません)
- NPC に話しかけて開く**ショップ画面**: 買う / 売る、カテゴリ切替、数量指定、11種類のデザイン(テーマ)
- 売却: 管理者が登録した「基準額」に、ショップごとの**倍率**を掛けた価格でアイテムを買い取ります
- **Cobblemon の報酬**: 野生・トレーナーとのバトルの勝利(親分は増額)、捕獲、図鑑の節目でコインがもらえます(それぞれ切り替え可能)
- プレイヤー間の**送金**(`/pay`)、オフラインのプレイヤーの所持金も管理可能
- LuckPerms などの**権限管理**に対応、他の Mod から使える**公開 API**
- すべての残高の変動を記録する**取引ログ**
- 右上の所持金表示(既定は「変動時のみ」)と、増減のアニメーション・効果音
- 日本語・英語に対応(ショップ名なども言語ごとに設定可能)

## 遊び方(プレイヤー向け)

- **ショップ店員**(ショップ店員スポーンエッグで出した村人など)を右クリックするとショップが開きます。
- 画面上部の **「買う / 売る」** で切り替えます(買取のあるショップのみ)。
  - 買う: 商品を選び、数量(`-` `+`、Shift で 5 刻み、マウスホイール)を決めて「購入する」(または Enter)。
  - 売る: 手持ちの売れるアイテムが一覧に出ます。数量欄をクリックすると最大数/1 に切り替わります。
  - 売れるのは、**メインインベントリにある未加工のアイテム**だけです(名前・エンチャント・耐久値・中身などが付いたものは対象外)。
- **所持金の確認**: 既定では、所持金が変わったときだけ右上に数秒表示されます。
  - `B` キー(コントロール設定で変更可)を押している間は、いつでも表示します。
  - `Shift + B` で表示モードが「変動時のみ」→「常に表示」→「非表示」と切り替わります(設定は
    `config/cobblemon_economy_plus_client.json` に保存されます)。
- **トレーナーカード**(クリエイティブタブのアイテム・下書き): 右クリックで所持金などのカードを開きます。

## コマンド

| コマンド | 内容 | 権限ノード(既定) |
|---|---|---|
| `/cep balance` | 自分の所持金を確認 | `cobblemon_economy_plus.balance`(全員) |
| `/cep balance <player>` | 他のプレイヤーの所持金を確認 | `cobblemon_economy_plus.balance.others`(OP2) |
| `/cep pay <player> <amount>`、`/pay <player> <amount>` | 送金 | `cobblemon_economy_plus.pay`(全員) |
| `/cep money get <player>` | 所持金を確認 | `cobblemon_economy_plus.money`(OP2) |
| `/cep money set <players> <amount>` | 所持金を設定 | 同上 |
| `/cep money add <players> <amount>` | 所持金を増やす | 同上 |
| `/cep money take <players> <amount>` | 所持金を減らす(不足なら変更しない) | 同上 |
| `/cep history <player> [件数]` | そのプレイヤーの直近の取引を表示(既定10件・最大50件、UTC) | `cobblemon_economy_plus.history`(OP2) |
| `/cep shop reload` / `/cep shop list` | 設定ファイル(ショップ・売却価格・報酬)の再読込 / ショップの一覧。エラーのあるファイルは前の設定のまま | `cobblemon_economy_plus.shop`(OP2) |
| `/cep shop validate` | shops.json・item_values.json・rewards.json を反映せずに検査(行番号つきのエラー、飛ばした項目、売り買いで儲かる設定) | 同上 |
| `/cep shopkeeper set <npc> <shop_id>` / `clear <npc>` | NPC にショップを設定 / 解除 | `cobblemon_economy_plus.shopkeeper`(OP2) |
| `/cep shopkeeper remove <npcs>` | 店員 NPC を削除(選択に含まれる店員以外は無視) | `cobblemon_economy_plus.shopkeeper.remove`(OP2) |

- プレイヤー指定は**プレイヤー名(オフラインでも可)**です。`@a` などのセレクターはオンラインのプレイヤーに一致します。
- ショップエディター(アイテム)の使用には `cobblemon_economy_plus.editor`(OP2)が必要です。エディターでの店員削除(Shift + 左クリック 2 回)には
  `cobblemon_economy_plus.shopkeeper.remove` が必要です。
- 括弧内は、権限管理 Mod が何も設定していないときの既定です(OP レベル2 = コマンド使用可能な管理者)。

## 権限管理(LuckPerms など)

上表の権限ノードを、LuckPerms などの権限管理 Mod で許可/拒否できます。

- **NeoForge:** 標準の権限 API を使います(追加の導入は不要)。
- **Fabric:** `fabric-permissions-api`(LuckPerms が実装)が入っていれば自動で使います。無ければ OP レベルで動作します。
- コマンドブロックや `execute as` 経由の実行は、従来どおり動作します。

## サーバー運営

### 設定ファイル

初回起動時に `config/cobblemon_economy_plus/` に自動作成されます。編集後は `/cep shop reload` で反映します。

**`shops.json`** — ショップの定義

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

| 項目 | 内容 |
|---|---|
| `id` | ショップID(半角小文字・数字・`_`・`-`、32文字まで) |
| `name` / `subtitle` | 文字列、または言語ごとの指定 `{ "en_us": "...", "ja_jp": "..." }`。閲覧者の言語で表示されます。`subtitle` を省略するとテーマ標準の文になります |
| `theme` | 画面のデザイン(下記)。省略時は `pokeball` |
| `sell_multiplier` | 買取価格の倍率(0〜100、既定 1.0)。**0 でそのショップは買取しません** |
| `entries` | 売る商品。`item`(アイテムID)、`amount`(1セットの個数、1〜64)、`price`(1セットの価格) |

**`item_values.json`** — 売却できるアイテムの「基準額」

```json
{ "values": { "minecraft:diamond": 250, "cobblemon:poke_ball": 100 } }
```

買取価格 = **基準額 × そのショップの `sell_multiplier`**(小数点以下切り捨て)。ここに載っていないアイテムは売れません。
アイテム単位の個別価格はありません(ショップごとの倍率で調整します)。
**買値より買取価格が高いアイテムがあると、起動時・再読込時にサーバーログへ警告が出ます**(買って売るだけで儲かる設定です)。

### Cobblemon の報酬(`rewards.json`)

バトルに勝つ・ポケモンを捕まえる・図鑑を埋めると、コインがもらえます(1.1.0 以降)。もらった額は画面下(アクションバー)に表示され、
取引ログには `reward:wild_battle` などの理由で記録されます。

| 種類 | 設定の名前 | 既定の金額(レベルは相手のポケモンのレベル) |
|---|---|---|
| 野生バトルの勝利 | `wild_battle` | 10 + レベル × 1 |
| 野生バトルの勝利(親分) | `wild_battle_alpha` | 100 + レベル × 5 |
| トレーナー(Cobblemon の NPC)に勝利 | `trainer_battle` | 100 + 相手の手持ちの最高レベル × 5 |
| 捕獲 | `capture` | 5 + レベル × 1 |
| 捕獲(親分) | `capture_alpha` | 50 + レベル × 3 |
| 図鑑の節目(捕まえた種類の数) | `pokedex_milestones` | 10 種で 500 〜 1000 種で 50,000(下の例) |

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
  }
}
```

- 金額は `base + レベル × per_level`。種類ごとに `enabled` で切り替えられ、一番上の `enabled` で全部まとめて止められます。
  書いていない項目は既定の値になります。
- 親分の項目を `false` にすると、親分も普通のポケモンと同じ金額になります(親分だけ報酬なしにしたいときは `base` と `per_level` を 0 に)。
- `daily_cap`: 1 人が 1 日にバトル・捕獲でもらえる合計の上限(0 = 上限なし)。サーバーの時刻の 0 時にリセットされます。図鑑の節目は対象外です。
- 図鑑の節目は 1 人 1 回だけです。1.1.0 を入れる前に越えていた節目は、次にログインしたときにまとめて支払われます。
- 対象外: プレイヤー同士のバトル、負けた・逃げたとき、バトル中に捕まえたポケモン(捕獲の報酬だけ出ます)。
- タグ `cep_no_reward` の付いた NPC は、勝ってもトレーナーの報酬が出ません(例: `/tag @e[type=cobblemon:npc,limit=1,sort=nearest] add cep_no_reward`)。
- 何度でも戦える NPC がいるサーバーでは、`daily_cap` か `cep_no_reward` で稼ぎすぎを防いでください。
- 他の Mod は、報酬を払う前に取り消したり金額を変えたりできます(下の「イベント」)。

### テーマ(デザイン)

`pokeball`(モンスターボール) / `greatball` / `ultraball` / `masterball` / `tm`(わざマシン) / `berry`(きのみ) /
`stone` / `wood` / `dirt` / `ore` / `iron`

NPC ごとに上書きもできます。**ショップエディター**(クリエイティブタブ)を持って、空中で右クリックするとモードが
「ショップ変更」⇄「テーマ変更」に切り替わり、NPC を右クリックで次へ、Shift + 右クリックで前へ切り替わります。

### 店員(ショップ店員NPC)

- クリエイティブタブの**ショップ店員スポーンエッグ**で、`general` ショップに接続済みの村人が出ます(その場に留まり、ダメージを受けません)。
- 任意の生き物 NPC を店員にするには:

```mcfunction
/cep shopkeeper set @e[type=minecraft:villager,limit=1,sort=nearest] trainer_boutique
/cep shopkeeper clear @e[type=minecraft:villager,limit=1,sort=nearest]
```

- 店員(コマンドで設定した NPC を含む)は攻撃・ダメージを受けません。例外は `/kill` と奈落だけです。
- 店員を削除するには、ショップエディターで 5 秒以内に 2 回 Shift + 左クリックするか、
  `/cep shopkeeper remove @e[tag=cep_shop:general]` などを実行します。`clear` はショップの紐付けだけを外し、NPC は残します。

店員はスコアボードタグで紐付けられます。

### 取引ログ

すべての残高の変動(購入・売却・送金・コマンド・他の Mod の API)が、次のファイルに追記されます。

`logs/cobblemon_economy_plus/transactions.log`

タブ区切りの 1 行 1 件です: `日時(UTC) / 種類 / UUID / プレイヤー名 / 増減 / 変動後の残高 / 発生元 / 詳細`。
8 MB を超えると `transactions.log.1`〜`.5` に回転します。
ゲーム内からは `/cep history <player> [件数]` で、特定のプレイヤーの直近の取引を確認できます(オフラインのプレイヤー名も可)。
プレイヤー名の欄は、記録した時にそのプレイヤーがオンラインだった場合だけ入ります。名前で見つからないときは UUID(サーバーフォルダの `usercache.json` で調べられます)で検索してください。

### 保存データ

残高はワールドのデータ(`data/cobblemon_economy_plus_balances.dat`)に UUID 単位で保存されます。
報酬の記録(受け取り済みの図鑑の節目、その日の報酬の合計)は `data/cobblemon_economy_plus_rewards.dat` に保存されます。
データ形式に版番号を持たせてあり、将来の更新で既存のワールドを移行できるようにしています。
**更新前にワールドのバックアップを取ってください。**

## 他の Mod から使う(API)

サーバー側 API: `com.diamo.cobblemoneconomyplus.api.CobblemonEconomyApi`(サーバースレッドから呼ぶこと)

```java
long balance = CobblemonEconomyApi.getBalance(server, playerUuid);
CobblemonEconomyApi.deposit(server, playerUuid, 500, "mymod:quest_reward");
boolean ok = CobblemonEconomyApi.withdraw(server, playerUuid, 200, "mymod:entry_fee");
boolean moved = CobblemonEconomyApi.transfer(server, fromUuid, toUuid, 100, "mymod:trade");
```

- UUID 指定なので、オフラインのプレイヤーにも使えます。変更はオンラインのクライアントへ自動で同期されます。
- 最後の引数 `reason` は取引ログに記録されます(省略すると `api`)。
- コマンドブロックやデータパックからは `/cep money ...` と `/cep pay ...` を使えます。
  設定ファイルで報酬コマンドを実行できる Mod なら、`cep money add <player> <amount>` でコインを配れます。

### イベント(1.1.0 以降、`CobblemonEconomyApi.API_VERSION >= 2`)

```java
// 残高が変わるたびに呼ばれる(ショップ・報酬・コマンド・他の Mod など、原因を問わない)
CobblemonEconomyApi.onBalanceChanged(change ->
        LOGGER.info("{}: {} -> {} ({})", change.playerId(), change.previousBalance(), change.newBalance(), change.reason()));

// バトル・捕獲・図鑑の報酬を払う前に呼ばれる。取り消しや金額の変更ができる
CobblemonEconomyApi.onReward(event -> {
    if (event.battleId() != null && MyFacility.isFacilityBattle(event.battleId())) {
        event.cancel(); // 施設が自前で報酬を出すので、二重に払わない
    }
});
```

- リスナーは Mod の初期化時に 1 回だけ登録してください。サーバースレッドで呼ばれます。例外を出したリスナーは、ログに出して飛ばします。
- `RewardEvent` からは、報酬の種類・プレイヤー・金額・親分かどうか・レベル・バトルの ID・トレーナー NPC(トレーナー戦のとき)を取れます。

### 任意の依存にする

この Mod が入っていなくても動かすには、`com.diamo.cobblemoneconomyplus` のクラスに触れる処理を別のクラスに分け、
Mod(mod id `cobblemon_economy_plus`)が入っていることを確かめてから呼んでください
(例: `FabricLoader.getInstance().isModLoaded(...)`、NeoForge の `ModList.get().isLoaded(...)`)。

## 開発・ビルド

JDK 21 が必要です。

```powershell
.\gradlew.bat build
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```

出力先: Fabric は `build/libs_fabric/`、NeoForge は `build/libs_neoforge/`。

### ローカルでのマルチプレイ確認

1 台のパソコンで、専用サーバーとクライアントを 2 つまで同時に起動できます(ローダーは全部同じものにしてください)。

```powershell
.\gradlew.bat :neoforge:runServer     # または :fabric:runServer
.\gradlew.bat :neoforge:runClient     # 1 人目
.\gradlew.bat :neoforge:runClient2    # 2 人目(プレイヤー名 Player001、別フォルダ run/client2/)
```

`run/server/1.21.1/server.properties` で `online-mode=false` にし(`eula.txt` の同意も必要です)、各クライアントから
`localhost` に接続します。2 つ目のクライアントは、専用のゲームフォルダを使い、メモリも 3 GB に抑えてあるので、
互いに干渉しません。

**通信形式(パケット)を変えたら、`EconomyPayloads.PROTOCOL_VERSION` を必ず上げてください。**
NeoForge は版が違うクライアントの接続を拒否します。Fabric には同様の仕組みがないため、版を揃える運用が必要です。

## ライセンス

MIT License です。[LICENSE](LICENSE) を参照してください。
