package syxianbanking.banking;

import game.boosting.BOOSTABLE_O;
import game.boosting.BSourceInfo;
import game.boosting.Boostable;
import game.boosting.BoostableCat;
import game.boosting.Booster;
import game.faction.FACTIONS;
import init.sprite.UI.UI;
import init.tech.TECH;
import init.tech.TECHS;
import snake2d.util.misc.CLAMP;
import syxianbanking.TR;

/**
 * Reads the optional Syxian Banking technologies from the vanilla tech system.
 *
 * The techs are declared in assets/init/tech/ADMIN.txt. Game builds have used
 * both raw tech keys (BANK1) and tree-prefixed keys (ADMIN_BANK1), so lookup is
 * intentionally tolerant.
 */
public final class BankTech {

    private static final String BANK_UNLOCK_TECH_KEY = "BANK0";
    private static final String BANK_RATES_TECH_KEY  = "BANK1";
    private static final String BANK_UNLOCK_TREE_KEY = "ADMIN_BANK0";
    private static final String BANK_RATES_TREE_KEY  = "ADMIN_BANK1";

    private static TECH bankUnlockTech;
    private static TECH bankRatesTech;
    private static boolean unlockResolved;
    private static boolean ratesResolved;
    private static boolean visualBoostsInstalled;

    private BankTech() {}

    public static boolean unlocked() {
        return level(bankUnlockTech()) > 0;
    }

    static int level() {
        return level(bankRatesTech());
    }

    static int levelForRates() {
        return level();
    }

    public static void installVisualEffects() {
        if (visualBoostsInstalled) return;
        try {
            TECH tech = bankRatesTech();
            if (tech == null) return;

            BoostableCat cat = new BoostableCat(
                    "SYXIAN_BANKING",
                    TR.s("tech.effect.category"),
                    TR.s("tech.effect.category.desc"),
                    BoostableCat.TYPE_SETT,
                    UI.icons().s.money);

            pushVisualEffect(tech, cat, "SYXIAN_BANKING_SAVINGS",
                    TR.s("tech.effect.savings"),
                    BankConstants.BANKING_TECH_SAVINGS_RATE_BONUS_PER_LEVEL * 100.0);
            pushVisualEffect(tech, cat, "SYXIAN_BANKING_LOANS",
                    TR.s("tech.effect.loans"),
                    BankConstants.BANKING_TECH_LOAN_RATE_DISCOUNT_PER_LEVEL * 100.0);
            pushVisualEffect(tech, cat, "SYXIAN_BANKING_PENALTIES",
                    TR.s("tech.effect.penalties"),
                    BankConstants.BANKING_TECH_PENALTY_RATE_DISCOUNT_PER_LEVEL * 100.0);

            visualBoostsInstalled = true;
        } catch (Throwable ignored) {
            visualBoostsInstalled = false;
        }
    }

    static double applySavingsRate(double rate) {
        return applySavingsRate(rate, level());
    }

    static double applyLoanRate(double rate) {
        return applyLoanRate(rate, level());
    }

    static double applyPenaltyRate(double rate) {
        return applyPenaltyRate(rate, level());
    }

    static double applySavingsRate(double rate, int techLevel) {
        return Sanitize.rate(rate * savingsRateFactor(techLevel), BankConstants.MAX_SAVINGS_RATE);
    }

    static double applyLoanRate(double rate, int techLevel) {
        return Sanitize.rate(rate * loanRateFactor(techLevel), BankConstants.MAX_BASE_LOAN_RATE);
    }

    static double applyPenaltyRate(double rate, int techLevel) {
        return Sanitize.rate(rate * penaltyRateFactor(techLevel), BankConstants.MAX_DAILY_PENALTY_RATE);
    }

    static double savingsRateFactorForLevel(int techLevel) {
        return savingsRateFactor(techLevel);
    }

    static double loanRateFactorForLevel(int techLevel) {
        return loanRateFactor(techLevel);
    }

    private static void pushVisualEffect(TECH tech, BoostableCat cat, String key,
            CharSequence name, double perLevelValue) {
        Boostable boostable = new Boostable(
                tech.boosters.all().size(),
                key,
                0.0,
                name,
                TR.s("tech.effect.category.desc"),
                UI.icons().s.money,
                cat,
                0.0);

        tech.boosters.push(
                new VisualBooster(TR.s("tech.banking.rates.name"), perLevelValue),
                boostable,
                name);
    }

    private static int level(TECH tech) {
        try {
            if (tech == null || FACTIONS.player() == null) return 0;
            return CLAMP.i(FACTIONS.player().tech.level(tech), 0, BankConstants.BANKING_TECH_MAX_LEVEL);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static double savingsRateFactor(int techLevel) {
        return 1.0 + clampedLevel(techLevel) * BankConstants.BANKING_TECH_SAVINGS_RATE_BONUS_PER_LEVEL;
    }

    private static double loanRateFactor(int techLevel) {
        return Math.max(BankConstants.BANKING_TECH_MIN_LOAN_RATE_FACTOR,
                1.0 - clampedLevel(techLevel) * BankConstants.BANKING_TECH_LOAN_RATE_DISCOUNT_PER_LEVEL);
    }

    private static double penaltyRateFactor(int techLevel) {
        return Math.max(BankConstants.BANKING_TECH_MIN_PENALTY_RATE_FACTOR,
                1.0 - clampedLevel(techLevel) * BankConstants.BANKING_TECH_PENALTY_RATE_DISCOUNT_PER_LEVEL);
    }

    private static int clampedLevel(int techLevel) {
        return CLAMP.i(techLevel, 0, BankConstants.BANKING_TECH_MAX_LEVEL);
    }

    private static TECH bankUnlockTech() {
        if (unlockResolved) return bankUnlockTech;
        bankUnlockTech = resolve(BANK_UNLOCK_TECH_KEY, BANK_UNLOCK_TREE_KEY);
        unlockResolved = bankUnlockTech != null;
        return bankUnlockTech;
    }

    private static TECH bankRatesTech() {
        if (ratesResolved) return bankRatesTech;
        bankRatesTech = resolve(BANK_RATES_TECH_KEY, BANK_RATES_TREE_KEY);
        ratesResolved = bankRatesTech != null;
        return bankRatesTech;
    }

    private static TECH resolve(String key, String treeKey) {
        try {
            for (int i = 0; i < TECHS.ALL().size(); i++) {
                TECH tech = TECHS.ALL().get(i);
                if (matches(tech, key, treeKey)) return tech;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean matches(TECH tech, String key, String treeKey) {
        if (tech == null || tech.key == null) return false;
        return key.equals(tech.key) || treeKey.equals(tech.key) || tech.key.endsWith("_" + key);
    }

    private static final class VisualBooster extends Booster {
        private final double perLevelValue;

        VisualBooster(CharSequence sourceName, double perLevelValue) {
            super(new BSourceInfo(sourceName, UI.icons().s.money), false);
            this.perLevelValue = perLevelValue;
        }

        @Override public double from() { return 0.0; }
        @Override public double to() { return perLevelValue; }
        @Override public double getValue(double input) { return perLevelValue * CLAMP.d(input, 0.0, 1.0); }
        @Override protected double pget(BOOSTABLE_O o) { return 1.0; }
    }
}
