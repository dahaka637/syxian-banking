package syxianbanking.ui.loans;

import game.time.TIME;
import snake2d.util.color.COLOR;
import snake2d.util.color.ColorImp;
import syxianbanking.TR;
import syxianbanking.banking.BankConstants;
import syxianbanking.banking.BankState;
import syxianbanking.domain.Loan;
import syxianbanking.ui.UiUtils;
import util.colors.GCOLOR;
import util.gui.misc.GBox;
import util.gui.misc.GText;
import util.gui.table.GStaples;
import util.text.DicTime;

/**
 * Debt-balance chart for the currently selected loan contract.
 *
 * Each loan keeps its own daily history, so changing the selected row in LoanListFrame
 * immediately changes the chart without mixing data from other contracts.
 */
final class LoanDebtChart extends GStaples {

    private final COLOR color;
    private final CharSequence title;

    LoanDebtChart(int w, int h, COLOR color, CharSequence title) {
        super(BankConstants.LOAN_DEBT_HISTORY_DAYS, false);
        this.color = color;
        this.title = title;
        body().setDim(w, h);
        normalizePlus(true);
    }

    @Override
    protected double getValue(int stapleI) {
        Loan loan = selectedLoan();
        return hasData(loan, stapleI) ? loan.debtHistoryValues[stapleI] : 0;
    }

    @Override
    protected void setColor(ColorImp c, int stapleI, double value) {
        c.set(hasData(selectedLoan(), stapleI) ? color : GCOLOR.UI().bg());
    }

    @Override
    protected void setColorBg(ColorImp c, int stapleI, double value) {
        c.set(GCOLOR.UI().bg());
    }

    @Override
    protected void hover(GBox box, int stapleI) {
        box.title(title);
        Loan loan = selectedLoan();
        GText t = box.text();
        if (loan == null) {
            t.add(TR.s("loanDetails.selectLoan"));
            box.add(t);
            return;
        }
        if (!hasData(loan, stapleI)) {
            t.add(TR.s("chart.noData"));
            box.add(t);
            return;
        }

        int back = Math.max(0, TIME.days().bitsSinceStart() - loan.debtHistoryDays[stapleI]);
        t.lablify();
        DicTime.setAgo(t, back * TIME.secondsPerDay());
        box.add(t);
        box.NL(4);

        t = box.text();
        UiUtils.appendMoney(t, loan.debtHistoryValues[stapleI], true);
        box.add(t);
    }

    private Loan selectedLoan() {
        BankState bank = BankState.INSTANCE;
        int loanIdx = bank.loans.selectedLoan;
        return bank.loans.loanValid(loanIdx) ? bank.loans.loans[loanIdx] : null;
    }

    private boolean hasData(Loan loan, int stapleI) {
        if (loan == null || loan.debtHistoryCount <= 0) return false;
        int firstValid = loan.debtHistoryValues.length - loan.debtHistoryCount;
        return stapleI >= firstValid;
    }
}
