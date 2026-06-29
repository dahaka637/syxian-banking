package syxianbanking.banking;

/**
 * Central repository for all numeric constants used across the mod.
 *
 * Three categories:
 *   - Save markers: binary identifiers for the save format versions.
 *     Never change their hex values — doing so breaks existing campaign saves.
 *   - Economic formulas: weights and base values that drive interest rate calculations.
 *     Adjust these to balance the mod without touching any other file.
 *   - Guardrails: upper bounds against NaN, Infinity and save corruption.
 *     Not gameplay caps — purely defensive limits.
 */
public final class BankConstants {
    private BankConstants() {}

    // ---- Save format markers ----
    // Append new data after SAVE_V2_END_MARK with a new V3 marker; never reorder existing fields.
    public static final int SAVE_MARK        = 0x53584235; // V1 main block
    public static final int SAVE_V2_MARK     = 0x53584236; // V2 economic snapshot + history
    public static final int SAVE_V2_END_MARK = 0x53584245; // V2 closing sentinel
    public static final int SAVE_V3_MARK     = 0x53584237; // V3 per-loan penalty cap state
    public static final int SAVE_V3_END_MARK = 0x53584246; // V3 closing sentinel
    public static final int SAVE_V4_MARK     = 0x53584238; // V4 per-loan debt history
    public static final int SAVE_V4_END_MARK = 0x53584247; // V4 closing sentinel

    // ---- Runtime capacity limits ----
    public static final int MAX_OPERATIONS          = 100; // savings operation log size
    public static final int MAX_LOANS               = 16;  // maximum simultaneous active loans
    public static final int HISTORY_DAYS            = 48;  // chart history window in days

    // Read limits while loading saves — guards against corrupted files with absurd counts.
    public static final int MAX_SAVED_LOANS         = 256;
    public static final int MAX_SAVED_LOAN_HISTORY  = 1000;
    public static final int MAX_SAVED_LOAN_DEBT_HISTORY = 1000;
    public static final int MAX_SAVED_HISTORY_LENGTH = 365;

    // ---- Rate smoothing (exponential moving average) ----
    // Lower values = rates change more slowly; higher values = faster reaction to the economy.
    public static final double SAVINGS_ADJUSTMENT_SPEED    = 0.08;
    public static final double LOAN_ADJUSTMENT_SPEED       = 0.12;

    // Fraction of remaining interest forgiven during early repayment, proportional to amount paid.
    public static final double EARLY_PAYMENT_DISCOUNT_FACTOR = 0.75;

    // ---- Market rate formula ----
    public static final double SAVINGS_BASE_RATE         = 2.0;  // annual savings rate before any adjustment (%)
    public static final double LOAN_BASE_SPREAD          = 3.0;  // minimum spread between savings and loan rate

    public static final double SAVINGS_STRESS_WEIGHT     = 0.35; // negative stress raises savings rate
    public static final double LOAN_STRESS_WEIGHT        = 0.55; // negative stress raises loan rate
    public static final double SAVINGS_DISPERSION_WEIGHT = 0.20; // economic inequality raises savings rate
    public static final double LOAN_DISPERSION_WEIGHT    = 0.35; // economic inequality raises loan rate
    public static final double SAVINGS_STRENGTH_DISCOUNT = 0.10; // positive strength lowers savings rate

    // ---- Credit capacity formula ----
    // creditRatio: fraction of the player's credit base available as new credit.
    // Savings act as collateral; active debt has a small extra burden to discourage chaining loans.
    public static final double CREDIT_RATIO_BASE               = 0.30;
    public static final double CREDIT_RATIO_STRENGTH_WEIGHT    = 0.002;
    public static final double CREDIT_RATIO_STRESS_WEIGHT      = 0.003;
    public static final double CREDIT_RATIO_DISPERSION_WEIGHT  = 0.0025;
    public static final double CREDIT_RATIO_RATE_WEIGHT        = 0.001;
    public static final double MIN_CREDIT_RATIO                = 0.05;
    public static final double MAX_CREDIT_RATIO                = 0.55;
    public static final double SAVINGS_CREDIT_COLLATERAL_WEIGHT = 1.0;
    public static final double ACTIVE_DEBT_EXTRA_BURDEN_WEIGHT  = 0.15;

