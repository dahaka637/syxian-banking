package syxianbanking.banking;

import game.faction.FACTIONS;
import game.faction.FCredits.CTYPE;
import game.time.TIME;

/**
 * Manages the player's savings account: balance, deposits, withdrawals, daily interest and operation log.
 *
 * Daily interest uses a fractional accumulator (interestRemainder):
 *   Daily gain = balance * (rate / 100) / daysPerYear.
 *   Because this value is often less than 1 gold, fractions accumulate in interestRemainder
 *   until the total reaches at least 1 whole gold coin before paying out.
 *   This prevents small balances from never earning interest.
 *
 * The operation log is a fixed-size LIFO stack of MAX_OPERATIONS entries.
 * The most recent entry is always at index 0. When full, the oldest entry is silently dropped.
 *
 * Reinvest mode:
 *   true  — interest is credited back into the bank balance (compound interest).
 *   false — interest is transferred to the player faction's treasury (simple interest).
 */
public final class SavingsAccount {

    // ---- Balance and settings ----
    public double  balance;
    double         interestRemainder; // fractional interest accumulated but not yet paid out
    public boolean reinvest;

    // Treasury cache — refreshed after every transaction for consistency within a game tick.
    double playerTreasury;

    // ---- Operation log (most recent at index 0) ----
    public int      operationCount;
    public final int[]    operationTypes    = new int[BankConstants.MAX_OPERATIONS];
    public final int[]    operationDays     = new int[BankConstants.MAX_OPERATIONS];
    public final double[] operationAmounts  = new double[BankConstants.MAX_OPERATIONS];
    public final double[] operationBalances = new double[BankConstants.MAX_OPERATIONS];

    // ---- Chart history arrays ----
    public final double[] rateHistory    = new double[BankConstants.HISTORY_DAYS];
    public final double[] balanceHistory = new double[BankConstants.HISTORY_DAYS];

    /** Full reset; called when starting a new campaign or loading a save. */
    public void reset() {
        balance           = 0;
        interestRemainder = 0;
        reinvest          = false;
        playerTreasury    = 0;
        operationCount    = 0;
        for (int i = 0; i < BankConstants.MAX_OPERATIONS; i++) {
            operationTypes[i]    = 0;
            operationDays[i]     = 0;
            operationAmounts[i]  = 0;
            operationBalances[i] = 0;
        }
        clearHistory();
    }

    /** Refreshes the treasury cache by reading the current value from the game. */
    public void refreshTreasury() {
        try {
            playerTreasury = Sanitize.money(FACTIONS.player().credits().credits());
        } catch (Throwable ignored) {
        }
    }

    /** Moves amount gold from the player treasury into the savings account. Capped by available treasury. */
    public void deposit(int amount) {
        if (amount <= 0) return;
        int available = availableTreasury();
        amount = Math.min(amount, available);
        if (amount <= 0) return;
        FACTIONS.player().credits().inc(-amount, CTYPE.MISC);
        balance        = Math.max(0.0, Sanitize.money(balance + amount));
        playerTreasury = Sanitize.money(FACTIONS.player().credits().credits());
        recordOperation(BankConstants.OP_DEPOSIT, amount);
    }

    /** Moves amount gold from the savings account into the player treasury. Capped by current balance. */
    public void withdraw(int amount) {
        if (amount <= 0) return;
        amount = Math.min(amount, availableBalance());
        if (amount <= 0) return;
        balance        = Math.max(0.0, Sanitize.money(balance - amount));
        FACTIONS.player().credits().inc(amount, CTYPE.MISC);
        playerTreasury = Sanitize.money(FACTIONS.player().credits().credits());
        recordOperation(BankConstants.OP_WITHDRAW, -amount);
    }

    /** Returns available treasury gold as a floor integer. */
    public int availableTreasury() {
        return Sanitize.positiveFloor(FACTIONS.player().credits().credits());
    }

    /** Returns available savings balance as a floor integer. */
    public int availableBalance() {
        return Sanitize.positiveFloor(balance);
    }

    /**
     * Applies one day of savings interest using the fractional accumulator.
     * Must be called once per game day after currentSavingsRate is computed.
     */
    public void applyDailyInterest(double currentSavingsRate, double daysPerYear) {
        if (balance <= 0 || currentSavingsRate <= 0 || daysPerYear <= 0) return;

        // Accumulate the fractional daily gain; only pay out when >= 1 whole gold.
        interestRemainder = Math.max(0.0, Sanitize.money(
                interestRemainder + balance * (currentSavingsRate / 100.0) / daysPerYear));

        int payout = Sanitize.positiveFloor(interestRemainder);
        if (payout <= 0) return;
        interestRemainder -= payout;

        if (reinvest) {
            balance = Math.max(0.0, Sanitize.money(balance + payout));
        } else {
            FACTIONS.player().credits().inc(payout, CTYPE.MISC);
            playerTreasury = Sanitize.money(FACTIONS.player().credits().credits());
        }
        recordOperation(BankConstants.OP_INTEREST, payout);
    }

    /** Records today's rate and balance into the chart history arrays. */
    public void pushHistory(double currentSavingsRate) {
        push(rateHistory, currentSavingsRate);
        push(balanceHistory, balance);
    }

    public void clearHistory() {
        clear(rateHistory);
        clear(balanceHistory);
    }

    boolean operationTypeValid(int type) {
        return type >= BankConstants.OP_DEPOSIT && type <= BankConstants.OP_INTEREST;
    }

    // Inserts a new entry at index 0, shifting older entries forward.
    // When the log is full the oldest entry is overwritten.
    private void recordOperation(int type, double amount) {
        for (int i = BankConstants.MAX_OPERATIONS - 1; i > 0; i--) {
            operationTypes[i]    = operationTypes[i - 1];
            operationDays[i]     = operationDays[i - 1];
            operationAmounts[i]  = operationAmounts[i - 1];
            operationBalances[i] = operationBalances[i - 1];
        }
        operationTypes[0]    = type;
        operationDays[0]     = TIME.days().bitsSinceStart();
        operationAmounts[0]  = Sanitize.money(amount);
        operationBalances[0] = Math.max(0.0, Sanitize.money(balance));
        if (operationCount < BankConstants.MAX_OPERATIONS) operationCount++;
    }

    private void push(double[] history, double value) {
        for (int i = 0; i < history.length - 1; i++) history[i] = history[i + 1];
        history[history.length - 1] = Double.isFinite(value) ? value : 0;
    }

    private void clear(double[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = 0;
    }
}
