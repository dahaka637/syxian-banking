package syxianbanking.ui.savings;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.ui.UiUtils;
import util.gui.misc.GText;

/**
 * Info display inside the deposit/withdraw popup.
 * Shows the available amount for the operation (treasury or savings balance)
 * and a reminder of the "value" field to guide the player.
 */
final class TransferInfo extends RENDEROBJ.RenderImp {

    private final SavingsTransferPopup popup;
    private final GText text = new GText(UI.FONT().S, 160);

    TransferInfo(int w, int h, SavingsTransferPopup popup) {
        super(w, h);
        this.popup = popup;
    }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        text.clear().normalify2();
        // Label changes depending on transfer direction.
        text.add(popup.isDeposit() ? TR.s("label.availableTreasury") : TR.s("label.availableSavings")).add(' ');
        UiUtils.appendMoney(text, popup.getMax(), false);
        text.adjustWidth();
        text.render(r, body().cX() - text.width() / 2, body().y1());

        text.clear().normalify2().add(TR.s("label.value"));
        text.adjustWidth();
        text.render(r, body().cX() - text.width() / 2, body().y1() + 24);
    }
}
