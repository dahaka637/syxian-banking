package syxianbanking.ui.savings;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import util.gui.misc.GButt;
import util.gui.misc.GText;

/**
 * Checkbox that controls whether savings interest is reinvested or paid to the treasury.
 *
 * Checked   — interest is credited back into the bank balance (compound interest).
 * Unchecked — interest is transferred to the player faction treasury (simple interest).
 * State is persisted in SavingsAccount.reinvest.
 */
final class ReinvestToggle extends GuiSection {

    private final GText label = new GText(UI.FONT().S, 180);

    ReinvestToggle(int w, int h) {
        add(new RENDEROBJ.RenderDummy(w, h), 0, 0);
        GButt.Checkbox check = new GButt.Checkbox() {
            @Override protected void clickA()    { BankState.INSTANCE.savings.reinvest = !BankState.INSTANCE.savings.reinvest; }
            @Override protected void renAction() { selectedSet(BankState.INSTANCE.savings.reinvest); }
        };
        check.hoverTitleSet(TR.s("hover.reinvestTitle"));
        check.hoverInfoSet(TR.s("hover.reinvestInfo"));
        add(check, 0, (h - check.body().height()) / 2);
    }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        super.render(r, ds);
        label.clear().normalify2().add(TR.s("label.reinvest"));
        label.render(r, body().x1() + 30, body().y1() + 5);
    }
}
