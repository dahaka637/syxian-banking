package syxianbanking.banking;

import game.time.TIME;
import syxianbanking.TR;

/**
 * Central orchestrator — coordinates the daily settlement between RateCalculator,
 * SavingsAccount and LoanManager, and exposes the singleton INSTANCE to the whole mod.
 *
 * Daily settlement order (executed once per game day in updateIfNeeded):
 *   1. Calculate market rates      — RateCalculator.calculate()
 *   2. Refresh player treasury cache
 *   3. Compute credit capacity and available terms
 *   4. Compute today's late penalty rate
 *   5. Apply daily savings interest — SavingsAccount.applyDailyInterest()
 *   6. Process loan installments   — LoanManager.processDailyLoans()
 *   7. Recompute credit capacity (loans may have been settled in step 6)
 *   8. Push values into chart history arrays
 *
 * Outside the settlement, updateIfNeeded() is also called every render frame by
 * BankPanel. When the day has not changed only credit capacity is refreshed (cheap).
 */
public final class BankState {

    /** Singleton shared across the whole mod. */
    public static final BankState INSTANCE = new BankState();

    public final RateCalculator calculator = new RateCalculator();
    public final SavingsAccount savings    = new SavingsAccount();
    public final LoanManager    loans      = new LoanManager(calculator);

    public int     historySamples;     // number of valid entries in the chart history arrays
    public boolean historyInitialized; // true once at least one history sample has been recorded
    public String  error;              // last calculation error message; null = OK

    // Game day of the last settlement; package-private so BankSerializer can restore it.
    int day = Integer.MIN_VALUE;

    private BankState() {}

    /** Resets all state for a fresh campaign or an empty save slot. */
    public void resetForNewContext() {
        day               = Integer.MIN_VALUE;
        historySamples    = 0;
        historyInitialized = false;
        error             = null;
        calculator.reset();
        savings.reset();
        loans.reset();
    }

    /**
     * Checks whether the game day has advanced; if so, runs the full daily settlement.
     * If the day has not changed, only refreshes credit capacity (lightweight path).
     *
     * Called every render frame by BankPanel and also by Instance.update() each tick.
     */
    public void updateIfNeeded() {
        int currentDay = TIME.days().bitsSinceStart();
        if (currentDay == day) {
            loans.syncSavingsCollateral(savings);
            loans.refreshCapacity();
            return;
        }
        day = currentDay;
        calculate();
    }

    private void calculate() {
        try {
            // 1. Market rates
            String calcError = calculator.calculate();
            if (calcError != null) { error = calcError; return; }

            // 2–3. Treasury and credit capacity
            savings.refreshTreasury();
            loans.syncSavingsCollateral(savings);
            loans.refreshCapacity();

            // 4. Late penalty rate for today (must be set before processing installments)
            double daysPerYear    = TIME.years().bitConversion(TIME.days());
            loans.latePenaltyRate = calculator.contractedPenaltyRate();

            // 5. Savings interest
            savings.applyDailyInterest(calculator.currentSavingsRate, daysPerYear);

            // 6. Loan installments
            loans.processDailyLoans(savings);

            // 7. Recompute capacity (settled loans free up available credit)
            loans.syncSavingsCollateral(savings);
            loans.refreshCapacity();

            // 8. Chart history
            if (!historyInitialized) historyInitialized = true;
            savings.pushHistory(calculator.currentSavingsRate);
            loans.pushHistory(calculator.currentLoanRate, loans.latePenaltyRate);
            if (historySamples < BankConstants.HISTORY_DAYS) historySamples++;

            error = null;
        } catch (Throwable e) {
            error = TR.s("error.calculate").toString() + e.getClass().getSimpleName();
        }
    }
}
