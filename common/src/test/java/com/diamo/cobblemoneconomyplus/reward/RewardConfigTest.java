package com.diamo.cobblemoneconomyplus.reward;

import com.diamo.cobblemoneconomyplus.config.ConfigMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardConfigTest {
    private static RewardConfig parse(String json, List<ConfigMessage> warnings) {
        return RewardConfig.parse(json, warnings);
    }

    @Test
    void defaultsMatchTheAgreedAmounts() {
        RewardConfig config = RewardConfig.defaults();
        assertTrue(config.enabled());
        assertEquals(0L, config.dailyCap());
        assertEquals(10 + 20, config.wildFormula(false).amount(20));
        assertEquals(100 + 20 * 5, config.wildFormula(true).amount(20));
        assertEquals(100 + 50 * 5, config.trainerBattle().amount(50));
        assertEquals(5 + 20, config.captureFormula(false).amount(20));
        assertEquals(50 + 20 * 3, config.captureFormula(true).amount(20));
        assertTrue(config.pokedexEnabled());
        assertEquals(14, config.milestones().size());
        assertEquals(500L, config.milestones().get(10));
        assertEquals(50_000L, config.milestones().get(1000));
    }

    @Test
    void missingFieldsUseTheDefaults() {
        List<ConfigMessage> warnings = new ArrayList<>();
        RewardConfig config = parse("{ \"daily_cap\": 3000, \"capture\": { \"base\": 7 } }", warnings);
        assertTrue(warnings.isEmpty(), warnings::toString);
        assertEquals(3000L, config.dailyCap());
        assertEquals(7 + 10, config.captureFormula(false).amount(10));
        assertEquals(10 + 10, config.wildFormula(false).amount(10));
        assertEquals(14, config.milestones().size());
    }

    @Test
    void eachRewardCanBeSwitchedOff() {
        RewardConfig config = parse("""
                { "wild_battle": { "enabled": false }, "trainer_battle": { "enabled": false },
                  "pokedex_milestones": { "enabled": false } }
                """, new ArrayList<>());
        assertEquals(0L, config.wildFormula(false).amount(50));
        assertEquals(0L, config.trainerBattle().amount(50));
        assertFalse(config.pokedexEnabled());
        assertTrue(config.wildFormula(true).amount(50) > 0, "the alpha reward keeps its own switch");
    }

    @Test
    void aDisabledAlphaRewardFallsBackToTheNormalOne() {
        RewardConfig config = parse("{ \"capture_alpha\": { \"enabled\": false } }", new ArrayList<>());
        assertEquals(config.captureFormula(false).amount(30), config.captureFormula(true).amount(30));
    }

    @Test
    void badValuesAreReplacedWithAWarning() {
        List<ConfigMessage> warnings = new ArrayList<>();
        RewardConfig config = parse("""
                { "enabled": "yes", "daily_cap": -5,
                  "wild_battle": { "base": 1.5, "per_level": "x" },
                  "pokedex_milestones": { "milestones": { "ten": 100, "20": -1, "30": 300 } } }
                """, warnings);
        assertTrue(config.enabled());
        assertEquals(0L, config.dailyCap());
        assertEquals(10 + 4, config.wildFormula(false).amount(4));
        assertEquals(Map.of(30, 300L), config.milestones());
        assertEquals(6, warnings.size(), warnings::toString);
    }

    @Test
    void brokenJsonIsAnErrorAndNotApplied() {
        List<ConfigMessage> errors = new ArrayList<>();
        assertNull(RewardManager.read("{ \"daily_cap\": 10 \"enabled\": true }", errors, new ArrayList<>()));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).text().startsWith("rewards.json: invalid JSON"), errors.get(0).text());

        errors.clear();
        assertNull(RewardManager.read("[]", errors, new ArrayList<>()));
        assertEquals(1, errors.size());

        assertNotNull(RewardManager.read(RewardConfig.DEFAULT_JSON, errors = new ArrayList<>(), new ArrayList<>()));
        assertTrue(errors.isEmpty());
    }

    @Test
    void levelsNeverMakeANegativeAmount() {
        RewardConfig.Formula formula = new RewardConfig.Formula(true, 10, 2);
        assertEquals(10L, formula.amount(-3));
        assertEquals(0L, new RewardConfig.Formula(false, 10, 2).amount(50));
    }

    @Test
    void dailyCapLimitsWhatIsLeftForToday() {
        assertEquals(100L, RewardRules.capped(100, 0, 999_999));
        assertEquals(100L, RewardRules.capped(100, 1000, 0));
        assertEquals(40L, RewardRules.capped(100, 1000, 960));
        assertEquals(0L, RewardRules.capped(100, 1000, 1000));
        assertEquals(0L, RewardRules.capped(100, 1000, 5000));
        assertEquals(0L, RewardRules.capped(0, 1000, 0));
    }

    @Test
    void milestonesAreDueOnceAndAllAtOnceWhenSkipped() {
        NavigableMap<Integer, Long> milestones = new TreeMap<>(Map.of(10, 500L, 25, 1000L, 50, 2500L));
        assertEquals(List.of(), RewardRules.dueMilestones(milestones, Set.of(), 9));
        assertEquals(List.of(10), RewardRules.dueMilestones(milestones, Set.of(), 10));
        assertEquals(List.of(10, 25), RewardRules.dueMilestones(milestones, Set.of(), 49));
        assertEquals(List.of(25, 50), RewardRules.dueMilestones(milestones, Set.of(10), 300));
        assertEquals(List.of(), RewardRules.dueMilestones(milestones, Set.of(10, 25, 50), 300));
    }

    @Test
    void loginBonusDaysAreReadWithTheirItems() {
        RewardConfig.DailyLogin defaults = RewardConfig.defaults().dailyLogin();
        assertEquals(28, defaults.days().size(), "four weeks");
        assertEquals(500L, defaults.days().get(6).coins());
        assertEquals(2, defaults.days().get(6).items().size());
        assertEquals(1000L, defaults.days().get(27).coins());

        List<ConfigMessage> warnings = new ArrayList<>();
        RewardConfig.DailyLogin login = parse("""
                { "daily_login": { "repeat": false, "days": [
                    { "coins": 10 },
                    { "items": [ { "item": "minecraft:apple", "count": 3 }, { "item": "minecraft:stone", "count": 0 } ] },
                    "oops"
                ] } }
                """, warnings).dailyLogin();
        assertEquals(2, warnings.size(), warnings::toString);
        assertEquals(3, login.days().size());
        assertEquals(List.of(new RewardConfig.LoginItem(
                net.minecraft.resources.ResourceLocation.parse("minecraft:apple"), 3)), login.days().get(1).items());
        assertEquals(0L, login.days().get(2).coins(), "an unreadable day pays nothing but keeps its place");
        assertTrue(login.enabled() && login.showScreen() && login.resetOnMiss());

        warnings.clear();
        assertEquals(28, parse("{ \"daily_login\": { \"days\": [] } }", warnings).dailyLogin().days().size(),
                "an empty list falls back to the default days");
        assertEquals(1, warnings.size());
    }

    @Test
    void loginDaysRepeatOrStayOnTheLastDay() {
        RewardConfig.DailyLogin repeating = RewardConfig.defaults().dailyLogin();
        assertEquals(0, repeating.dayIndex(1));
        assertEquals(27, repeating.dayIndex(28));
        assertEquals(0, repeating.dayIndex(29));
        RewardConfig.DailyLogin staying = new RewardConfig.DailyLogin(true, true, true, false, repeating.days());
        assertEquals(27, staying.dayIndex(29));
        assertEquals(27, staying.dayIndex(100));
    }

    @Test
    void loginCountFollowsTheMissRule() {
        assertEquals(0, RewardRules.loginCount(100, 3, 100, true), "already paid today");
        assertEquals(4, RewardRules.loginCount(99, 3, 100, true));
        assertEquals(1, RewardRules.loginCount(97, 3, 100, true), "a missed day starts over");
        assertEquals(4, RewardRules.loginCount(97, 3, 100, false), "or every login day counts");
    }

    @Test
    void savedDataKeepsClaimedMilestonesAndTodaysTotal() {
        UUID loginPlayer = UUID.randomUUID();
        RewardSavedData withLogin = new RewardSavedData();
        withLogin.recordLogin(loginPlayer, 20_000L, 5);
        RewardSavedData.LoginStreak streak = RewardSavedData.load(
                withLogin.save(new net.minecraft.nbt.CompoundTag(), null), null).lastLogin(loginPlayer);
        assertEquals(new RewardSavedData.LoginStreak(20_000L, 5), streak);

        UUID player = UUID.randomUUID();
        RewardSavedData data = new RewardSavedData();
        data.claimMilestone(player, 10);
        data.claimMilestone(player, 25);
        data.addRewarded(player, 20_000L, 300L);
        data.addRewarded(player, 20_000L, 200L);

        RewardSavedData loaded = RewardSavedData.load(data.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(Set.of(10, 25), loaded.claimedMilestones(player));
        assertEquals(500L, loaded.rewardedOn(player, 20_000L));
        assertEquals(0L, loaded.rewardedOn(player, 20_001L), "a new day starts from zero");

        loaded.addRewarded(player, 20_001L, 50L);
        assertEquals(50L, loaded.rewardedOn(player, 20_001L));
        assertEquals(Set.of(), loaded.claimedMilestones(UUID.randomUUID()));
    }
}
