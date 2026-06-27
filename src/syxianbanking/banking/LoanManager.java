package syxianbanking.banking;

import game.faction.FACTIONS;
import game.faction.FCredits.CTYPE;
import game.time.TIME;
import snake2d.util.misc.CLAMP;
import syxianbanking.domain.Loan;

/**
 * Manages all active loans: origination, daily settlement, early repayment and credit capacity.
 *
 * Loans array:
 *   Loans are stored in a fixed-size array of MAX_LOANS slots. When a loan is removed,
 *   the slots after it are shifted down to keep the array compact with no gaps.
 *   selectedLoan is adjusted automatically so the UI stays consistent.
 *
 * Daily amortization:
 *   Each day, processDailyLoan() tries to debit the current installment from the treasury.
 *   If the treasury is insufficient it pays what it can (partial payment) and applies a
 *   daily penalty on the remaining balance. The installment is then recalculated as the
 *   larger of two values:
 *     - Straight-line:    debtRemaining / installmentsRemaining
 *     - Amortized:        constant-payment formula covering the remaining principal
 *   Taking the max ensures accumulated penalties don't extend repayment indefinitely.
 *
 * Early repayment discount:
 *   The discount is proportional to the fraction of the debt being paid, applied only
 *   to the remaining interest portion. EARLY_PAYMENT_DISCOUNT_FACTOR controls generosity.
 *
 * Credit capacity:
 *   Available credit = playerNetWorth * creditRatio - activeLoanDebt.
 *   Both creditRatio and maxLoanInstallments vary with the economic snapshot from RateCalculator.
 */
public final class LoanManager {

    private final RateCalculator calc;

    // ---- Active loans ----
    public final Loan[] loans;
    public int  loanCount;
    int         nextLoanId  = 1;   // monotonically increasing; never reused across a campaign
    public int  selectedLoan = -1; // index of the loan selected in the UI (-1 = none)

    // ---- Computed credit capacity ----
    public double maxLoanAvailable;    // max gold available to borrow right now
    public int    maxLoanInstallments; // max term allowed in installments
    public double playerNetWorth;      // player net worth cache

    // Daily settlement output
    public double latePenaltyRate;

    // ---- Chart history arrays ----
    public final double[] rateHistory    = new double[BankConstants.HISTORY_DAYS];
    public final double[] penaltyHistory = new double[BankConstants.HISTORY_DAYS];

    public LoanManager(RateCalculator calc) {
        this.calc = calc;
        loans = new Loan[BankConstants.MAX_LOANS];
        for (int i = 0; i < BankConstants.MAX_LOANS; i++) loans[i] = new Loan();
    }

    /** Full reset; called when starting a new campaign or loading a save. */
    public void reset() {
        loanCount          = 0;
        nextLoanId         = 1;
        selectedLoan       = -1;
        maxLoanAvailable   = 0;
        maxLoanInstallments = 0;
        playerNetWorth     = 0;
        latePenaltyRate    = 0;
        for (int i = 0; i < BankConstants.MAX_LOANS; i++) loans[i].clear();
        clearHistory();
    }

    // ---- Credit capacity queries ----

    public int availableLoanAmount() {
        refreshCapacity();
        return Sanitize.positiveFloor(maxLoanAvailable);
    }

    public int maxInstallmentsAllowed() {
        refreshCapacity();
        return Math.max(1, maxLoanInstallments);
    }

    /** Max the player can pay early on a loan, bounded by treasury and the settlement amount. */
    public int availableEarlyPayment(int loanIdx, SavingsAccount savings) {
        if (!loanValid(loanIdx)) return 0;
        return Math.max(0, Math.min(
                savings.availableTreasury(),
                Sanitize.positiveCeil(requiredEarlyPaymentToSettle(loanIdx))));
    }

    // ---- Operations ----

