package syxianbanking.ui.savings;

import init.sprite.UI.UI;
import snake2d.MButt;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.misc.CLAMP;
import syxianbanking.TR;
import syxianbanking.banking.BankConstants;
import syxianbanking.banking.BankState;
import syxianbanking.ui.UiUtils;
import util.gui.misc.GText;

/**
 * Scrollable list of savings operations (deposits, withdrawals, interest payouts).
 *
 * Holds at most MAX_OPERATIONS entries with the most recent at index 0.
 * Mouse wheel scrolls the list when the cursor is over this component.
 *
 * Colour coding: green = deposit/interest, red = withdrawal, cyan = interest payout.
 */
final class OperationHistoryFrame extends CLICKABLE.ClickableAbs {

    private static final int ROW_H = 24;
    private int scroll;
    private final GText header = new GText(UI.FONT().H2, 120).lablify();
    private final GText text   = new GText(UI.FONT().S,   220);

    OperationHistoryFrame(int w, int h) { super(w, h); }

    @Override
    protected void render(SPRITE_RENDERER r, float ds,
            boolean isActive, boolean isSelected, boolean isHovered) {
        UiUtils.card(r, body().x1(), body().x2(), body().y1(), body().y2());
        BankState bank = BankState.INSTANCE;

        if (isHovered) {
            float wheel = MButt.clearWheelSpin();
            if      (wheel > 0) scroll--;
            else if (wheel < 0) scroll++;
        }

        int rows      = Math.max(1, (body().height() - 56) / ROW_H);
        int maxScroll = Math.max(0, bank.savings.operationCount - rows);
        scroll = CLAMP.i(scroll, 0, maxScroll);

        int x = body().x1() + 16;
        int y = body().y1() + 12;
        header.clear().add(TR.s("operations.header"));
        header.render(r, x, y);
        y += 36;

        if (bank.savings.operationCount == 0) {
            text.clear().normalify2().add(TR.s("operations.none"));
            text.render(r, x, y);
            return;
        }

        int end = Math.min(bank.savings.operationCount, scroll + rows);
        for (int i = scroll; i < end; i++) {
            int   type  = bank.savings.operationTypes[i];
            COLOR color = operationColor(type);

            text.clear().color(color).add(operationName(type));
            text.render(r, x, y);

            text.clear().color(color);
            UiUtils.appendMoney(text, bank.savings.operationAmounts[i], true);
            text.render(r, x + 220, y);

            text.clear().normalify2().add(TR.s("label.balance")).add(' ');
            UiUtils.appendMoney(text, bank.savings.operationBalances[i], false);
            text.render(r, x + 360, y);

            text.clear().normalify2();
            UiUtils.appendAge(text, bank.savings.operationDays[i]);
            text.render(r, x + 610, y);

            y += ROW_H;
        }
    }

    private COLOR operationColor(int type) {
        if (type == BankConstants.OP_WITHDRAW) return COLOR.RED100;
        if (type == BankConstants.OP_INTEREST) return COLOR.NYAN100;
        return COLOR.GREEN100;
    }

    private CharSequence operationName(int type) {
        if (type == BankConstants.OP_WITHDRAW) return TR.s("operation.withdraw");
        if (type == BankConstants.OP_INTEREST) return TR.s("operation.interest");
        return TR.s("operation.deposit");
    }
}
