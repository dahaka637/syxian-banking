package syxianbanking.ui.loans;

import init.sprite.UI.UI;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import syxianbanking.ui.StaticLabel;
import util.data.INT.IntImp;
import util.gui.misc.GButt;
import util.gui.misc.GInputInt;
import util.gui.misc.GText;
import view.main.VIEW;

/**
 * Popup for early (partial or full) repayment of the selected loan.
 *
 * LoanPrepayInfo shows a live preview of the discount and remaining balance
 * for the amount entered. The repayment logic is handled by LoanManager.prepayLoan().
 */
final class LoanPrepayPopup extends GuiSection {

    private static final int POPUP_W = 460;

    private final int    loanIdx;
    private final int    maxAmount;
    private final IntImp amount;

    LoanPrepayPopup(int loanIdx) {
        this.loanIdx   = loanIdx;
        this.maxAmount = BankState.INSTANCE.loans.availableEarlyPayment(loanIdx, BankState.INSTANCE.savings);
        this.amount    = new IntImp(0, 0, Math.max(0, maxAmount));

        GText title = new GText(UI.FONT().H2, 120).lablify();
        title.add(TR.s("popup.prepay"));
        title.adjustWidth();
        add(new RENDEROBJ.RenderDummy(POPUP_W, 1), 0, 0);
        add(title, (POPUP_W - title.width()) / 2, 0);
        add(new LoanPrepayInfo(400, 128, this), (POPUP_W - 400) / 2, 42);

        GuiSection row = new GuiSection();
        row.add(new StaticLabel(TR.s("label.value"), 90, 32), 0, 0);
        GInputInt input = new GInputInt(amount, true, true);
        row.add(input, 100, 0);
        GButt.ButtPanel maxButton = new GButt.ButtPanel(TR.s("button.max")) {
            @Override protected void clickA() { amount.set(maxAmount); }
        }.setDim(84, 32);
        row.add(maxButton, input.body().x2() + 12, 0);
        add(row, (POPUP_W - row.body().width()) / 2, 188);

        GuiSection buttons = new GuiSection();
        GButt.ButtPanel ok = new GButt.ButtPanel(UI.icons().m.ok) {
            @Override
            protected void clickA() {
                if (amount.get() > 0 && amount.get() <= maxAmount) {
                    BankState.INSTANCE.loans.prepayLoan(loanIdx, amount.get(), BankState.INSTANCE.savings);
                    VIEW.inters().popup.close();
                }
            }
            @Override
            protected void renAction() { activeSet(amount.get() > 0 && amount.get() <= maxAmount); }
        };
        ok.hoverTitleSet(TR.s("hover.confirm"));
        GButt.ButtPanel cancel = new GButt.ButtPanel(UI.icons().m.cancel) {
            @Override protected void clickA() { VIEW.inters().popup.close(); }
        };
        cancel.hoverTitleSet(TR.s("hover.cancel"));
        buttons.add(ok);
        buttons.addRightC(0, cancel);
        add(buttons, (POPUP_W - buttons.body().width()) / 2, 244);
        pad(16, 16);
    }

    int getLoanIdx()   { return loanIdx; }
    int getAmount()    { return amount.get(); }
    int getMaxAmount() { return maxAmount; }
}