    /**
     * Creates a new loan contract and deposits the principal into the player treasury.
     *
     * The final rate includes two premiums over the current base rate:
     *   Value premium  — cubic growth with leverage; large loans relative to net worth are costly.
     *   Term premium   — square-root growth with installments; longer terms cost moderately more.
     */
    public void contractLoan(int amount, int installments, SavingsAccount savings) {
        refreshCapacity();
        if (loanCount >= BankConstants.MAX_LOANS) return;
        amount       = Math.min(amount, availableLoanAmount());
        installments = CLAMP.i(installments, 1, maxInstallmentsAllowed());
        if (amount <= 0 || installments <= 0) return;

        double worth      = Math.max(playerNetWorth, 1.0);
        double finalRate  = finalLoanRate(amount, installments, worth);
        double daysPerYear = Math.max(1.0, TIME.years().bitConversion(TIME.days()));
        double periodRate = Math.pow(1.0 + finalRate / 100.0, 1.0 / daysPerYear) - 1.0;
        double inst       = installment(amount, periodRate, installments);
        double debt       = Math.max(amount, Sanitize.money(inst * installments));

        Loan loan = loans[loanCount];
        loan.id                    = nextLoanId++;
        loan.contractDay           = TIME.days().bitsSinceStart();
        loan.installmentsContracted = installments;
        loan.installmentsRemaining  = installments;
        loan.originalPrincipal     = amount;
        loan.netWorthAtContract    = playerNetWorth;
        loan.baseRate              = calc.currentLoanRate;
        loan.finalRate             = finalRate;
        loan.periodRate            = periodRate;
        loan.contractedInstallment = inst;
        loan.currentInstallment    = inst;
        loan.penaltyRate           = calc.contractedPenaltyRate();
        loan.principalRemaining    = amount;
        loan.debtRemaining         = Math.max(0.0, Sanitize.money(debt));
        loan.historyCount          = 0;
        loanCount++;

        FACTIONS.player().credits().inc(amount, CTYPE.MISC);
        savings.playerTreasury = Sanitize.money(FACTIONS.player().credits().credits());
        selectedLoan = loanCount - 1;
        recordLoanHistory(loanCount - 1, Loan.OP_CONTRACT, amount, 0, debt);
        refreshCapacity(); // update now that a new loan reduces available credit
    }

    /** Applies an early (partial or full) repayment to the selected loan. */
    public void prepayLoan(int loanIdx, int amount, SavingsAccount savings) {
        if (!loanValid(loanIdx) || amount <= 0) return;
        amount = Math.min(amount, availableEarlyPayment(loanIdx, savings));
        if (amount <= 0) return;

        double requiredToSettle = requiredEarlyPaymentToSettle(loanIdx);
        double paid    = Math.min(amount, requiredToSettle);
        EarlyPaymentPreview preview = earlyPaymentPreview(loanIdx, paid);

        FACTIONS.player().credits().inc(-paid, CTYPE.MISC);
        savings.playerTreasury = Sanitize.money(FACTIONS.player().credits().credits());

        if (paid >= requiredToSettle || preview.newDebt <= BankConstants.SETTLED_DEBT_EPSILON) {
            loans[loanIdx].debtRemaining         = 0;
            loans[loanIdx].principalRemaining     = 0;
            loans[loanIdx].installmentsRemaining  = 0;
            loans[loanIdx].currentInstallment     = 0;
            recordLoanHistory(loanIdx, Loan.OP_EARLY, paid, preview.discount, 0);
            removeLoan(loanIdx);
            return;
        }

        loans[loanIdx].debtRemaining      = Math.max(0.0, Sanitize.money(preview.newDebt));
        loans[loanIdx].principalRemaining = Math.max(0.0, Sanitize.money(preview.newPrincipal));
        recalculateLoanInstallment(loanIdx);
        recordLoanHistory(loanIdx, Loan.OP_EARLY, paid, preview.discount, loans[loanIdx].debtRemaining);
        refreshCapacity();
    }

    /** Processes daily installment debits for all active loans (reverse order so removals are safe). */
    public void processDailyLoans(SavingsAccount savings) {
        for (int i = loanCount - 1; i >= 0; i--) processDailyLoan(i, savings);
    }

    // ---- Read-only previews (called by UI, no state changes) ----

    public double previewLoanFinalRate(int amount, int installments) {
        if (amount <= 0 || installments <= 0) return 0;
        return finalLoanRate(amount, installments, Math.max(playerNetWorth, 1.0));
    }

    public double previewLoanInstallment(int amount, int installments) {
        if (amount <= 0 || installments <= 0) return 0;
        double daysPerYear = Math.max(1.0, TIME.years().bitConversion(TIME.days()));
        double finalRate   = previewLoanFinalRate(amount, installments);
        double periodRate  = Math.pow(1.0 + finalRate / 100.0, 1.0 / daysPerYear) - 1.0;
        return installment(amount, periodRate, installments);
    }

