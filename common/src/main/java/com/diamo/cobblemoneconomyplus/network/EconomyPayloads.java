package com.diamo.cobblemoneconomyplus.network;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** All custom play payloads shared by Fabric and NeoForge. */
public final class EconomyPayloads {
    /**
     * Network protocol version, checked by NeoForge when a client joins (a mismatch refuses the connection
     * with a clear message). <b>Bump this whenever any payload's wire format changes.</b> Fabric has no
     * built-in version check, so keep client and server on the same mod version there.
     */
    public static final String PROTOCOL_VERSION = "1.1";

    private static final int MAX_SHOP_ID_LENGTH = 32;
    private static final int MAX_NAME_LENGTH = 512;
    private static final int MAX_ITEM_ID_LENGTH = 128;
    private static final int MAX_ENTRIES = 120;
    private static final int MAX_SELL_ENTRIES = 512;

    private EconomyPayloads() {
    }

    public record BalancePayload(long balance) implements CustomPacketPayload {
        public static final Type<BalancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CobblemonEconomyPlus.MOD_ID, "balance"
        ));
        public static final StreamCodec<RegistryFriendlyByteBuf, BalancePayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeVarLong(payload.balance),
                buffer -> new BalancePayload(Math.max(0L, buffer.readVarLong()))
        );

        @Override
        public Type<BalancePayload> type() {
            return TYPE;
        }
    }

    /**
     * Opens a shop. {@code entries} is what the shop sells; {@code sellEntries} is what it buys from players
     * (each with {@code amount = 1} and the per-item price this shop pays).
     */
    public record OpenShopPayload(String shopId, String shopName, String theme, String subtitle, long balance,
                                  List<ShopEntryPayload> entries, List<ShopEntryPayload> sellEntries)
            implements CustomPacketPayload {
        public static final Type<OpenShopPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CobblemonEconomyPlus.MOD_ID, "open_shop"
        ));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenShopPayload> STREAM_CODEC = StreamCodec.of(
                EconomyPayloads::writeOpenShop,
                EconomyPayloads::readOpenShop
        );

        public OpenShopPayload {
            entries = List.copyOf(entries);
            sellEntries = List.copyOf(sellEntries);
        }

        @Override
        public Type<OpenShopPayload> type() {
            return TYPE;
        }
    }

    /**
     * A purchase request. {@code itemId}, {@code amount} and {@code price} are what the client displayed for
     * {@code entryIndex}; the server rejects the request if the shop no longer matches (e.g. after a reload).
     */
    public record ShopActionPayload(String shopId, int entryIndex, int quantity, String itemId, int amount, long price)
            implements CustomPacketPayload {
        public static final Type<ShopActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CobblemonEconomyPlus.MOD_ID, "shop_action"
        ));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopActionPayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUtf(payload.shopId, MAX_SHOP_ID_LENGTH);
                    buffer.writeVarInt(payload.entryIndex);
                    buffer.writeVarInt(payload.quantity);
                    buffer.writeUtf(payload.itemId, MAX_ITEM_ID_LENGTH);
                    buffer.writeVarInt(payload.amount);
                    buffer.writeVarLong(payload.price);
                },
                buffer -> new ShopActionPayload(
                        buffer.readUtf(MAX_SHOP_ID_LENGTH),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readUtf(MAX_ITEM_ID_LENGTH),
                        buffer.readVarInt(),
                        buffer.readVarLong()
                )
        );

        @Override
        public Type<ShopActionPayload> type() {
            return TYPE;
        }
    }

    /**
     * A sale request. {@code unitPrice} is what the client displayed; the server rejects the request if the
     * shop's price for the item has changed in the meantime.
     */
    public record SellActionPayload(String shopId, String itemId, int quantity, long unitPrice)
            implements CustomPacketPayload {
        public static final Type<SellActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CobblemonEconomyPlus.MOD_ID, "sell_action"
        ));
        public static final StreamCodec<RegistryFriendlyByteBuf, SellActionPayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUtf(payload.shopId, MAX_SHOP_ID_LENGTH);
                    buffer.writeUtf(payload.itemId, MAX_ITEM_ID_LENGTH);
                    buffer.writeVarInt(payload.quantity);
                    buffer.writeVarLong(payload.unitPrice);
                },
                buffer -> new SellActionPayload(
                        buffer.readUtf(MAX_SHOP_ID_LENGTH),
                        buffer.readUtf(MAX_ITEM_ID_LENGTH),
                        buffer.readVarInt(),
                        buffer.readVarLong()
                )
        );

        @Override
        public Type<SellActionPayload> type() {
            return TYPE;
        }
    }

    /**
     * Wire format for one item bundle in a shop. The other fields describe purchase limits (1.1+): {@code limitMax} /
     * {@code limitLeft} bundles per player in {@code period} (a {@code ShopDefinition.Period} ordinal), {@code stockLeft}
     * shared by everyone, and {@code restockSeconds} until a partly sold stock fills up again. Each is -1 when it does
     * not apply.
     */
    public record ShopEntryPayload(String itemId, int amount, long price, int limitMax, int limitLeft, int period,
                                   int stockLeft, int restockSeconds) {
        /** An entry without limits (and every sell offer). */
        public ShopEntryPayload(String itemId, int amount, long price) {
            this(itemId, amount, price, -1, -1, 0, -1, -1);
        }

        public boolean hasLimit() {
            return limitMax >= 0;
        }

        public boolean hasStock() {
            return stockLeft >= 0;
        }

        /** Bundles that can still be bought (Integer.MAX_VALUE without limits). */
        public int allowed() {
            int allowed = Integer.MAX_VALUE;
            if (hasLimit()) {
                allowed = Math.min(allowed, Math.max(0, limitLeft));
            }
            if (hasStock()) {
                allowed = Math.min(allowed, stockLeft);
            }
            return allowed;
        }

        /** The same entry with new remaining counts (after a purchase). */
        public ShopEntryPayload withRemaining(int newLimitLeft, int newStockLeft, int newRestockSeconds) {
            return new ShopEntryPayload(itemId, amount, price, limitMax, newLimitLeft, period, newStockLeft,
                    newRestockSeconds);
        }
    }

    /**
     * Sent after a purchase of a limited entry (or one refused by its limit): the new remaining counts for entry
     * {@code entryIndex} of the open shop, so the screen updates without being rebuilt.
     */
    public record ShopEntryUpdatePayload(String shopId, int entryIndex, int limitLeft, int stockLeft, int restockSeconds)
            implements CustomPacketPayload {
        public static final Type<ShopEntryUpdatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CobblemonEconomyPlus.MOD_ID, "shop_entry_update"
        ));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopEntryUpdatePayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUtf(payload.shopId, MAX_SHOP_ID_LENGTH);
                    buffer.writeVarInt(payload.entryIndex);
                    buffer.writeVarInt(payload.limitLeft);
                    buffer.writeVarInt(payload.stockLeft);
                    buffer.writeVarInt(payload.restockSeconds);
                },
                buffer -> new ShopEntryUpdatePayload(
                        buffer.readUtf(MAX_SHOP_ID_LENGTH),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt()
                )
        );

        @Override
        public Type<ShopEntryUpdatePayload> type() {
            return TYPE;
        }
    }

    private static void writeOpenShop(RegistryFriendlyByteBuf buffer, OpenShopPayload payload) {
        buffer.writeUtf(payload.shopId, MAX_SHOP_ID_LENGTH);
        buffer.writeUtf(payload.shopName, MAX_NAME_LENGTH);
        buffer.writeUtf(payload.theme, MAX_SHOP_ID_LENGTH);
        buffer.writeUtf(payload.subtitle, MAX_NAME_LENGTH);
        buffer.writeVarLong(payload.balance);
        writeEntries(buffer, payload.entries);
        writeEntries(buffer, payload.sellEntries);
    }

    private static OpenShopPayload readOpenShop(RegistryFriendlyByteBuf buffer) {
        String shopId = buffer.readUtf(MAX_SHOP_ID_LENGTH);
        String shopName = buffer.readUtf(MAX_NAME_LENGTH);
        String theme = buffer.readUtf(MAX_SHOP_ID_LENGTH);
        String subtitle = buffer.readUtf(MAX_NAME_LENGTH);
        long balance = Math.max(0L, buffer.readVarLong());
        List<ShopEntryPayload> entries = readEntries(buffer, MAX_ENTRIES);
        List<ShopEntryPayload> sellEntries = readEntries(buffer, MAX_SELL_ENTRIES);
        return new OpenShopPayload(shopId, shopName, theme, subtitle, balance, entries, sellEntries);
    }

    private static void writeEntries(RegistryFriendlyByteBuf buffer, List<ShopEntryPayload> entries) {
        buffer.writeVarInt(entries.size());
        for (ShopEntryPayload entry : entries) {
            buffer.writeUtf(entry.itemId, MAX_ITEM_ID_LENGTH);
            buffer.writeVarInt(entry.amount);
            buffer.writeVarLong(entry.price);
            buffer.writeVarInt(entry.limitMax);
            buffer.writeVarInt(entry.limitLeft);
            buffer.writeVarInt(entry.period);
            buffer.writeVarInt(entry.stockLeft);
            buffer.writeVarInt(entry.restockSeconds);
        }
    }

    private static List<ShopEntryPayload> readEntries(RegistryFriendlyByteBuf buffer, int limit) {
        int size = buffer.readVarInt();
        if (size < 0 || size > limit) {
            throw new IllegalArgumentException("Invalid shop entry count: " + size);
        }
        List<ShopEntryPayload> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new ShopEntryPayload(
                    buffer.readUtf(MAX_ITEM_ID_LENGTH),
                    buffer.readVarInt(),
                    Math.max(0L, buffer.readVarLong()),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            ));
        }
        return entries;
    }
}
