package syxianbanking.ui;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.renderable.RENDEROBJ;
import util.gui.misc.GText;

/**
 * Right-aligned static label used as a field name beside numeric inputs in popups.
 * Right-alignment keeps all input fields vertically lined up regardless of label length.
 */
public final class StaticLabel extends RENDEROBJ.RenderImp {

    private final CharSequence label;
    private final GText text = new GText(UI.FONT().S, 100).normalify2();

    public StaticLabel(CharSequence label, int w, int h) {
        super(w, h);
        this.label = label;
    }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        text.clear().normalify2().add(label);
        text.adjustWidth();
        text.render(r,
                body().x2() - text.width(),
                body().y1() + (body().height() - text.height()) / 2);
    }
}
