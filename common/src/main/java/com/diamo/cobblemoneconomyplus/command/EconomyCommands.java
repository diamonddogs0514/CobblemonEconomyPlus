package com.diamo.cobblemoneconomyplus.command;

import com.diamo.cobblemoneconomyplus.api.CobblemonEconomyApi;
import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import com.diamo.cobblemoneconomyplus.economy.EconomyService;
import com.diamo.cobblemoneconomyplus.economy.TransactionLog;
import com.diamo.cobblemoneconomyplus.permission.EconomyPermission;
import com.diamo.cobblemoneconomyplus.permission.EconomyPermissions;
import com.diamo.cobblemoneconomyplus.shop.ShopDefinition;
import com.diamo.cobblemoneconomyplus.shop.ShopManager;
import com.diamo.cobblemoneconomyplus.shop.Shopkeeper;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Server command registration shared by both loaders.
 *
 * <p>Player arguments are game profiles, so names of offline players (known to the server) work too and
 * selectors such as {@code @a} still match the online players. Each command group is guarded by an
 * {@link EconomyPermission} node, which permission managers such as LuckPerms can grant or deny.</p>
 */
public final class EconomyCommands {
    private EconomyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cep")
                .then(Commands.literal("balance")
                        .requires(source -> EconomyPermissions.has(source, EconomyPermission.BALANCE))
                        .executes(context -> showOwnBalance(context.getSource()))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(source -> EconomyPermissions.has(source, EconomyPermission.BALANCE_OTHERS))
                                .executes(context -> showBalances(
                                        context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "player")
                                ))
                        )
                )
                .then(payCommand("pay"))
                .then(Commands.literal("money")
                        .requires(source -> EconomyPermissions.has(source, EconomyPermission.MONEY))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> showBalances(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "player")
                                        ))
                                )
                        )
                        .then(Commands.literal("set")
                                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(context -> setMoney(
                                                        context.getSource(),
                                                        GameProfileArgument.getGameProfiles(context, "players"),
                                                        LongArgumentType.getLong(context, "amount")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("add")
                                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(context -> addMoney(
                                                        context.getSource(),
                                                        GameProfileArgument.getGameProfiles(context, "players"),
                                                        LongArgumentType.getLong(context, "amount")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("take")
                                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(context -> takeMoney(
                                                        context.getSource(),
                                                        GameProfileArgument.getGameProfiles(context, "players"),
                                                        LongArgumentType.getLong(context, "amount")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("history")
                        .requires(source -> EconomyPermissions.has(source, EconomyPermission.HISTORY))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> showHistory(
                                        context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "player"),
                                        DEFAULT_HISTORY_COUNT
                                ))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_HISTORY_COUNT))
                                        .executes(context -> showHistory(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "player"),
                                                IntegerArgumentType.getInteger(context, "count")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("shop")
                        .requires(source -> EconomyPermissions.has(source, EconomyPermission.SHOP))
                        .then(Commands.literal("reload")
                                .executes(context -> reloadShops(context.getSource()))
                        )
                        .then(Commands.literal("list")
                                .executes(context -> listShops(context.getSource()))
                        )
                )
                .then(Commands.literal("shopkeeper")
                        .requires(source -> EconomyPermissions.has(source, EconomyPermission.SHOPKEEPER))
                        .then(Commands.literal("set")
                                .then(Commands.argument("npc", EntityArgument.entity())
                                        .then(Commands.argument("shop", StringArgumentType.word())
                                                .executes(context -> setShopkeeper(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "npc"),
                                                        StringArgumentType.getString(context, "shop")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("clear")
                                .then(Commands.argument("npc", EntityArgument.entity())
                                        .executes(context -> clearShopkeeper(
                                                context.getSource(),
                                                EntityArgument.getEntity(context, "npc")
                                        ))
                                )
                        )
                )
        );

        // Convenience alias: /pay <player> <amount>
        dispatcher.register(payCommand("pay"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> payCommand(String name) {
        return Commands.literal(name)
                .requires(source -> EconomyPermissions.has(source, EconomyPermission.PAY))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> pay(
                                        context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "player"),
                                        LongArgumentType.getLong(context, "amount")
                                ))
                        )
                );
    }

    // ------------------------------------------------------------------ balance & money

    /** Transaction-log label for a command: who ran it (a player name, "Server", a command block, ...). */
    private static String commandReason(CommandSourceStack source) {
        return "command:" + source.getTextName();
    }

    private static int showOwnBalance(CommandSourceStack source) throws CommandSyntaxException {
        GameProfile profile = source.getPlayerOrException().getGameProfile();
        return showBalances(source, java.util.List.of(profile));
    }

    private static int showBalances(CommandSourceStack source, Collection<GameProfile> profiles) {
        for (GameProfile profile : profiles) {
            long balance = EconomyService.getBalance(source.getServer(), profile.getId());
            source.sendSuccess(() -> Component.translatable(
                    "command.cobblemon_economy_plus.balance",
                    profile.getName(),
                    EconomyFormat.money(balance)
            ), false);
        }
        return profiles.size();
    }

    private static int setMoney(CommandSourceStack source, Collection<GameProfile> profiles, long amount) {
        for (GameProfile profile : profiles) {
            CobblemonEconomyApi.setBalance(source.getServer(), profile.getId(), amount, commandReason(source));
        }
        source.sendSuccess(() -> Component.translatable(
                "command.cobblemon_economy_plus.money_set",
                profiles.size(),
                EconomyFormat.money(amount)
        ), true);
        return profiles.size();
    }

    private static int addMoney(CommandSourceStack source, Collection<GameProfile> profiles, long amount) {
        for (GameProfile profile : profiles) {
            CobblemonEconomyApi.deposit(source.getServer(), profile.getId(), amount, commandReason(source));
        }
        source.sendSuccess(() -> Component.translatable(
                "command.cobblemon_economy_plus.money_add",
                profiles.size(),
                EconomyFormat.money(amount)
        ), true);
        return profiles.size();
    }

    private static int takeMoney(CommandSourceStack source, Collection<GameProfile> profiles, long amount) {
        int changed = 0;
        for (GameProfile profile : profiles) {
            if (CobblemonEconomyApi.withdraw(source.getServer(), profile.getId(), amount, commandReason(source))) {
                changed++;
            }
        }

        if (changed == 0) {
            source.sendFailure(Component.translatable("command.cobblemon_economy_plus.money_take_failed"));
            return 0;
        }

        int affected = changed;
        source.sendSuccess(() -> Component.translatable(
                "command.cobblemon_economy_plus.money_take",
                affected,
                EconomyFormat.money(amount)
        ), true);
        return changed;
    }

    // ------------------------------------------------------------------ history

    private static final int DEFAULT_HISTORY_COUNT = 10;
    private static final int MAX_HISTORY_COUNT = 50;
    private static final DateTimeFormatter HISTORY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    /** {@code /cep history <player> [count]}: the player's latest transactions from the transaction log. */
    private static int showHistory(CommandSourceStack source, Collection<GameProfile> profiles, int count) {
        int shown = 0;
        for (GameProfile profile : profiles) {
            List<TransactionLog.Entry> entries = TransactionLog.recent(source.getServer(), profile.getId(), count);
            if (entries.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(
                        "command.cobblemon_economy_plus.history_empty", profile.getName()), false);
                continue;
            }

            source.sendSuccess(() -> Component.translatable(
                    "command.cobblemon_economy_plus.history_header", profile.getName(), entries.size()), false);
            for (TransactionLog.Entry entry : entries) {
                boolean gain = entry.delta() >= 0;
                String amount = (gain ? "+" : "-") + EconomyFormat.money(Math.abs(entry.delta()));
                String tail = entry.detail().isEmpty() ? entry.source() : entry.source() + " " + entry.detail();
                source.sendSuccess(() -> Component.literal(HISTORY_TIME.format(entry.time()) + "  ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(amount).withStyle(gain ? ChatFormatting.GREEN : ChatFormatting.RED))
                        .append(Component.literal("  → " + EconomyFormat.money(entry.balanceAfter()) + "  ")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(entry.type().name() + "  " + tail).withStyle(ChatFormatting.DARK_GRAY)),
                        false);
            }
            shown += entries.size();
        }
        return shown;
    }

    // ------------------------------------------------------------------ pay

    private static int pay(CommandSourceStack source, Collection<GameProfile> targets, long amount)
            throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();
        if (targets.size() != 1) {
            source.sendFailure(Component.translatable("command.cobblemon_economy_plus.pay_one_player"));
            return 0;
        }

        GameProfile receiver = targets.iterator().next();
        if (receiver.getId().equals(sender.getUUID())) {
            source.sendFailure(Component.translatable("command.cobblemon_economy_plus.pay_self"));
            return 0;
        }
        if (!CobblemonEconomyApi.transfer(source.getServer(), sender.getUUID(), receiver.getId(), amount, "pay:" + sender.getGameProfile().getName())) {
            source.sendFailure(Component.translatable("command.cobblemon_economy_plus.pay_failed"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "command.cobblemon_economy_plus.pay_sent",
                receiver.getName(),
                EconomyFormat.money(amount)
        ), false);
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(receiver.getId());
        if (online != null) {
            online.sendSystemMessage(Component.translatable(
                    "command.cobblemon_economy_plus.pay_received",
                    sender.getGameProfile().getName(),
                    EconomyFormat.money(amount)
            ));
        }
        return 1;
    }

    // ------------------------------------------------------------------ shops

    private static int reloadShops(CommandSourceStack source) {
        int count = ShopManager.reload(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.cobblemon_economy_plus.shops_reloaded", count), true);
        return count;
    }

    private static int listShops(CommandSourceStack source) {
        String shopIds = ShopManager.all(source.getServer()).stream()
                .map(ShopDefinition::id)
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("command.cobblemon_economy_plus.shop_list", shopIds), false);
        return 1;
    }

    private static int setShopkeeper(CommandSourceStack source, Entity target, String requestedShopId) {
        String shopId = requestedShopId.toLowerCase(java.util.Locale.ROOT);
        if (ShopManager.get(source.getServer(), shopId).isEmpty()) {
            source.sendFailure(Component.translatable("command.cobblemon_economy_plus.shop_unknown", requestedShopId));
            return 0;
        }
        if (!Shopkeeper.setShop(target, shopId)) {
            source.sendFailure(Component.translatable("command.cobblemon_economy_plus.invalid_shopkeeper"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "command.cobblemon_economy_plus.shopkeeper_set",
                target.getDisplayName(),
                shopId
        ), true);
        return 1;
    }

    private static int clearShopkeeper(CommandSourceStack source, Entity target) {
        if (!Shopkeeper.clearShop(target)) {
            source.sendFailure(Component.translatable("command.cobblemon_economy_plus.no_shopkeeper"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.cobblemon_economy_plus.shopkeeper_cleared",
                target.getDisplayName()
        ), true);
        return 1;
    }
}
