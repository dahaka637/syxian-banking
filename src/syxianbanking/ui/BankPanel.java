package syxianbanking.ui;

import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.banking.BankState;
import view.ui.manage.IFullView;

/**
 * Abstract base for the Savings and Loans panels.
 *
 * Responsibilities:
 *   - Defines the shared layout constants (panel size, content margins, chart dimensions).
 *   - Calls BankState.updateIfNeeded() on every render frame so subcomponents
 *     always read current values without having to call it individually.
 *   - Provides addChartBlock() to insert a label + history chart pair consistently.
 */
public abstract class BankPanel extends GuiSection {

    public static final int PANEL_W           = 920;
    public static final int PANEL_H           = IFullView.HEIGHT - 72;
    public static final int CONTENT_X         = 40;
    public static final int CONTENT_W         = 840;
    public static final int CHART_GAP         = 18;  // gap between the two side-by-side charts
    public static final int CHART_W           = (CONTENT_W - CHART_GAP) / 2;
    public static final int CHART_H           = 82;
    public static final int CHART_LABEL_OFFSET = 22; // height of the label above the chart
    public static final int CHART_ROW_H       = CHART_LABEL_OFFSET + CHART_H;

    protected BankPanel() {
        // RenderDummy reserves the panel's space so the outer layout sizes correctly.
        add(new RENDEROBJ.RenderDummy(PANEL_W, PANEL_H), 0, 0);
    }

    // Updates bank state before rendering children so every subcomponent sees the current day's values.
    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        BankState.INSTANCE.updateIfNeeded();
        super.render(r, ds);
    }

    /**
     * Adds a label + history chart block at position (x, y).
     * The label sits above the chart by CHART_LABEL_OFFSET pixels.
     * percent: true = tooltip formats values as %, false = as money.
     */
    protected void addChartBlock(CharSequence label, double[] values, COLOR color,
            CharSequence title, boolean percent, int x, int y) {
        add(new TextLabel(label, CHART_W), x, y);
        add(new HistoryChart(CHART_W, CHART_H, values, color, title, percent, false),
                x, y + CHART_LABEL_OFFSET);
    }
}
