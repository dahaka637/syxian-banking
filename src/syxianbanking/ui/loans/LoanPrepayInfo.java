package syxianbanking.ui.loans;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import syxianbanking.ui.UiUtils;
import util.gui.misc.GText;

/**
 * Live preview inside LoanPrepayPopup.
 *
 * Shows the interest discount earned and the remaining balance for the amount
 * currently entered by the player. Uses read-only preview methods — no state is changed.
 */
final class LoanPrepayInfo extends RENDEROBJ.RenderImp {

    private final LoanPrepayPopup popup;
    private final GText text = new GText(UI.FONT().S, 220);

    LoanPrepayInfo(int w, int h, LoanPrepayPopup popup) {
        super(w, h);
        this.popup = popup;
    }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        BankState bank    = BankState.INSTANCE;
        int       loanIdx = popup.getLoanIdx();
        int       amount  = popup.getAmount();

        double discount  = bank.loans.previewEarlyPaymentDiscount(loanIdx, amount);
        double debtAfter = bank.loans.previewEarlyPaymentDebtAfter(loanIdx, amount);
        int cx = body().cX();
        int y  = body().y1();

        text.clear().normalify2().add(TR.s("label.availableTreasury")).add(' ');
        UiUtils.appendMoney(text, bank.savings.availableTreasury(), false);
        UiUtils.centerText(r, text, cx, y);
        y += 22;

        text.clear().normalify2().add(TR.s("label.dueInContract")).add(' ');
        UiUtils.appendMoney(text, bank.loans.loanValid(loanIdx) ? bank.loans.loans[loanIdx].debtRemaining : 0, false);
        UiUtils.centerText(r, text, cx, y);
        y += 22;

        text.clear().normalify2().add(TR.s("label.maxPrepay")).add(' ');
        UiUtils.appendMoney(text, popup.getMaxAmount(), false);
        UiUtils.centerText(r, text, cx, y);
        y += 24;

        text.clear().lablifySub().add(TR.s("label.prepayPreview"));
        UiUtils.centerText(r, text, cx, y);
        y += 22;

        text.clear().normalify2().add(TR.s("label.estimatedDiscount")).add(' ');
        UiUtils.appendMoney(text, discount, false);
        UiUtils.centerText(r, text, cx, y);
        y += 22;

        text.clear().normalify2().add(TR.s("label.debtAfterPayment")).add(' ');
        UiUtils.appendMoney(text, debtAfter, false);
        UiUtils.centerText(r, text, cx, y);
    }
}
