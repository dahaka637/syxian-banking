package syxianbanking.banking;

import game.faction.FACTIONS;
import game.faction.npc.FactionNPC;
import snake2d.util.misc.CLAMP;
import syxianbanking.TR;

/**
 * Calculates market interest rates from the economic state of NPC kingdoms.
 *
 * The calculation runs once per game day inside BankState.updateIfNeeded()
 * using three passes over the list of active kingdoms:
 *
 *   Pass 1 — Wealth baseline: sums NPC wealth to compute the average,
 *             used as the reference for wealth-weighted signals in the next passes.
 *
 *   Pass 2 — Weighted credit signal: converts each NPC creditScore into a
 *             zero-centred signal and weights it by relative wealth.
 *             Wealthier kingdoms carry more influence over the final rate.
 *
 *   Pass 3 — Signal decomposition: splits the weighted average into
 *             negativeStress, positiveStrength and economicDispersion,
 *             which feed separate rate and credit-capacity formulas.
 *
 * Resulting rates (currentSavingsRate, currentLoanRate) are smoothed by
 * an exponential moving average to prevent abrupt day-to-day jumps.
 * The economic snapshot fields are public so LoanManager can read them
 * for credit capacity and term calculations.
 */
public final class RateCalculator {

    // ---- Market rates (daily output) ----
    public double currentSavingsRate; // current annual savings rate (%)
    public double currentLoanRate;    // current annual loan rate (%)
    public double targetSavingsRate;  // target computed today; current converges towards it
    public double targetLoanRate;
    public boolean initialized;       // false only before the first calculated day
    private int appliedBankTechLevel = -1; // last tech level reflected in the current displayed rates

    // ---- Economic snapshot (rebuilt every game day) ----
    public int    kingdoms;
    public double averageWealth;
    public double totalEconomicWeight;
    public double averageCreditSignal;
    public double economicDispersion;  // weighted average deviation from mean signal
    public double negativeStress;      // negative component of the signal (raises rates)
    public double positiveStrength;    // positive component of the signal (lowers rates)

    // Error message from the last calculation attempt; null means success.
    String error;

    /** Resets all rates and the economic snapshot; called when starting a new campaign. */
    public void reset() {
        currentSavingsRate = 0;
        currentLoanRate    = 0;
        targetSavingsRate  = 0;
        targetLoanRate     = 0;
        initialized        = false;
        appliedBankTechLevel = -1;
        error              = null;
        resetSnapshot();
    }

    /**
     * Runs the three NPC passes and updates current market rates.
     * Returns a localized error message, or null on success.
     */
    public String calculate() {
        resetSnapshot();

        // Pass 1: sum all active kingdom wealth to establish the average.
        for (FactionNPC faction : FACTIONS.NPCs()) {
            if (!faction.isActive()) continue;
            averageWealth += Math.max(0.0, Sanitize.money(FACTIONS.WORTH().faction(faction)));
            kingdoms++;
        }
        if (kingdoms == 0) {
            error = TR.s("error.noKingdoms").toString();
            return error;
        }
        averageWealth /= kingdoms;

        // Pass 2: compute wealth-weighted average credit signal.
        // Wealthier kingdoms receive a higher logarithmic weight, giving them more market influence.
        for (FactionNPC faction : FACTIONS.NPCs()) {
            if (!faction.isActive()) continue;
            double signal = creditSignal(faction);
            double wealth = Math.max(0.0, Sanitize.money(FACTIONS.WORTH().faction(faction)));
            double weight = economicWeight(wealth / Math.max(averageWealth, 1.0));
            averageCreditSignal += signal * weight;
            totalEconomicWeight += weight;
        }
        if (totalEconomicWeight <= 0) {
            error = TR.s("error.invalidEconomicWeight").toString();
            return error;
        }
        averageCreditSignal /= totalEconomicWeight;

        // Pass 3: decompose the signal into stress, strength and dispersion.
        // Dispersion measures inequality between kingdoms — higher inequality = less stable market.
        for (FactionNPC faction : FACTIONS.NPCs()) {
            if (!faction.isActive()) continue;
            double signal = creditSignal(faction);
            double wealth = Math.max(0.0, Sanitize.money(FACTIONS.WORTH().faction(faction)));
            double weight = economicWeight(wealth / Math.max(averageWealth, 1.0));
            economicDispersion += Math.abs(signal - averageCreditSignal) * weight;
            negativeStress     += Math.max(0.0, -signal) * weight;
            positiveStrength   += Math.max(0.0,  signal) * weight;
        }
        economicDispersion /= totalEconomicWeight;
        negativeStress     /= totalEconomicWeight;
        positiveStrength   /= totalEconomicWeight;

        // Compute market target rates from the economic snapshot, then apply banking tech.
        double marketSavingsTarget = Sanitize.rate(
                BankConstants.SAVINGS_BASE_RATE
                + negativeStress    * BankConstants.SAVINGS_STRESS_WEIGHT
                + economicDispersion * BankConstants.SAVINGS_DISPERSION_WEIGHT
                - positiveStrength  * BankConstants.SAVINGS_STRENGTH_DISCOUNT,
                BankConstants.MAX_SAVINGS_RATE);

        double marketLoanTarget = Sanitize.rate(
                marketSavingsTarget + BankConstants.LOAN_BASE_SPREAD
                + negativeStress    * BankConstants.LOAN_STRESS_WEIGHT
                + economicDispersion * BankConstants.LOAN_DISPERSION_WEIGHT,
                BankConstants.MAX_BASE_LOAN_RATE);

        int bankTechLevel = BankTech.levelForRates();
        targetSavingsRate = BankTech.applySavingsRate(marketSavingsTarget, bankTechLevel);
        targetLoanRate    = BankTech.applyLoanRate(marketLoanTarget, bankTechLevel);

        // On the first day, snap directly to target (no smoothing).
        // Tech level changes also snap so researched banking practices are visible immediately.
        if (!initialized || appliedBankTechLevel != bankTechLevel) {
            currentSavingsRate = targetSavingsRate;
            currentLoanRate    = targetLoanRate;
            initialized = true;
            appliedBankTechLevel = bankTechLevel;
        } else {
            currentSavingsRate += (targetSavingsRate - currentSavingsRate) * BankConstants.SAVINGS_ADJUSTMENT_SPEED;
            currentLoanRate    += (targetLoanRate    - currentLoanRate)    * BankConstants.LOAN_ADJUSTMENT_SPEED;
        }
        currentSavingsRate = Sanitize.rate(currentSavingsRate, BankConstants.MAX_SAVINGS_RATE);
        currentLoanRate    = Sanitize.rate(currentLoanRate,    BankConstants.MAX_BASE_LOAN_RATE);

        error = null;
        return null;
    }

