package syxianbanking.banking;

import java.io.IOException;

import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.misc.CLAMP;
import syxianbanking.domain.Loan;

/**
 * Handles binary serialization and deserialization of all bank state.
 *
 * Save format layout:
 *
 *   [V1 block — always present, never reorder or remove fields]
 *     bool initialized | double currentSavingsRate | double currentLoanRate
 *     double targetSavingsRate | double targetLoanRate | int day
 *     int SAVE_MARK
 *     double balance | double interestRemainder | bool reinvest
 *     int operationCount | [operation entries...] | int nextLoanId | int loanCount
 *     int selectedLoan | [loan entries...]
 *
 *   [V2 block — optional, present only in saves created after V2 was added]
 *     int SAVE_V2_MARK
 *     [economic snapshot: averageWealth, totalEconomicWeight, ...]
 *     [chart histories: savings rate, loan rate, penalty, balance]
 *     int SAVE_V2_END_MARK
 *
 * Compatibility rules:
 *   - Never reorder or remove fields from the V1 block.
 *   - Add new data after SAVE_V2_END_MARK with a new V3 marker block.
 *   - Load tests each marker before reading — old saves without V2 are valid.
 *   - MAX_SAVED_* limits guard against absurdly large counts in corrupted saves.
 */
public final class BankSerializer {
    private BankSerializer() {}

    // ---- Save ----

    /** Serializes all bank state into the campaign save file. */
    public static void save(FilePutter file, BankState state) {
        // V1 block — field order is fixed; do not change.
        file.bool(state.calculator.initialized);
        file.d(state.calculator.currentSavingsRate);
        file.d(state.calculator.currentLoanRate);
        file.d(state.calculator.targetSavingsRate);
        file.d(state.calculator.targetLoanRate);
        file.i(state.day);
        file.i(BankConstants.SAVE_MARK);
        file.d(state.savings.balance);
        file.d(state.savings.interestRemainder);
        file.bool(state.savings.reinvest);
        file.i(state.savings.operationCount);
        for (int i = 0; i < state.savings.operationCount; i++) {
            file.i(state.savings.operationTypes[i]);
            file.i(state.savings.operationDays[i]);
            file.d(state.savings.operationAmounts[i]);
            file.d(state.savings.operationBalances[i]);
        }
        file.i(state.loans.nextLoanId);
        file.i(state.loans.loanCount);
        file.i(state.loans.selectedLoan);
        for (int i = 0; i < state.loans.loanCount; i++) saveLoan(file, state.loans.loans[i]);
        saveV2Extension(file, state);
    }

    // ---- Load ----

    /**
     * Deserializes bank state from the campaign save file.
     * Resets state before reading so any field absent in old saves keeps its default value.
     */
    public static void load(FileGetter file, BankState state) throws IOException {
        // V1 — economic rates come first (before SAVE_MARK)
        state.calculator.initialized        = file.bool();
        state.calculator.currentSavingsRate = Sanitize.rate(file.d(), BankConstants.MAX_SAVINGS_RATE);
        state.calculator.currentLoanRate    = Sanitize.rate(file.d(), BankConstants.MAX_BASE_LOAN_RATE);
        state.calculator.targetSavingsRate  = Sanitize.rate(file.d(), BankConstants.MAX_SAVINGS_RATE);
        state.calculator.targetLoanRate     = Sanitize.rate(file.d(), BankConstants.MAX_BASE_LOAN_RATE);
        state.day = file.i();

        // Reset ensures zero defaults for any field the old save doesn't contain.
        state.savings.reset();
        state.loans.reset();
        state.historySamples    = 0;
        state.historyInitialized = false;

        // Very old saves (before SAVE_MARK) stop here.
        if (file.remainingInts() <= 0 || !file.test(BankConstants.SAVE_MARK)) {
            state.loans.refreshCapacity();
            return;
        }

        // Savings account
        state.savings.balance           = Math.max(0.0, Sanitize.money(file.d()));
        state.savings.interestRemainder = Math.max(0.0, Sanitize.money(file.d()));
        state.savings.reinvest          = file.bool();

        // Operation log
        int savedOps = file.i();
        if (savedOps < 0 || savedOps > BankConstants.MAX_OPERATIONS * 10)
            throw new IOException("invalid saved operation count: " + savedOps);
        for (int i = 0; i < savedOps; i++) {
            int    type    = file.i();
            int    opDay   = file.i();
            double amount  = Sanitize.money(file.d());
            double balance = Math.max(0.0, Sanitize.money(file.d()));
            // Discard invalid types or entries beyond the runtime cap (bytes must still be read).
            if (state.savings.operationTypeValid(type) && state.savings.operationCount < BankConstants.MAX_OPERATIONS) {
                int idx = state.savings.operationCount;
                state.savings.operationTypes[idx]    = type;
                state.savings.operationDays[idx]     = opDay;
                state.savings.operationAmounts[idx]  = amount;
                state.savings.operationBalances[idx] = balance;
                state.savings.operationCount++;
            }
        }

        // Loans
        state.loans.nextLoanId  = Math.max(1, file.i());
        int savedLoans = file.i();
        if (savedLoans < 0 || savedLoans > BankConstants.MAX_SAVED_LOANS)
            throw new IOException("invalid saved loan count: " + savedLoans);
        state.loans.selectedLoan = file.i();
        for (int i = 0; i < savedLoans; i++) {
            // Only store the first MAX_LOANS; extras are read and discarded to advance the file pointer.
            boolean store = i < BankConstants.MAX_LOANS;
            loadLoan(file, store ? state.loans.loans[i] : null);
            if (store) state.loans.loanCount++;
        }
        state.loans.normalizeLoadedLoans();
        if (!state.loans.loanValid(state.loans.selectedLoan))
            state.loans.selectedLoan = state.loans.loanCount > 0 ? 0 : -1;

        // V2 block is optional — present only in saves written after V2 was introduced.
        if (file.remainingInts() > 0 && file.test(BankConstants.SAVE_V2_MARK))
            loadV2Extension(file, state);

        state.loans.refreshCapacity();
    }

