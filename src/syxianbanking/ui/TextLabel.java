package syxianbanking.ui;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.renderable.RENDEROBJ;
import util.gui.misc.GText;

/**
 * Left-aligned sub-title label used as a chart section heading.
 * Rendered with the lablifySub style for visual consistency across all panels.
 */
public final class TextLabel extends RENDEROBJ.RenderImp {

    private final CharSequence text;
    private final GText label = new GText(UI.FONT().S, 120).lablifySub();

    public TextLabel(CharSequence text, int w) {
        super(w, 20);
        this.text = text;
    }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        label.clear().add(text);
        label.render(r, body().x1(), body().y1());
    }
}
