package com.diamo.cobblemoneconomyplus.api;

import java.util.UUID;

/**
 * A change to one player's balance, passed to listeners registered with
 * {@link CobblemonEconomyApi#onBalanceChanged}. A transfer between two players produces two changes.
 *
 * @param playerId        whose balance changed (the player may be offline)
 * @param previousBalance balance before the change
 * @param newBalance      balance after the change
 * @param reason          what caused it, as written to the transaction log (for example {@code "shop:general"},
 *                        {@code "reward:wild_battle"}, {@code "command"}, or the reason another mod passed to the API)
 */
public record BalanceChange(UUID playerId, long previousBalance, long newBalance, String reason) {
    /** {@code newBalance - previousBalance}: positive when coins were added. */
    public long delta() {
        return newBalance - previousBalance;
    }
}
