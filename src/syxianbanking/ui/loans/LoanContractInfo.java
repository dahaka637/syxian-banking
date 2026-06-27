package syxianbanking.ui.loans;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import syxianbanking.ui.UiUtils;
import util.gui.misc.GText;

/**
 * Live preview inside LoanContractPopup.
 *
 * Recalculated every render frame to reflect the player's current amount/installment inputs.
 * Shows: final rate with premiums, estimated daily installment, total due and total interest.
 * All values are read-only previews — no bank state is modified.
 */
final class LoanContractInfo extends RENDEROBJ.RenderImp {

    private final LoanContractPopup popup;
    private final GText text = new GText(UI.FONT().S, 220);

    LoanContractInfo(int w, int h, LoanContractPopup popup) {
        super(w, h);
        this.popup = popup;
    }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        BankState bank       = BankState.INSTANCE;
        int    amount        = popup.getAmount();
        int    installments  = popup.getInstallments();
        double finalRate     = bank.loans.previewLoanFinalRate(amount, installments);
        double installment   = bank.loans.previewLoanInstallment(amount, installments);
        double totalDue      = bank.loans.previewLoanTotalDue(amount, installments);
        double totalInterest = bank.loans.previewLoanTotalInterest(amount, installments);
        int cx = body().cX();
        int y  = body().y1();

        text.clear().normalify2().add(TR.s("label.maxAvailable")).add(' ');
        UiUtils.appendMoney(text, popup.getMaxAmount(), false);
        UiUtils.centerText(r, text, cx, y);
        y += 22;

        text.clear().normalify2().add(TR.s("label.maxDailyInstallments")).add(' ').add(popup.getMaxInstallments());
        UiUtils.centerText(r, text, cx, y);
        y += 26;

        text.clear().lablifySub().add(TR.s("label.contractPreview"));
        UiUtils.centerText(r, text, cx, y);
        y += 24;

        text.clear().normalify2().add(TR.s("label.estimatedTotalRate")).add(' ');
        UiUtils.appendPercent(text, finalRate);
        UiUtils.centerText(r, text, cx, y);
        y += 22;

        text.clear().normalify2().add(TR.s("label.estimatedDailyInstallment")).add(' ');
        UiUtils.appendMoney(text, installment, false);
        UiUtils.centerText(r, text, cx, y);
        y += 22;

        text.clear().normalify2().add(TR.s("label.totalDue")).add(' ');
        UiUtils.appendMoney(text, totalDue, false);
        UiUtils.centerText(r, text, cx, y);
        y += 22;

        text.clear().normalify2().add(TR.s("label.estimatedTotalInterest")).add(' ');
        UiUtils.appendMoney(text, totalInterest, false);
        UiUtils.centerText(r, text, cx, y);
    }
}
