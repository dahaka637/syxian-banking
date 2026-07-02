package syxianbanking.ui;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import util.gui.misc.GText;
import view.ui.manage.IFullView;

/**
 * Shown when the player opens the bank before researching the banking unlock.
 */
public final class BankLockedView extends IFullView {

    public BankLockedView() {
        super("Syxian Banking", BankingView.bankIcon());
        section.body().setWidth(WIDTH).setHeight(1);
        section.add(new LockedMessage(), 0, 0);
    }

    private static final class LockedMessage extends RENDEROBJ.RenderImp {
        private final GText title = new GText(UI.FONT().H2, 320).lablify();
        private final GText body  = new GText(UI.FONT().M, 640).normalify2();

        LockedMessage() {
            super(WIDTH, HEIGHT);
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            int centerX = body().cX();
            int y = body().y1() + 210;

            title.clear().lablify().add(TR.s("bank.locked.title"));
            title.adjustWidth();
            title.render(r, centerX - title.width() / 2, y);

            body.clear().normalify2().add(TR.s("bank.locked.body"));
            body.adjustWidth();
            body.render(r, centerX - body.width() / 2, y + 44);
        }
    }
}