    public double previewLoanTotalDue(int amount, int installments) {
        if (amount <= 0 || installments <= 0) return 0;
        return Math.max(amount, previewLoanInstallment(amount, installments) * installments);
    }

    public double previewLoanTotalInterest(int amount, int installments) {
        return Math.max(0.0, previewLoanTotalDue(amount, installments) - amount);
    }

    public double previewEarlyPaymentDiscount(int loanIdx, int amount) {
        if (!loanValid(loanIdx) || amount <= 0) return 0;
        double paid = Math.min(amount, requiredEarlyPaymentToSettle(loanIdx));
        return earlyPaymentPreview(loanIdx, paid).discount;
    }

    public double previewEarlyPaymentDebtAfter(int loanIdx, int amount) {
        if (!loanValid(loanIdx) || amount <= 0) return loanValid(loanIdx) ? loans[loanIdx].debtRemaining : 0;
        double paid = Math.min(amount, requiredEarlyPaymentToSettle(loanIdx));
        return earlyPaymentPreview(loanIdx, paid).newDebt;
    }

    /**
     * Minimum payment required to fully settle the loan with the early-payment discount applied.
     *
     * Formula: required = debt / (1 + interest * DISCOUNT_FACTOR / debt)
     * This ensures the discount never exceeds the interest portion of the debt.
     */
    public double requiredEarlyPaymentToSettle(int loanIdx) {
        if (!loanValid(loanIdx)) return 0;
        Loan loan       = loans[loanIdx];
        double debt      = Math.max(0.0, loan.debtRemaining);
        double principal = Math.max(0.0, loan.principalRemaining);
        double interest  = Math.max(0.0, debt - principal);
        double denom     = 1.0 + (interest * BankConstants.EARLY_PAYMENT_DISCOUNT_FACTOR / Math.max(debt, 1.0));
        return CLAMP.d(debt / Math.max(denom, 1.0), 0.0, debt);
    }

    public boolean loanValid(int loanIdx) {
        return loanIdx >= 0 && loanIdx < loanCount;
    }

    // ---- Chart history ----

    public void pushHistory(double currentLoanRate, double penalty) {
        push(rateHistory, currentLoanRate);
        push(penaltyHistory, penalty);
    }

    public void clearHistory() {
        clear(rateHistory);
        clear(penaltyHistory);
    }

    // ---- Credit capacity ----

    /**
     * Recomputes maxLoanAvailable and maxLoanInstallments from the current
     * economic snapshot and player net worth.
     *
     * We check for non-finite values BEFORE clamping because CLAMP.d(NaN, ...) returns NaN in Java
     * (NaN comparisons are always false, so neither branch triggers in a standard min/max clamp).
     */
    public void refreshCapacity() {
        refreshPlayerNetWorth();
        double outstanding = activeLoanDebt();

        double creditRatioRaw = BankConstants.CREDIT_RATIO_BASE
                + calc.positiveStrength   * BankConstants.CREDIT_RATIO_STRENGTH_WEIGHT
                - calc.negativeStress     * BankConstants.CREDIT_RATIO_STRESS_WEIGHT
                - calc.economicDispersion * BankConstants.CREDIT_RATIO_DISPERSION_WEIGHT
                - calc.currentLoanRate    * BankConstants.CREDIT_RATIO_RATE_WEIGHT;
        if (!Double.isFinite(creditRatioRaw)) creditRatioRaw = BankConstants.MIN_CREDIT_RATIO;
        double creditRatio = CLAMP.d(creditRatioRaw, BankConstants.MIN_CREDIT_RATIO, BankConstants.MAX_CREDIT_RATIO);

        maxLoanAvailable = Math.max(0.0, Sanitize.money(playerNetWorth * creditRatio - outstanding));

        // High leverage (large debt relative to net worth) reduces available term.
        double leverage = playerNetWorth <= 0 ? 1.0 : outstanding / playerNetWorth;
        int terms = (int) Math.round(BankConstants.BASE_LOAN_TERM
                + calc.positiveStrength   * BankConstants.TERM_STRENGTH_WEIGHT
                - calc.negativeStress     * BankConstants.TERM_STRESS_WEIGHT
                - calc.economicDispersion * BankConstants.TERM_DISPERSION_WEIGHT
                - calc.currentLoanRate    * BankConstants.TERM_RATE_WEIGHT
                - leverage                * BankConstants.TERM_LEVERAGE_WEIGHT);
        maxLoanInstallments = CLAMP.i(terms, BankConstants.MIN_LOAN_TERM, BankConstants.MAX_LOAN_TERM);
    }

