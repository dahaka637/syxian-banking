package syxianbanking.ui.savings;

import snake2d.util.color.COLOR;
import snake2d.util.gui.clickable.CLICKABLE;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import syxianbanking.ui.BankPanel;
import util.gui.misc.GButt;
import view.main.VIEW;

/**
 * Main panel for the Savings tab.
 *
 * Layout top to bottom:
 *   SavingsSummary      — current annual rate and balance.
 *   Deposit / Withdraw  — buttons that open SavingsTransferPopup.
 *   ReinvestToggle      — checkbox for compound vs simple interest.
 *   OperationHistoryFrame — scrollable log of recent operations.
 *   Two charts          — savings rate history and balance history.
 */
public final class SavingsPanel extends BankPanel {

    public SavingsPanel() {
        int x      = CONTENT_X;
        int chartY = PANEL_H - CHART_ROW_H; // charts sit at the very bottom of the panel

        add(new SavingsSummary(CONTENT_W, 170), x, 0);

        GButt.ButtPanel deposit = new GButt.ButtPanel(TR.s("button.deposit")) {
            @Override protected void clickA() { showSavingsPopup(true, this); }
        }.setDim(200, 38);
        deposit.hoverTitleSet(TR.s("hover.depositMoney"));

        GButt.ButtPanel withdraw = new GButt.ButtPanel(TR.s("button.withdraw")) {
            @Override protected void clickA() { showSavingsPopup(false, this); }
        }.setDim(200, 38);
        withdraw.hoverTitleSet(TR.s("hover.withdrawMoney"));

        add(deposit,  x + 210, 202);
        add(withdraw, x + 430, 202);
        add(new ReinvestToggle(420, 28), x + 210, 246);
        add(new OperationHistoryFrame(CONTENT_W, chartY - 314), x, 296);

        addChartBlock(TR.s("data.savingsRateHistory"),
                BankState.INSTANCE.savings.rateHistory,
                COLOR.YELLOW100, TR.s("chart.savingsRate"), true, x, chartY);
        addChartBlock(TR.s("data.bankBalanceHistory"),
                BankState.INSTANCE.savings.balanceHistory,
                COLOR.GREEN100, TR.s("chart.bankBalance"), false, x + CHART_W + CHART_GAP, chartY);
    }

    // Refreshes state before opening the popup so limits reflect the current day.
    private static void showSavingsPopup(boolean deposit, CLICKABLE trigger) {
        BankState.INSTANCE.updateIfNeeded();
        VIEW.inters().popup.show(new SavingsTransferPopup(deposit), trigger);
    }
}
