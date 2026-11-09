package com.diamo.cobblemoneconomyplus.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Wire-format round trips. If one of these fails after a change, bump EconomyPayloads.PROTOCOL_VERSION. */
class EconomyPayloadsTest {
    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buffer, value);
        T decoded = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "decoder must consume the whole packet");
        return decoded;
    }

    @Test
    void protocolVersionIsSet() {
        assertFalse(EconomyPayloads.PROTOCOL_VERSION.isBlank());
    }

    @Test
    void balanceRoundTrips() {
        assertEquals(new EconomyPayloads.BalancePayload(123_456_789L),
                roundTrip(EconomyPayloads.BalancePayload.STREAM_CODEC, new EconomyPayloads.BalancePayload(123_456_789L)));
    }

    @Test
    void negativeBalancesAreClampedWhenDecoded() {
        assertEquals(0L,
                roundTrip(EconomyPayloads.BalancePayload.STREAM_CODEC, new EconomyPayloads.BalancePayload(-1L)).balance());
    }

    @Test
    void openShopRoundTripsWithBothLists() {
        EconomyPayloads.OpenShopPayload payload = new EconomyPayloads.OpenShopPayload(
                "boutique", "{\"en_us\":\"Boutique\",\"ja_jp\":\"ブティック\"}", "iron", "Sturdy goods", 5_000L,
                List.of(new EconomyPayloads.ShopEntryPayload("minecraft:apple", 4, 12L),
                        new EconomyPayloads.ShopEntryPayload("cobblemon:poke_ball", 1, 200L)),
                List.of(new EconomyPayloads.ShopEntryPayload("minecraft:diamond", 1, 250L)));

        assertEquals(payload, roundTrip(EconomyPayloads.OpenShopPayload.STREAM_CODEC, payload));
    }

    @Test
    void openShopWithNoEntriesRoundTrips() {
        EconomyPayloads.OpenShopPayload payload = new EconomyPayloads.OpenShopPayload(
                "empty", "Empty", "pokeball", "", 0L, List.of(), List.of());
        assertEquals(payload, roundTrip(EconomyPayloads.OpenShopPayload.STREAM_CODEC, payload));
    }

    @Test
    void shopActionCarriesWhatTheClientDisplayed() {
        EconomyPayloads.ShopActionPayload payload =
                new EconomyPayloads.ShopActionPayload("boutique", 7, 3, "cobblemon:great_ball", 1, 600L);
        assertEquals(payload, roundTrip(EconomyPayloads.ShopActionPayload.STREAM_CODEC, payload));
    }

    @Test
    void sellActionRoundTrips() {
        EconomyPayloads.SellActionPayload payload =
                new EconomyPayloads.SellActionPayload("boutique", "minecraft:diamond", 2304, 300L);
        assertEquals(payload, roundTrip(EconomyPayloads.SellActionPayload.STREAM_CODEC, payload));
    }

    @Test
    void anAbsurdEntryCountIsRejectedWhenDecoding() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeUtf("shop");
        buffer.writeUtf("Name");
        buffer.writeUtf("pokeball");
        buffer.writeUtf("");
        buffer.writeVarLong(0L);
        buffer.writeVarInt(1_000_000); // claims a million entries
        assertThrows(IllegalArgumentException.class, () -> EconomyPayloads.OpenShopPayload.STREAM_CODEC.decode(buffer));
    }
}