    // ---- Post-load normalization ----

    /**
     * Compacts and validates loans after loading a save.
     * Removes already-settled loans and restores the selected loan by ID
     * (the index may have shifted after compaction).
     * Called exclusively by BankSerializer after a full load.
     */
    void normalizeLoadedLoans() {
        int selectedId = loanValid(selectedLoan) ? loans[selectedLoan].id : -1;
        int write = 0;
        int maxId = 0;
        for (int read = 0; read < loanCount; read++) {
            if (loans[read].debtRemaining <= BankConstants.SETTLED_DEBT_EPSILON) continue;
            if (write != read) loans[write].copyFrom(loans[read]);
            normalizeLoadedLoan(write);
            maxId = Math.max(maxId, loans[write].id);
            write++;
        }
        for (int i = write; i < loanCount; i++) loans[i].clear();
        loanCount  = write;
        nextLoanId = Math.max(nextLoanId, maxId + 1); // ensure nextLoanId never reuses an existing ID

        selectedLoan = -1;
        if (selectedId > 0) {
            for (int i = 0; i < loanCount; i++) {
                if (loans[i].id == selectedId) { selectedLoan = i; break; }
            }
        }
    }

    // ---- Private implementation ----

    private void processDailyLoan(int loanIdx, SavingsAccount savings) {
        if (!loanValid(loanIdx)) return;
        Loan loan = loans[loanIdx];

        if (loan.debtRemaining <= BankConstants.SETTLED_DEBT_EPSILON) { removeLoan(loanIdx); return; }

        // Guard against save corruption leaving zero remaining installments on an active loan.
        if (loan.installmentsRemaining <= 0) {
            loan.installmentsRemaining = 1;
            recalculateLoanInstallment(loanIdx);
        }
        if (loan.currentInstallment <= 0 || !Double.isFinite(loan.currentInstallment)) {
            recalculateLoanInstallment(loanIdx);
        }

        double due       = CLAMP.d(loan.currentInstallment, 0.0, loan.debtRemaining);
        if (due <= 0) return;
        int    available = savings.availableTreasury();
        double paid      = Math.min(due, available);
        double debtBefore = loan.debtRemaining;

        if (paid > 0) {
            FACTIONS.player().credits().inc(-paid, CTYPE.MISC);
            savings.playerTreasury = Sanitize.money(FACTIONS.player().credits().credits());
            loan.debtRemaining = Math.max(0.0, Sanitize.money(loan.debtRemaining - paid));
            reducePrincipalFromPayment(loan, paid);
        }

        if (paid + BankConstants.PAYMENT_EPSILON >= due) {
            loan.installmentsRemaining = Math.max(0, loan.installmentsRemaining - 1);
            recordLoanHistory(loanIdx, Loan.OP_PAYMENT, paid, 0, loan.debtRemaining);
        } else {
            if (paid > 0) recordLoanHistory(loanIdx, Loan.OP_PARTIAL, paid, 0, loan.debtRemaining);
            loan.penaltyRate = Sanitize.rate(loan.penaltyRate, BankConstants.MAX_DAILY_PENALTY_RATE);
            double penalty = Sanitize.money(loan.debtRemaining * (loan.penaltyRate / 100.0));
            if (penalty > 0) {
                loan.debtRemaining = Math.max(0.0, Sanitize.money(loan.debtRemaining + penalty));
                recordLoanHistory(loanIdx, Loan.OP_PENALTY, penalty, 0, loan.debtRemaining);
            }
        }

        if (loan.debtRemaining <= BankConstants.SETTLED_DEBT_EPSILON
                || (loan.installmentsRemaining <= 0 && debtBefore <= due + BankConstants.PAYMENT_EPSILON)) {
            removeLoan(loanIdx);
            return;
        }
        recalculateLoanInstallment(loanIdx);
    }

    // Pays accrued interest first; the remainder reduces principal.
    private void reducePrincipalFromPayment(Loan loan, double paid) {
        double interestDue   = Math.max(0.0, loan.principalRemaining * loan.periodRate);
        double principalPaid = Math.max(0.0, paid - interestDue);
        loan.principalRemaining = Math.max(0.0, Sanitize.money(loan.principalRemaining - principalPaid));
        // Principal cannot exceed total debt (possible after penalties inflate debtRemaining).
        if (loan.debtRemaining < loan.principalRemaining) loan.principalRemaining = loan.debtRemaining;
    }

