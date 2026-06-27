package syxianbanking.ui;

import game.time.TIME;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.renderable.RENDEROBJ;
import util.colors.GCOLOR;
import util.gui.misc.GText;
import util.text.DicTime;

/**
 * Static rendering helpers shared by all bank UI panels.
 *
 * Centralises repetitive UI operations so every panel produces consistent visuals:
 * card borders, money/percent formatting and text positioning.
 * All methods are stateless and operate directly on the SPRITE_RENDERER.
 */
public final class UiUtils {
    private UiUtils() {}

    /** Draws a card background (border + fill + subtle inner highlight) in the given rectangle. */
    public static void card(SPRITE_RENDERER r, int x1, int x2, int y1, int y2) {
        GCOLOR.UI().border().render(r, x1,     x2,     y1,     y2);
        GCOLOR.UI().bg()    .render(r, x1 + 1, x2 - 1, y1 + 1, y2 - 1);
        snake2d.util.color.COLOR.WHITE15.render(r, x1 + 2, x2 - 2, y1 + 2, y2 - 2);
    }

    /** Overload that accepts a RENDEROBJ body directly. */
    public static void card(SPRITE_RENDERER r, RENDEROBJ obj) {
        card(r, obj.body().x1(), obj.body().x2(), obj.body().y1(), obj.body().y2());
    }

    /** Appends a localised relative-time description ("3 days ago") for the given game day. */
    public static void appendAge(GText text, int day) {
        int back = Math.max(0, TIME.days().bitsSinceStart() - day);
        DicTime.setAgo(text, back * TIME.secondsPerDay());
    }

    /** Renders text horizontally centred at cx. */
    public static void centerText(SPRITE_RENDERER r, GText text, int cx, int y) {
        text.adjustWidth();
        text.render(r, cx - text.width() / 2, y);
    }

    /** Appends a percentage with 2 decimal places and an explicit sign (e.g. +5.20%). */
    public static void appendPercent(GText text, double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded >= 0) text.add('+');
        text.add(rounded, 2).add('%');
    }

    /** Appends a decimal number with 2 places, optionally with sign. */
    public static void appendNumber(GText text, double value, boolean signed) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (signed && rounded >= 0) text.add('+');
        text.add(rounded);
    }

    /** Appends a monetary value rounded to the nearest integer, optionally with sign. */
    public static void appendMoney(GText text, double value, boolean signed) {
        long rounded = Math.round(value);
        if (signed && rounded >= 0) text.add('+');
        text.add(rounded);
    }
}
