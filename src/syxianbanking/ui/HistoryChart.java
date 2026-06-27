package syxianbanking.ui;

import game.time.TIME;
import snake2d.util.color.COLOR;
import snake2d.util.color.ColorImp;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import util.colors.GCOLOR;
import util.gui.misc.GBox;
import util.gui.misc.GText;
import util.gui.table.GStaples;
import util.text.DicTime;

/**
 * Bar chart for displaying a rolling history of a rate or balance metric.
 *
 * Each bar corresponds to one game day. Index 0 in the values array is the oldest entry,
 * the last index is the most recent. Days without data yet (before historySamples reaches
 * the array size) are rendered in the background colour so the chart appears to grow
 * from right to left as samples accumulate.
 *
 * The mouse-over tooltip shows the value and how many days ago that bar represents.
 */
public final class HistoryChart extends GStaples {

    private final double[]     values;
    private final COLOR        color;
    private final CharSequence title;
    private final boolean      percent; // true = format tooltip value as %; false = as money

    public HistoryChart(int w, int h, double[] values, COLOR color,
            CharSequence title, boolean percent, boolean negative) {
        super(values.length, negative);
        this.values  = values;
        this.color   = color;
        this.title   = title;
        this.percent = percent;
        body().setDim(w, h);
        normalizePlus(true);
    }

    @Override
    protected double getValue(int stapleI) {
        return hasData(stapleI) ? values[stapleI] : 0;
    }

    @Override
    protected void setColor(ColorImp c, int stapleI, double value) {
        // Bars without data blend into the background to make them visually absent.
        c.set(hasData(stapleI) ? color : GCOLOR.UI().bg());
    }

    @Override
    protected void setColorBg(ColorImp c, int stapleI, double value) {
        c.set(GCOLOR.UI().bg());
    }

    @Override
    protected void hover(GBox box, int stapleI) {
        box.title(title);
        GText t = box.text();
        if (!hasData(stapleI)) {
            t.add(TR.s("chart.noData"));
            box.add(t);
            return;
        }
        // Index 0 is the oldest — stapleI 0 is the most days in the past.
        int back = values.length - stapleI - 1;
        t.lablify();
        DicTime.setAgo(t, back * TIME.secondsPerDay());
        box.add(t);
        box.NL(4);
        t = box.text();
        if (percent) UiUtils.appendPercent(t, values[stapleI]);
        else         UiUtils.appendMoney(t, values[stapleI], true);
        box.add(t);
    }

    // A staple has valid data only if enough samples have been collected to reach that index.
    // Valid samples are always the most recent ones (at the end of the array).
    private boolean hasData(int stapleI) {
        int samples    = BankState.INSTANCE.historySamples;
        int firstValid = values.length - samples;
        return samples > 0 && stapleI >= firstValid;
    }
}