    // Takes the max of straight-line and amortized installment so penalties don't extend repayment forever.
    private void recalculateLoanInstallment(int loanIdx) {
        if (!loanValid(loanIdx)) return;
        Loan loan = loans[loanIdx];
        if (loan.debtRemaining <= BankConstants.SETTLED_DEBT_EPSILON) {
            loan.debtRemaining         = 0;
            loan.principalRemaining    = 0;
            loan.currentInstallment    = 0;
            loan.installmentsRemaining = 0;
            return;
        }
        if (loan.installmentsRemaining <= 0) loan.installmentsRemaining = 1;
        double straightLine = loan.debtRemaining / loan.installmentsRemaining;
        double amortized    = installment(loan.principalRemaining, loan.periodRate, loan.installmentsRemaining);
        loan.currentInstallment = Sanitize.money(Math.max(straightLine, amortized));
    }

    // discount = remainingInterest * (paid / debt) * DISCOUNT_FACTOR
    private EarlyPaymentPreview earlyPaymentPreview(int loanIdx, double paid) {
        Loan loan = loans[loanIdx];
        double debtBefore  = loan.debtRemaining;
        double prinBefore  = loan.principalRemaining;
        paid = CLAMP.d(paid, 0.0, debtBefore);
        double interest    = Math.max(0.0, debtBefore - prinBefore);
        double pct         = paid / Math.max(debtBefore, 1.0);
        double discount    = Math.min(interest, interest * pct * BankConstants.EARLY_PAYMENT_DISCOUNT_FACTOR);
        double newPrincipal = prinBefore - Math.min(prinBefore, paid);
        double newDebt      = Math.max(0.0, Math.max(debtBefore - paid - discount, newPrincipal));
        if (paid >= requiredEarlyPaymentToSettle(loanIdx) || newDebt <= BankConstants.SETTLED_DEBT_EPSILON) {
            newDebt     = 0;
            newPrincipal = 0;
        }
        return new EarlyPaymentPreview(newDebt, newPrincipal, discount);
    }

    // Inserts a new event at index 0, shifting older entries forward.
    private void recordLoanHistory(int loanIdx, int type, double amount, double discount, double balance) {
        if (!loanValid(loanIdx)) return;
        Loan loan = loans[loanIdx];
        for (int i = Loan.MAX_HISTORY - 1; i > 0; i--) {
            loan.historyTypes[i]     = loan.historyTypes[i - 1];
            loan.historyDays[i]      = loan.historyDays[i - 1];
            loan.historyAmounts[i]   = loan.historyAmounts[i - 1];
            loan.historyDiscounts[i] = loan.historyDiscounts[i - 1];
            loan.historyBalances[i]  = loan.historyBalances[i - 1];
        }
        loan.historyTypes[0]     = type;
        loan.historyDays[0]      = TIME.days().bitsSinceStart();
        loan.historyAmounts[0]   = amount;
        loan.historyDiscounts[0] = discount;
        loan.historyBalances[0]  = balance;
        if (loan.historyCount < Loan.MAX_HISTORY) loan.historyCount++;
    }

    // Compacts the array by shifting slots after loanIdx down by one, then adjusts selectedLoan.
    private void removeLoan(int loanIdx) {
        if (!loanValid(loanIdx)) return;
        for (int i = loanIdx; i < loanCount - 1; i++) loans[i].copyFrom(loans[i + 1]);
        loanCount--;
        loans[loanCount].clear();
        if      (selectedLoan == loanIdx) selectedLoan = loanCount > 0 ? Math.min(loanIdx, loanCount - 1) : -1;
        else if (selectedLoan > loanIdx)  selectedLoan--;
        refreshCapacity();
    }

    // Value premium grows cubically with leverage; term premium grows with square root of installments.
    private double finalLoanRate(double amount, int installments, double netWorth) {
        double leverage    = CLAMP.d(amount / Math.max(netWorth, 1.0), 0.0, BankConstants.MAX_RATE_LEVERAGE);
        double valuePrem   = (Math.pow(1.0 + leverage, 3.0) - 1.0) * BankConstants.LOAN_VALUE_PREMIUM_WEIGHT;
        double termPrem    = Math.sqrt(installments / BankConstants.TERM_PREMIUM_REFERENCE_INSTALLMENTS)
                * BankConstants.LOAN_TERM_PREMIUM_WEIGHT;
        return Sanitize.rate(calc.currentLoanRate + valuePrem + termPrem, BankConstants.MAX_CONTRACT_LOAN_RATE);
    }

