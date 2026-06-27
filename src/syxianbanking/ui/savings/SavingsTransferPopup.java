package syxianbanking.ui.savings;

import init.sprite.UI.UI;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import util.data.INT.IntImp;
import util.gui.misc.GButt;
import util.gui.misc.GInputInt;
import util.gui.misc.GText;
import view.main.VIEW;

/**
 * Popup for depositing to or withdrawing from the savings account.
 *
 * The maximum amount (max) is captured when the popup opens and does not change
 * while it is open. This prevents the player from confirming a value that was
 * valid at open time but became invalid due to a loan installment debited mid-popup.
 */
final class SavingsTransferPopup extends GuiSection {

    private static final int POPUP_W = 420;

    private final boolean deposit;
    private final IntImp  amount;
    private final int     max;

    SavingsTransferPopup(boolean deposit) {
        this.deposit = deposit;
        this.max     = deposit
                ? BankState.INSTANCE.savings.availableTreasury()
                : BankState.INSTANCE.savings.availableBalance();
        this.amount  = new IntImp(0, 0, Math.max(0, max));

        GText title = new GText(UI.FONT().H2, 80).lablify();
        title.add(deposit ? TR.s("popup.depositSavings") : TR.s("popup.withdrawSavings"));
        title.adjustWidth();
        add(new RENDEROBJ.RenderDummy(POPUP_W, 1), 0, 0);
        add(title, (POPUP_W - title.width()) / 2, 0);
        add(new TransferInfo(360, 48, this), (POPUP_W - 360) / 2, 42);

        GInputInt input = new GInputInt(amount, true, true);
        GButt.ButtPanel maxButton = new GButt.ButtPanel(TR.s("button.max")) {
            @Override protected void clickA() { amount.set(max); }
        }.setDim(92, 32);
        maxButton.hoverTitleSet(TR.s("hover.useMaxValue"));
        GuiSection inputRow = new GuiSection();
        inputRow.add(input);
        inputRow.addRightC(12, maxButton);
        add(inputRow, (POPUP_W - inputRow.body().width()) / 2, 104);

        GuiSection buttons = new GuiSection();
        GButt.ButtPanel ok = new GButt.ButtPanel(UI.icons().m.ok) {
            @Override
            protected void clickA() {
                if (amount.get() <= 0) return;
                if (deposit) BankState.INSTANCE.savings.deposit(amount.get());
                else         BankState.INSTANCE.savings.withdraw(amount.get());
                VIEW.inters().popup.close();
            }
            @Override
            protected void renAction() { activeSet(amount.get() > 0 && amount.get() <= max); }
        };
        ok.hoverTitleSet(TR.s("hover.confirm"));
        GButt.ButtPanel cancel = new GButt.ButtPanel(UI.icons().m.cancel) {
            @Override protected void clickA() { VIEW.inters().popup.close(); }
        };
        cancel.hoverTitleSet(TR.s("hover.cancel"));
        buttons.add(ok);
        buttons.addRightC(0, cancel);
        add(buttons, (POPUP_W - buttons.body().width()) / 2, 152);
        pad(16, 16);
    }

    boolean isDeposit() { return deposit; }
    int     getMax()    { return max; }
}
