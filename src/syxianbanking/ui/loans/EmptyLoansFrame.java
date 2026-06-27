package syxianbanking.ui.loans;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.ui.UiUtils;
import util.gui.misc.GText;

/**
 * Placeholder card shown when there are no active loans.
 * Informs the player that a loan can be taken using the button above.
 *
 * Note: currently not wired into LoansPanel layout — LoanDetailsFrame already handles
 * the "no selection" state internally. Kept here for potential future layout changes.
 */
final class EmptyLoansFrame extends RENDEROBJ.RenderImp {

    private final GText text  = new GText(UI.FONT().H2, 120).lablifySub();
    private final GText small = new GText(UI.FONT().S,  180);

    EmptyLoansFrame(int w, int h) { super(w, h); }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        UiUtils.card(r, body().x1(), body().x2(), body().y1(), body().y2());
        int x = body().x1() + 16;
        int y = body().y1() + 36;
        text.clear().add(TR.s("loans.noneTitle"));
        text.render(r, x, y);
        y += 34;
        small.clear().normalify2().add(TR.s("loans.futureContracts"));
        small.render(r, x, y);
    }
}
