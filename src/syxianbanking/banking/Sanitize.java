package syxianbanking.banking;

import snake2d.util.misc.CLAMP;

/**
 * Package-private sanitization helpers for financial and economic values.
 *
 * Centralises defense against NaN, Infinity and overflow that can arise from:
 *   - Corrupted data in old save files.
 *   - Division by zero in economic calculations when no NPC kingdoms are active.
 *   - Exponential debt growth from cascading late penalties.
 *
 * All limit constants live in BankConstants. This class is intentionally
 * package-private — only the banking package uses it directly.
 */
final class Sanitize {
    private Sanitize() {}

    /**
     * Ensures a monetary value is finite and within acceptable bounds.
     * Returns 0 for NaN/Infinity; applies a symmetric clamp at MAX_MONEY.
     */
    static double money(double value) {
        if (!Double.isFinite(value)) return 0;
        if (value >  BankConstants.MAX_MONEY) return  BankConstants.MAX_MONEY;
        if (value < -BankConstants.MAX_MONEY) return -BankConstants.MAX_MONEY;
        return value;
    }

    /**
     * Ensures a percentage rate is finite and in [0, max].
     * Rates are never negative in this mod.
     */
    static double rate(double value, double max) {
        if (!Double.isFinite(value)) return 0;
        return CLAMP.d(value, 0.0, max);
    }

    /**
     * Ensures an economic signal (NPC credit score, stress, dispersion) is finite
     * and within the symmetric range [-MAX_ECONOMIC_SIGNAL, MAX_ECONOMIC_SIGNAL].
     */
    static double economicSignal(double value) {
        if (!Double.isFinite(value)) return 0;
        return CLAMP.d(value, -BankConstants.MAX_ECONOMIC_SIGNAL, BankConstants.MAX_ECONOMIC_SIGNAL);
    }

    /**
     * Sanitizes a chart history value. History only accepts positive values
     * within the expected range for that metric.
     */
    static double historyValue(double value, double max) {
        if (!Double.isFinite(value)) return 0;
        return CLAMP.d(value, 0.0, max);
    }

    /**
     * Converts a double to an int by flooring, returning 0 for negative values.
     * Used for amounts available in the player treasury.
     */
    static int positiveFloor(double value) {
        value = money(value);
        if (value <= 0) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(value);
    }

    /**
     * Converts a double to an int by ceiling, returning 0 for negative values.
     * Used for the minimum amount required in payment operations.
     */
    static int positiveCeil(double value) {
        value = money(value);
        if (value <= 0) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(value);
    }
}