    // ---- V2 extension: economic snapshot + chart histories ----

    private static void saveV2Extension(FilePutter file, BankState state) {
        file.i(BankConstants.SAVE_V2_MARK);
        file.d(Sanitize.money(state.calculator.averageWealth));
        file.d(Math.max(0.0, Sanitize.economicSignal(state.calculator.totalEconomicWeight)));
        file.d(Sanitize.economicSignal(state.calculator.averageCreditSignal));
        file.d(Math.max(0.0, Sanitize.economicSignal(state.calculator.economicDispersion)));
        file.d(Math.max(0.0, Sanitize.economicSignal(state.calculator.negativeStress)));
        file.d(Math.max(0.0, Sanitize.economicSignal(state.calculator.positiveStrength)));
        file.d(Math.max(0.0, Sanitize.money(state.loans.playerNetWorth)));
        file.d(Math.max(0.0, Sanitize.money(state.loans.maxLoanAvailable)));
        file.d(Sanitize.rate(state.loans.latePenaltyRate, BankConstants.MAX_DAILY_PENALTY_RATE));
        file.i(CLAMP.i(state.loans.maxLoanInstallments, 1, 365));
        file.i(CLAMP.i(state.historySamples, 0, BankConstants.HISTORY_DAYS));
        file.i(state.historyInitialized ? 1 : 0);
        // History order must match exactly in save and load.
        saveHistory(file, state.savings.rateHistory);
        saveHistory(file, state.loans.rateHistory);
        saveHistory(file, state.loans.penaltyHistory);
        saveHistory(file, state.savings.balanceHistory);
        file.i(BankConstants.SAVE_V2_END_MARK);
    }

    private static void loadV2Extension(FileGetter file, BankState state) throws IOException {
        state.calculator.averageWealth       = Math.max(0.0, Sanitize.money(file.d()));
        state.calculator.totalEconomicWeight = Math.max(0.0, Sanitize.economicSignal(file.d()));
        state.calculator.averageCreditSignal = Sanitize.economicSignal(file.d());
        state.calculator.economicDispersion  = Math.max(0.0, Sanitize.economicSignal(file.d()));
        state.calculator.negativeStress      = Math.max(0.0, Sanitize.economicSignal(file.d()));
        state.calculator.positiveStrength    = Math.max(0.0, Sanitize.economicSignal(file.d()));
        state.loans.playerNetWorth           = Math.max(0.0, Sanitize.money(file.d()));
        state.loans.maxLoanAvailable         = Math.max(0.0, Sanitize.money(file.d()));
        state.loans.latePenaltyRate          = Sanitize.rate(file.d(), BankConstants.MAX_DAILY_PENALTY_RATE);
        state.loans.maxLoanInstallments      = CLAMP.i(file.i(), 1, 365);
        state.historySamples                 = CLAMP.i(file.i(), 0, BankConstants.HISTORY_DAYS);
        state.historyInitialized             = file.i() != 0 && state.historySamples > 0;
        loadHistory(file, state.savings.rateHistory,    BankConstants.MAX_SAVINGS_RATE);
        loadHistory(file, state.loans.rateHistory,      BankConstants.MAX_BASE_LOAN_RATE);
        loadHistory(file, state.loans.penaltyHistory,   BankConstants.MAX_DAILY_PENALTY_RATE);
        loadHistory(file, state.savings.balanceHistory, BankConstants.MAX_MONEY);
        if (file.remainingInts() > 0)
            file.test(BankConstants.SAVE_V2_END_MARK); // consume sentinel to keep file pointer consistent
    }

    // ---- History helpers ----

    private static void saveHistory(FilePutter file, double[] history) {
        file.i(history.length);
        for (double v : history) file.d(Double.isFinite(v) ? v : 0.0);
    }

