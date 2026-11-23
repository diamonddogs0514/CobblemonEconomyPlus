package com.diamo.cobblemoneconomyplus;

import com.diamo.cobblemoneconomyplus.command.EconomyCommands;
import com.diamo.cobblemoneconomyplus.economy.BalanceRanking;
import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import com.diamo.cobblemoneconomyplus.economy.EconomyService;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderHandler;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Text Placeholder API support (Fabric only; registered when that mod is installed):
 * <ul>
 *     <li>{@code %cobblemon_economy_plus:balance%} - the player's balance, formatted (1,234)</li>
 *     <li>{@code %cobblemon_economy_plus:balance_raw%} - the same as a plain number (1234)</li>
 *     <li>{@code %cobblemon_economy_plus:rank%} - the player's place in {@code /cep top}, or "-" without coins</li>
 *     <li>{@code %cobblemon_economy_plus:top_name N%} / {@code top_balance N%} - who is N-th in {@code /cep top}
 *     and their balance (N = 1 when left out), for leaderboards; empty when there is no N-th player</li>
 * </ul>
 */
final class FabricPlaceholders {
    private FabricPlaceholders() {
    }

    static void register() {
        register("balance", playerPlaceholder(context ->
                EconomyFormat.money(EconomyService.getBalance(context.player()))));
        register("balance_raw", playerPlaceholder(context ->
                Long.toString(EconomyService.getBalance(context.player()))));
        register("rank", playerPlaceholder(context -> BalanceRanking.cached(context.server())
                .find(context.player().getUUID())
                .map(entry -> Integer.toString(entry.rank()))
                .orElse("-")));
        register("top_name", (context, argument) -> top(context, argument)
                .map(entry -> PlaceholderResult.value(EconomyCommands.playerName(context.server(), entry.playerId())))
                .orElse(PlaceholderResult.value("")));
        register("top_balance", (context, argument) -> top(context, argument)
                .map(entry -> PlaceholderResult.value(EconomyFormat.money(entry.balance())))
                .orElse(PlaceholderResult.value("")));
        CobblemonEconomyPlus.LOGGER.info("Text Placeholder API found: %cobblemon_economy_plus:...% placeholders registered");
    }

    private static void register(String name, PlaceholderHandler handler) {
        Placeholders.register(ResourceLocation.fromNamespaceAndPath(CobblemonEconomyPlus.MOD_ID, name), handler);
    }

    private interface PlayerValue {
        String get(PlaceholderContext context);
    }

    private static PlaceholderHandler playerPlaceholder(PlayerValue value) {
        return (context, argument) -> context.hasPlayer()
                ? PlaceholderResult.value(value.get(context))
                : PlaceholderResult.invalid("No player!");
    }

    private static Optional<BalanceRanking.Entry> top(PlaceholderContext context, String argument) {
        int position = 1;
        if (argument != null && !argument.isBlank()) {
            try {
                position = Integer.parseInt(argument.trim());
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
        return BalanceRanking.cached(context.server()).at(position);
    }
}
