package syxianbanking.ui.loans;

import snake2d.util.color.COLOR;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import syxianbanking.ui.BankPanel;
import util.gui.misc.GButt;
import view.main.VIEW;

/**
 * Main panel for the Loans tab.
 *
 * Layout top to bottom:
 *   LoanSummary      — base rate, late penalty, available credit and max term.
 *   LoanListFrame    — scrollable list of active loans; click to select.
 *   Contract button  — opens LoanContractPopup.
 *   Prepay button    — enabled only when a loan is selected; opens LoanPrepayPopup.
 *   LoanDetailsFrame — stats and event history for the selected loan.
 *   Two charts       — late penalty history and loan rate history.
 */
public final class LoansPanel extends BankPanel {

    public LoansPanel() {
        int x      = CONTENT_X;
        int chartY = PANEL_H - CHART_ROW_H;

        add(new LoanSummary(CONTENT_W, 150), x, 0);
        add(new LoanListFrame(CONTENT_W, 170), x, 166);

        GButt.ButtPanel loan = new GButt.ButtPanel(TR.s("button.contractLoan")) {
            @Override
            protected void clickA() {
                BankState.INSTANCE.updateIfNeeded();
                VIEW.inters().popup.show(new LoanContractPopup(), this);
            }
        }.setDim(310, 40);
        loan.hoverTitleSet(TR.s("hover.contractLoan"));
        add(loan, x + 100, 352);

        GButt.ButtPanel prepay = new GButt.ButtPanel(TR.s("button.prepay")) {
            @Override
            protected void clickA() {
                int sel = BankState.INSTANCE.loans.selectedLoan;
                if (BankState.INSTANCE.loans.loanValid(sel))
                    VIEW.inters().popup.show(new LoanPrepayPopup(sel), this);
            }
            @Override
            protected void renAction() {
                activeSet(BankState.INSTANCE.loans.loanValid(BankState.INSTANCE.loans.selectedLoan));
            }
        }.setDim(310, 38);
        prepay.hoverTitleSet(TR.s("hover.amortizeLoan"));
        add(prepay, x + 430, 353);

        add(new LoanDetailsFrame(CONTENT_W, chartY - 422), x, 404);

        addChartBlock(TR.s("data.loanPenaltyHistory"),
                BankState.INSTANCE.loans.penaltyHistory,
                COLOR.ORANGE100, TR.s("chart.loanPenalty"), true, x, chartY);
        addChartBlock(TR.s("data.loanRateHistory"),
                BankState.INSTANCE.loans.rateHistory,
                COLOR.RED100, TR.s("chart.loanRate"), true, x + CHART_W + CHART_GAP, chartY);
    }
}