    /**
     * Returns the daily late penalty rate based on the current economic snapshot.
     * Called once per day before processing loan installments.
     */
    public double contractedPenaltyRate() {
        double marketPenaltyRate = Sanitize.rate(
                BankConstants.LATE_PENALTY_BASE
                + currentLoanRate    * BankConstants.LATE_PENALTY_RATE_WEIGHT
                + negativeStress     * BankConstants.LATE_PENALTY_STRESS_WEIGHT
                + economicDispersion * BankConstants.LATE_PENALTY_DISPERSION_WEIGHT,
                BankConstants.MAX_DAILY_PENALTY_RATE);
        return BankTech.applyPenaltyRate(marketPenaltyRate);
    }

    boolean refreshBankTechAdjustmentIfNeeded() {
        if (!initialized) return false;

        int bankTechLevel = BankTech.levelForRates();
        if (appliedBankTechLevel < 0) {
            appliedBankTechLevel = bankTechLevel;
            return false;
        }
        if (appliedBankTechLevel == bankTechLevel) return false;

        double marketSavingsTarget = unapplyFactor(targetSavingsRate,
                BankTech.savingsRateFactorForLevel(appliedBankTechLevel));
        double marketLoanTarget = unapplyFactor(targetLoanRate,
                BankTech.loanRateFactorForLevel(appliedBankTechLevel));

        targetSavingsRate = BankTech.applySavingsRate(marketSavingsTarget, bankTechLevel);
        targetLoanRate    = BankTech.applyLoanRate(marketLoanTarget, bankTechLevel);
        currentSavingsRate = targetSavingsRate;
        currentLoanRate    = targetLoanRate;
        appliedBankTechLevel = bankTechLevel;
        return true;
    }

    private void resetSnapshot() {
        kingdoms            = 0;
        averageWealth       = 0;
        totalEconomicWeight = 0;
        averageCreditSignal = 0;
        economicDispersion  = 0;
        negativeStress      = 0;
        positiveStrength    = 0;
    }

    // The game's creditScore is centred at 1.0, so we shift by 1.0 and scale by 100.
    private double creditSignal(FactionNPC faction) {
        double creditScore = faction.stockpile.creditScore();
        if (!Double.isFinite(creditScore)) return 0;
        return CLAMP.d((creditScore - 1.0) * 100.0, -BankConstants.MAX_CREDIT_SIGNAL, BankConstants.MAX_CREDIT_SIGNAL);
    }

    // Log₂(wealthFactor + 1): gives weight 0 for broke kingdoms and grows sub-linearly,
    // so wealthy kingdoms matter more but don't completely dominate the calculation.
    private double economicWeight(double wealthFactor) {
        if (!Double.isFinite(wealthFactor)) return 1.0;
        double safe = Math.max(wealthFactor, 0.0);
        return Math.max(0.0, Math.log(safe + 1.0) / Math.log(2.0));
    }

    private double unapplyFactor(double adjustedRate, double factor) {
        if (!Double.isFinite(factor) || factor <= 0) return adjustedRate;
        return adjustedRate / factor;
    }
}
