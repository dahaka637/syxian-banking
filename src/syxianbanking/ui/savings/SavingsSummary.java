package syxianbanking.ui.savings;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import syxianbanking.ui.UiUtils;
import util.gui.misc.GText;

/**
 * Summary card for the savings account: current annual rate, balance and interest mode.
 * Displays a red error line if the economic calculation failed (e.g. no active NPC kingdoms).
 */
final class SavingsSummary extends RENDEROBJ.RenderImp {

    private final GText header = new GText(UI.FONT().H2, 80).lablify();
    private final GText big    = new GText(UI.FONT().M, 120);
    private final GText small  = new GText(UI.FONT().S, 160);

    SavingsSummary(int w, int h) { super(w, h); }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        UiUtils.card(r, body().x1(), body().x2(), body().y1(), body().y2());
        BankState bank = BankState.INSTANCE;
        int x = body().x1() + 16;
        int y = body().y1() + 12;

        header.clear().add(TR.s("savings.header"));
        header.render(r, x, y);
        y += 34;

        big.clear().lablify().add(TR.s("savings.annualRate")).add(' ');
        UiUtils.appendPercent(big, bank.calculator.currentSavingsRate);
        big.render(r, x, y);
        y += 46;

        big.clear().lablify().add(TR.s("savings.currentBalance")).add(' ');
        UiUtils.appendMoney(big, bank.savings.balance, false);
        big.render(r, x, y);
        y += 34;

        small.clear().normalify2();
        if (bank.error != null) {
            small.color(COLOR.RED100).add(bank.error);
        } else if (bank.savings.reinvest) {
            small.add(TR.s("savings.interestReinvested"));
        } else {
            small.add(TR.s("savings.interestTreasury"));
        }
        small.render(r, x, y);
    }
}
