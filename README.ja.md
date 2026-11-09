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
| `/cep shop reload` / `/cep shop list` | ショップ設定の再読込 / 一覧 | `cobblemon_economy_plus.shop`(OP2) |
| `/cep shopkeeper set <npc> <shop_id>` / `clear <npc>` | NPC にショップを設定 / 解除 | `cobblemon_economy_plus.shopkeeper`(OP2) |

- プレイヤー指定は**プレイヤー名(オフラインでも可)**です。`@a` などのセレクターはオンラインのプレイヤーに一致します。
- ショップエディター(アイテム)の使用には `cobblemon_economy_plus.editor`(OP2)が必要です。
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

### テーマ(デザイン)

`pokeball`(モンスターボール) / `greatball` / `ultraball` / `masterball` / `tm`(わざマシン) / `berry`(きのみ) /
`stone` / `wood` / `dirt` / `ore` / `iron`

NPC ごとに上書きもできます。**ショップエディター**(クリエイティブタブ)を持って、空中で右クリックするとモードが
「ショップ変更」⇄「テーマ変更」に切り替わり、NPC を右クリックで次へ、Shift + 右クリックで前へ切り替わります。

### 店員(ショップ店員NPC)

- クリエイティブタブの**ショップ店員スポーンエッグ**で、`general` ショップに接続済みの村人が出ます(その場に留まり、通常のダメージでは失われません)。
- 任意の生き物 NPC を店員にするには:

```mcfunction
/cep shopkeeper set @e[type=minecraft:villager,limit=1,sort=nearest] trainer_boutique
/cep shopkeeper clear @e[type=minecraft:villager,limit=1,sort=nearest]
```

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
