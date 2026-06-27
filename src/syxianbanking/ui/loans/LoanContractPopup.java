package syxianbanking.ui.loans;

import init.sprite.UI.UI;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.banking.BankConstants;
import syxianbanking.banking.BankState;
import syxianbanking.ui.StaticLabel;
import util.data.INT.IntImp;
import util.gui.misc.GButt;
import util.gui.misc.GInputInt;
import util.gui.misc.GText;
import view.main.VIEW;

/**
 * Popup for taking out a new loan.
 *
 * The player chooses the principal amount and number of installments.
 * LoanContractInfo shows a live preview of the resulting rate, installment and totals.
 *
 * Available limits (maxAmount, maxInstallments) are captured when the popup opens
 * so they stay consistent even if the market changes while the popup is open.
 */
final class LoanContractPopup extends GuiSection {

    private static final int POPUP_W = 560;

    private final int    maxAmount;
    private final int    maxInstallments;
    private final IntImp amount;
    private final IntImp installments;

    LoanContractPopup() {
        BankState.INSTANCE.updateIfNeeded();
        maxAmount       = BankState.INSTANCE.loans.availableLoanAmount();
        maxInstallments = BankState.INSTANCE.loans.maxInstallmentsAllowed();
        amount          = new IntImp(0, 0, Math.max(0, maxAmount));
        installments    = new IntImp(Math.min(12, maxInstallments), 1, Math.max(1, maxInstallments));

        GText title = new GText(UI.FONT().H2, 120).lablify();
        title.add(TR.s("popup.contractLoan"));
        title.adjustWidth();
        add(new RENDEROBJ.RenderDummy(POPUP_W, 1), 0, 0);
        add(title, (POPUP_W - title.width()) / 2, 0);
        add(new LoanContractInfo(500, 154, this), (POPUP_W - 500) / 2, 42);

        GuiSection amountRow      = inputRow(TR.s("label.value"),       amount,       maxAmount);
        GuiSection installmentRow = inputRow(TR.s("label.installments"), installments, maxInstallments);
        add(amountRow,      (POPUP_W - amountRow.body().width())      / 2, 214);
        add(installmentRow, (POPUP_W - installmentRow.body().width()) / 2, 264);

        GuiSection buttons = new GuiSection();
        GButt.ButtPanel ok = new GButt.ButtPanel(UI.icons().m.ok) {
            @Override
            protected void clickA() {
                if (amount.get() > 0 && installments.get() > 0
                        && BankState.INSTANCE.loans.loanCount < BankConstants.MAX_LOANS) {
                    BankState.INSTANCE.loans.contractLoan(amount.get(), installments.get(), BankState.INSTANCE.savings);
                    VIEW.inters().popup.close();
                }
            }
            @Override
            protected void renAction() {
                activeSet(amount.get() > 0 && amount.get() <= maxAmount
                        && installments.get() > 0 && installments.get() <= maxInstallments
                        && BankState.INSTANCE.loans.loanCount < BankConstants.MAX_LOANS);
            }
        };
        ok.hoverTitleSet(TR.s("hover.confirm"));
        GButt.ButtPanel cancel = new GButt.ButtPanel(UI.icons().m.cancel) {
            @Override protected void clickA() { VIEW.inters().popup.close(); }
        };
        cancel.hoverTitleSet(TR.s("hover.cancel"));
        buttons.add(ok);
        buttons.addRightC(0, cancel);
        add(buttons, (POPUP_W - buttons.body().width()) / 2, 324);
        pad(16, 16);
    }

    int getAmount()          { return amount.get(); }
    int getInstallments()    { return installments.get(); }
    int getMaxAmount()       { return maxAmount; }
    int getMaxInstallments() { return maxInstallments; }

    // Builds a row: [right-aligned label] [numeric input] [MAX button]
    private GuiSection inputRow(CharSequence label, IntImp target, int max) {
        GuiSection row = new GuiSection();
        row.add(new StaticLabel(label, 120, 32), 0, 0);
        GInputInt input = new GInputInt(target, true, true);
        row.add(input, 130, 0);
        GButt.ButtPanel maxButton = new GButt.ButtPanel(TR.s("button.max")) {
            @Override protected void clickA() { target.set(max); }
        }.setDim(84, 32);
        maxButton.hoverTitleSet(TR.s("hover.useMax"));
        row.add(maxButton, input.body().x2() + 12, 0);
        return row;
    }
}
