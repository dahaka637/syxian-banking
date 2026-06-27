package syxianbanking.ui.loans;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import syxianbanking.ui.UiUtils;
import util.gui.misc.GText;

/**
 * Summary card for the loan market: current base rate, daily late penalty,
 * available credit and maximum term. Shows a red error if the economic calculation failed.
 */
final class LoanSummary extends RENDEROBJ.RenderImp {

    private final GText header = new GText(UI.FONT().H2, 80).lablify();
    private final GText big    = new GText(UI.FONT().M, 140);
    private final GText small  = new GText(UI.FONT().S, 180);

    LoanSummary(int w, int h) { super(w, h); }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        UiUtils.card(r, body().x1(), body().x2(), body().y1(), body().y2());
        BankState bank = BankState.INSTANCE;
        int x = body().x1() + 16;
        int y = body().y1() + 12;

        header.clear().add(TR.s("loans.header"));
        header.render(r, x, y);
        y += 34;

        big.clear().lablify().add(TR.s("loans.annualRate")).add(' ');
        UiUtils.appendPercent(big, bank.calculator.currentLoanRate);
        big.render(r, x, y);
        y += 30;

        if (bank.error != null) {
            small.clear().normalify2().color(COLOR.RED100).add(bank.error);
            small.render(r, x, y);
            return;
        }

        small.clear().normalify2().add(TR.s("loans.latePenalty")).add(' ');
        UiUtils.appendPercent(small, bank.loans.latePenaltyRate);
        small.render(r, x, y);
        y += 26;

        small.clear().normalify2().add(TR.s("label.maxAvailable")).add(' ');
        UiUtils.appendMoney(small, bank.loans.availableLoanAmount(), false);
        small.render(r, x, y);
        y += 24;

        small.clear().normalify2().add(TR.s("loans.maxInstallments")).add(' ')
                .add(bank.loans.maxInstallmentsAllowed());
        small.render(r, x, y);
    }
}