    // Standard constant-payment (price) formula: PMT = PV * r / (1 - (1+r)^-n)
    private double installment(double amount, double periodRate, int installments) {
        if (installments <= 0) return amount;
        if (periodRate  <= 0)  return amount / installments;
        double payment = amount * periodRate / (1.0 - Math.pow(1.0 + periodRate, -installments));
        return Double.isFinite(payment) && payment > 0 ? Sanitize.money(payment) : amount;
    }

    private double activeLoanDebt() {
        double debt = 0;
        for (int i = 0; i < loanCount; i++) debt += Math.max(0.0, loans[i].debtRemaining);
        return Math.max(0.0, Sanitize.money(debt));
    }

    private void refreshPlayerNetWorth() {
        try {
            playerNetWorth = Math.max(0.0, Sanitize.money(FACTIONS.WORTH().faction(FACTIONS.player())));
        } catch (Throwable ignored) {
        }
    }

    private void normalizeLoadedLoan(int loanIdx) {
        Loan loan = loans[loanIdx];
        loan.id = Math.max(1, loan.id);
        loan.installmentsContracted = CLAMP.i(loan.installmentsContracted, 1, 365);
        if (loan.installmentsRemaining <= 0 && loan.debtRemaining > BankConstants.SETTLED_DEBT_EPSILON)
            loan.installmentsRemaining = 1;
        loan.installmentsRemaining = CLAMP.i(loan.installmentsRemaining, 0, loan.installmentsContracted);
        loan.originalPrincipal     = Math.max(0.0, Sanitize.money(loan.originalPrincipal));
        loan.netWorthAtContract    = Math.max(0.0, Sanitize.money(loan.netWorthAtContract));
        loan.baseRate              = Sanitize.rate(loan.baseRate, BankConstants.MAX_BASE_LOAN_RATE);
        loan.finalRate             = Sanitize.rate(loan.finalRate, BankConstants.MAX_CONTRACT_LOAN_RATE);
        // Fallback for old saves that didn't store a final rate.
        if (loan.finalRate <= 0 && calc.currentLoanRate > 0) loan.finalRate = calc.currentLoanRate;
        loan.periodRate = CLAMP.d(loan.periodRate, 0.0, 1.0);
        if (loan.periodRate <= 0 && loan.finalRate > 0) {
            double dpy = Math.max(1.0, TIME.years().bitConversion(TIME.days()));
            loan.periodRate = Math.pow(1.0 + loan.finalRate / 100.0, 1.0 / dpy) - 1.0;
        }
        loan.penaltyRate        = Sanitize.rate(loan.penaltyRate, BankConstants.MAX_DAILY_PENALTY_RATE);
        loan.principalRemaining = CLAMP.d(loan.principalRemaining, 0.0, loan.debtRemaining);
        if (loan.principalRemaining <= 0 && loan.debtRemaining > BankConstants.SETTLED_DEBT_EPSILON)
            loan.principalRemaining = Math.min(loan.originalPrincipal, loan.debtRemaining);
        if (loan.contractedInstallment <= 0 || !Double.isFinite(loan.contractedInstallment))
            loan.contractedInstallment = loan.debtRemaining / Math.max(1, loan.installmentsContracted);
        if (loan.currentInstallment <= 0 || !Double.isFinite(loan.currentInstallment))
            recalculateLoanInstallment(loanIdx);
    }

    private void push(double[] history, double value) {
        for (int i = 0; i < history.length - 1; i++) history[i] = history[i + 1];
        history[history.length - 1] = Double.isFinite(value) ? value : 0;
    }

    private void clear(double[] arr) {
        for (int i = 0; i < arr.length; i++) arr[i] = 0;
    }

    // Immutable result of an early-payment preview calculation.
    static final class EarlyPaymentPreview {
        final double newDebt;
        final double newPrincipal;
        final double discount;

        EarlyPaymentPreview(double newDebt, double newPrincipal, double discount) {
            this.newDebt      = newDebt;
            this.newPrincipal = newPrincipal;
            this.discount     = discount;
        }
    }
}