    // ---- Loan term formula ----
    // The displayed maximum is rounded to an even number. MIN/MAX are hard guardrails;
    // the playable floor and ceiling are recalculated from the market every refresh.
    public static final int    MIN_LOAN_TERM                  = 2;
    public static final int    MAX_LOAN_TERM                  = 240;
    public static final double TERM_BASE_OFFSET               = 18.0;
    public static final double TERM_CREDIT_RATIO_WEIGHT       = 96.0;
    public static final double TERM_STRENGTH_WEIGHT           = 0.30;
    public static final double TERM_STRESS_WEIGHT             = 0.38;
    public static final double TERM_DISPERSION_WEIGHT         = 0.26;
    public static final double TERM_RATE_WEIGHT               = 0.32;
    public static final double TERM_LEVERAGE_WEIGHT           = 30.0; // high leverage reduces available term
    public static final double TERM_FLOOR_BASE                = 2.0;
    public static final double TERM_FLOOR_CREDIT_RATIO_WEIGHT = 18.0;
    public static final double TERM_FLOOR_STRENGTH_WEIGHT     = 0.04;
    public static final double TERM_FLOOR_STRESS_WEIGHT       = 0.05;
    public static final double TERM_FLOOR_RATE_WEIGHT         = 0.03;
    public static final double TERM_FLOOR_LEVERAGE_WEIGHT     = 8.0;
    public static final int    TERM_FLOOR_MAX                 = 24;
    public static final double TERM_CEILING_BASE              = 30.0;
    public static final double TERM_CEILING_CREDIT_RATIO_WEIGHT = 132.0;
    public static final double TERM_CEILING_STRENGTH_WEIGHT   = 0.20;
    public static final double TERM_CEILING_STRESS_WEIGHT     = 0.18;
    public static final double TERM_CEILING_DISPERSION_WEIGHT = 0.10;
    public static final double TERM_CEILING_RATE_WEIGHT       = 0.12;
    public static final double TERM_CEILING_LEVERAGE_WEIGHT   = 8.0;
    public static final int    TERM_CEILING_MIN               = 12;
    public static final double TERM_CREDIT_PER_EXTRA_INSTALLMENT = 100000.0;
    public static final int    LOAN_DEBT_HISTORY_DAYS         = 120;

    // ---- Loan pricing premiums ----
    public static final double LOAN_VALUE_PREMIUM_WEIGHT           = 5.0;
    public static final double LOAN_TERM_PREMIUM_WEIGHT            = 2.0;
    public static final double TERM_PREMIUM_REFERENCE_INSTALLMENTS = 12.0; // reference scale for term premium
    public static final double MAX_RATE_LEVERAGE                   = 5.0;

    // ---- Late penalty formula ----
    public static final double LATE_PENALTY_BASE              = 0.5;  // daily penalty base (%)
    public static final double LATE_PENALTY_RATE_WEIGHT       = 0.03;
    public static final double LATE_PENALTY_STRESS_WEIGHT     = 0.05;
    public static final double LATE_PENALTY_DISPERSION_WEIGHT = 0.03;
    public static final double PENALTY_CAP_DEBT_MULTIPLIER    = 5.0;  // max debt before penalties stop

    // ---- Settlement thresholds ----
    // Debts below SETTLED_DEBT_EPSILON are considered fully paid (prevents infinite penny rounding).
    public static final double SETTLED_DEBT_EPSILON = 0.5;
    // Tolerance to consider an installment fully paid despite floating-point error.
    public static final double PAYMENT_EPSILON      = 0.0001;

    // ---- Guardrails against NaN / Infinity / save corruption ----
    // These are NOT gameplay caps — purely defensive ceilings for invalid math results.
    public static final double MAX_MONEY              = Integer.MAX_VALUE * 64.0;
    public static final double MAX_CREDIT_SIGNAL      = 150.0;
    public static final double MAX_SAVINGS_RATE       = 45.0;
    public static final double MAX_BASE_LOAN_RATE     = 120.0;
    public static final double MAX_CONTRACT_LOAN_RATE = 180.0;
    public static final double MAX_DAILY_PENALTY_RATE = 10.0;
    public static final double MAX_ECONOMIC_SIGNAL    = 1000.0;

    // ---- Display rounding ----
    // Loan offers are rounded down to clean numbers while keeping two meaningful digits.
    public static final int LOAN_OFFER_MIN_ROUNDING_STEP = 10;

    // ---- Savings operation type codes ----
    public static final int OP_DEPOSIT  = 0;
    public static final int OP_WITHDRAW = 1;
    public static final int OP_INTEREST = 2;
}
