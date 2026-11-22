package com.diamo.cobblemoneconomyplus.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A reward that is about to be paid, passed to listeners registered with {@link CobblemonEconomyApi#onReward}
 * before any coins move. Listeners can {@link #cancel()} it or change the {@link #setAmount amount}.
 *
 * <p>Typical use: a mod that runs its own battle facility cancels the reward for its battles, so players are not
 * paid twice (once by the facility, once by this mod):</p>
 *
 * <pre>{@code
 * CobblemonEconomyApi.onReward(event -> {
 *     if (event.battleId() != null && MyFacility.isFacilityBattle(event.battleId())) {
 *         event.cancel();
 *     }
 * });
 * }</pre>
 */
public final class RewardEvent {
    private final RewardType type;
    private final ServerPlayer player;
    private final boolean alpha;
    private final int level;
    private final int milestone;
    private final @Nullable UUID battleId;
    private final @Nullable Entity opponent;
    private long amount;
    private boolean cancelled;

    public RewardEvent(RewardType type, ServerPlayer player, long amount, boolean alpha, int level, int milestone,
                       @Nullable UUID battleId, @Nullable Entity opponent) {
        this.type = type;
        this.player = player;
        this.amount = Math.max(0L, amount);
        this.alpha = alpha;
        this.level = level;
        this.milestone = milestone;
        this.battleId = battleId;
        this.opponent = opponent;
    }

    public RewardType type() {
        return type;
    }

    /** The player who receives the reward. */
    public ServerPlayer player() {
        return player;
    }

    /** Coins to pay. */
    public long amount() {
        return amount;
    }

    /** Changes the coins to pay (negative values become 0; 0 pays nothing). */
    public void setAmount(long amount) {
        this.amount = Math.max(0L, amount);
    }

    /** Whether the Pokémon defeated or captured was an alpha. Always false for trainer battles and milestones. */
    public boolean alpha() {
        return alpha;
    }

    /**
     * The level the amount was based on: the wild or captured Pokémon's level, or the highest level in the trainer's
     * team. 0 for milestones.
     */
    public int level() {
        return level;
    }

    /** For {@link RewardType#POKEDEX_MILESTONE}: the number of species caught that was reached. 0 otherwise. */
    public int milestone() {
        return milestone;
    }

    /** The Cobblemon battle's id for battle rewards, otherwise null. */
    public @Nullable UUID battleId() {
        return battleId;
    }

    /** The NPC trainer entity for trainer battle rewards, otherwise null. */
    public @Nullable Entity opponent() {
        return opponent;
    }

    /** Stops the reward: no coins are paid and no message is shown. */
    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
