package syxianbanking.domain;

import syxianbanking.banking.BankConstants;

/**
 * Represents a single active loan contract.
 *
 * Replaces the original parallel-array pattern (13 separate arrays for loan state)
 * with a single object, making loan data access and manipulation far safer and cleaner.
 *
 * Lifecycle:
 *   - Created by LoanManager.contractLoan() with the negotiated terms.
 *   - Each game day, LoanManager.processDailyLoan() debits one installment and logs the event.
 *   - Removed automatically when debtRemaining <= SETTLED_DEBT_EPSILON,
 *     or when the player calls LoanManager.prepayLoan() for full early settlement.
 *
 * Amortization: each daily installment first covers accrued interest
 * (principalRemaining * periodRate) and the remainder reduces principalRemaining.
 * Late penalties are added to debtRemaining without touching principalRemaining.
 */
public final class Loan {

    public static final int MAX_HISTORY = 100;

    // ---- Loan event type codes (used in the history arrays) ----
    public static final int OP_CONTRACT = 0; // contract created
    public static final int OP_PAYMENT  = 1; // installment paid in full
    public static final int OP_PARTIAL  = 2; // installment paid partially (insufficient funds)
    public static final int OP_PENALTY  = 3; // late penalty applied
    public static final int OP_EARLY    = 4; // early repayment
    public static final int OP_CAP      = 5; // late penalties permanently capped

    // ---- Contract identity and terms ----
    public int id;                       // unique sequential ID across the campaign
    public int contractDay;              // game day when the loan was created
    public int installmentsContracted;   // number of installments agreed at signing
    public int installmentsRemaining;    // installments still outstanding

    // ---- Financial values ----
    public double originalPrincipal;     // gross amount borrowed
    public double netWorthAtContract;    // player net worth at contract date
    public double baseRate;              // market base rate at contract date (% p.a.)
    public double finalRate;             // final rate including value and term premiums (% p.a.)
    // Daily equivalent rate derived from finalRate via compound interest formula.
    public double periodRate;
    public double contractedInstallment; // installment calculated at signing
    public double currentInstallment;    // recalculated installment after penalties or partial prepay
    public double penaltyRate;           // daily late penalty rate (% of outstanding balance)
    public boolean penaltyCapped;        // true once this contract reaches the penalty cap
    public double principalRemaining;    // principal not yet amortized
    public double debtRemaining;         // total outstanding debt (principal + accrued interest + penalties)

    // ---- Event history (most recent at index 0) ----
    public int historyCount;
    public final int[]    historyTypes     = new int[MAX_HISTORY];
    public final int[]    historyDays      = new int[MAX_HISTORY];
    public final double[] historyAmounts   = new double[MAX_HISTORY];
    public final double[] historyDiscounts = new double[MAX_HISTORY]; // early-payment discount received
    public final double[] historyBalances  = new double[MAX_HISTORY]; // balance after the event

    // ---- Daily debt chart history (most recent at the end of the arrays) ----
    public int debtHistoryCount;
    public final int[]    debtHistoryDays   = new int[BankConstants.LOAN_DEBT_HISTORY_DAYS];
    public final double[] debtHistoryValues = new double[BankConstants.LOAN_DEBT_HISTORY_DAYS];

    /** Zeros all fields. Called when removing a loan or reusing a slot in the loans array. */
    public void clear() {
        id = 0;
        contractDay = 0;
        installmentsContracted = 0;
        installmentsRemaining = 0;
        originalPrincipal = 0;
        netWorthAtContract = 0;
        baseRate = 0;
        finalRate = 0;
        periodRate = 0;
        contractedInstallment = 0;
        currentInstallment = 0;
        penaltyRate = 0;
        penaltyCapped = false;
        principalRemaining = 0;
        debtRemaining = 0;
        historyCount = 0;
        clearDebtHistory();
        for (int h = 0; h < MAX_HISTORY; h++) {
            historyTypes[h] = 0;
            historyDays[h] = 0;
            historyAmounts[h] = 0;
            historyDiscounts[h] = 0;
            historyBalances[h] = 0;
        }
    }

    /** Deep-copies all data from src into this object. Used when compacting the loans array after removal. */
    public void copyFrom(Loan src) {
        id = src.id;
        contractDay = src.contractDay;
        installmentsContracted = src.installmentsContracted;
        installmentsRemaining = src.installmentsRemaining;
        originalPrincipal = src.originalPrincipal;
        netWorthAtContract = src.netWorthAtContract;
        baseRate = src.baseRate;
        finalRate = src.finalRate;
        periodRate = src.periodRate;
        contractedInstallment = src.contractedInstallment;
        currentInstallment = src.currentInstallment;
        penaltyRate = src.penaltyRate;
        penaltyCapped = src.penaltyCapped;
        principalRemaining = src.principalRemaining;
        debtRemaining = src.debtRemaining;
        historyCount = src.historyCount;
        debtHistoryCount = src.debtHistoryCount;
        for (int h = 0; h < MAX_HISTORY; h++) {
            historyTypes[h] = src.historyTypes[h];
            historyDays[h] = src.historyDays[h];
            historyAmounts[h] = src.historyAmounts[h];
            historyDiscounts[h] = src.historyDiscounts[h];
            historyBalances[h] = src.historyBalances[h];
        }
        for (int h = 0; h < debtHistoryValues.length; h++) {
            debtHistoryDays[h] = src.debtHistoryDays[h];
            debtHistoryValues[h] = src.debtHistoryValues[h];
        }
    }

    /** Clears the per-loan chart history without touching contract terms or event history. */
    public void clearDebtHistory() {
        debtHistoryCount = 0;
        for (int h = 0; h < debtHistoryValues.length; h++) {
            debtHistoryDays[h] = 0;
            debtHistoryValues[h] = 0;
        }
    }

    /** Records one daily debt sample, overwriting the current day if already present. */
    public void recordDebtHistory(int day, double debt) {
        int last = debtHistoryValues.length - 1;
        if (debtHistoryCount > 0 && debtHistoryDays[last] == day) {
            debtHistoryValues[last] = debt;
            return;
        }
        for (int h = 0; h < last; h++) {
            debtHistoryDays[h] = debtHistoryDays[h + 1];
            debtHistoryValues[h] = debtHistoryValues[h + 1];
        }
        debtHistoryDays[last] = day;
        debtHistoryValues[last] = debt;
        if (debtHistoryCount < debtHistoryValues.length) debtHistoryCount++;
    }

    /** Returns true if type is a valid event code for the history arrays. */
    public static boolean historyTypeValid(int type) {
        return type >= OP_CONTRACT && type <= OP_CAP;
    }
}