    /**
     * Loads a variable-length history, right-aligning it in the target array.
     * If the saved history is longer than the target, the oldest entries are discarded.
     * If shorter, the beginning of the array stays zeroed (data not yet collected).
     */
    private static void loadHistory(FileGetter file, double[] target, double maxValue) throws IOException {
        for (int i = 0; i < target.length; i++) target[i] = 0;
        int savedLength = file.i();
        if (savedLength < 0 || savedLength > BankConstants.MAX_SAVED_HISTORY_LENGTH)
            throw new IOException("invalid saved history length: " + savedLength);
        int firstKept    = Math.max(0, savedLength - target.length);
        int targetOffset = Math.max(0, target.length - savedLength);
        for (int i = 0; i < savedLength; i++) {
            double value = Sanitize.historyValue(file.d(), maxValue);
            if (i >= firstKept) target[targetOffset + i - firstKept] = value;
        }
    }

    // ---- Loan serialization ----

    /** Writes all loan fields in the order defined by the V1 format. */
    private static void saveLoan(FilePutter file, Loan loan) {
        file.i(loan.id);
        file.i(loan.contractDay);
        file.i(loan.installmentsContracted);
        file.i(loan.installmentsRemaining);
        file.d(loan.originalPrincipal);
        file.d(loan.netWorthAtContract);
        file.d(loan.baseRate);
        file.d(loan.finalRate);
        file.d(loan.periodRate);
        file.d(loan.contractedInstallment);
        file.d(loan.currentInstallment);
        file.d(loan.penaltyRate);
        file.d(loan.principalRemaining);
        file.d(loan.debtRemaining);
        file.i(loan.historyCount);
        for (int h = 0; h < loan.historyCount; h++) {
            file.i(loan.historyTypes[h]);
            file.i(loan.historyDays[h]);
            file.d(loan.historyAmounts[h]);
            file.d(loan.historyDiscounts[h]);
            file.d(loan.historyBalances[h]);
        }
    }

    /**
     * Reads one loan from the file.
     * Pass null for target to discard the data while still advancing the file pointer.
     */
    private static void loadLoan(FileGetter file, Loan target) throws IOException {
        int    id                     = file.i();
        int    contractDay            = file.i();
        int    installmentsContracted = file.i();
        int    installmentsRemaining  = file.i();
        double originalPrincipal      = Math.max(0.0, Sanitize.money(file.d()));
        double netWorthAtContract     = Math.max(0.0, Sanitize.money(file.d()));
        double baseRate               = Sanitize.rate(file.d(), BankConstants.MAX_BASE_LOAN_RATE);
        double finalRate              = Sanitize.rate(file.d(), BankConstants.MAX_CONTRACT_LOAN_RATE);
        double periodRate             = CLAMP.d(Sanitize.money(file.d()), 0.0, 1.0);
        double contractedInstallment  = Math.max(0.0, Sanitize.money(file.d()));
        double currentInstallment     = Math.max(0.0, Sanitize.money(file.d()));
        double penaltyRate            = Sanitize.rate(file.d(), BankConstants.MAX_DAILY_PENALTY_RATE);
        double principalRemaining     = Math.max(0.0, Sanitize.money(file.d()));
        double debtRemaining          = Math.max(0.0, Sanitize.money(file.d()));
        int    history                = file.i();
        if (history < 0 || history > BankConstants.MAX_SAVED_LOAN_HISTORY)
            throw new IOException("invalid saved loan history count: " + history);

        if (target != null) {
            target.id                     = Math.max(1, id);
            target.contractDay            = contractDay;
            target.installmentsContracted = CLAMP.i(installmentsContracted, 1, 365);
            target.installmentsRemaining  = CLAMP.i(installmentsRemaining, 0, target.installmentsContracted);
            target.originalPrincipal      = originalPrincipal;
            target.netWorthAtContract     = netWorthAtContract;
            target.baseRate               = baseRate;
            target.finalRate              = finalRate;
            target.periodRate             = periodRate;
            target.contractedInstallment  = contractedInstallment;
            target.currentInstallment     = currentInstallment;
            target.penaltyRate            = penaltyRate;
            target.principalRemaining     = principalRemaining;
            target.debtRemaining          = debtRemaining;
            target.historyCount           = 0;
        }

        // History entries are always read even when target is null, to keep the file pointer correct.
        for (int h = 0; h < history; h++) {
            int    type     = file.i();
            int    histDay  = file.i();
            double amount   = Sanitize.money(file.d());
            double discount = Math.max(0.0, Sanitize.money(file.d()));
            double balance  = Math.max(0.0, Sanitize.money(file.d()));
            if (target != null && Loan.historyTypeValid(type) && target.historyCount < Loan.MAX_HISTORY) {
                int idx = target.historyCount++;
                target.historyTypes[idx]     = type;
                target.historyDays[idx]      = histDay;
                target.historyAmounts[idx]   = amount;
                target.historyDiscounts[idx] = discount;
                target.historyBalances[idx]  = balance;
            }
        }
    }
}
