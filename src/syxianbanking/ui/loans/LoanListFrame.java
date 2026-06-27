package syxianbanking.ui.loans;

import init.sprite.UI.UI;
import snake2d.MButt;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.misc.CLAMP;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import syxianbanking.ui.UiUtils;
import util.gui.misc.GText;

/**
 * Scrollable list of active loans.
 *
 * Each row shows: contract number, outstanding balance, current installment and remaining installments.
 * Clicking a row sets selectedLoan in LoanManager, which LoanDetailsFrame uses to show details.
 * Selected row is highlighted white 25%; hovered row white 15%.
 */
final class LoanListFrame extends CLICKABLE.ClickableAbs {

    private static final int ROW_H = 28;
    private int hoveredLoan = -1;
    private int scroll;
    private final GText header = new GText(UI.FONT().H2, 140).lablify();
    private final GText text   = new GText(UI.FONT().S,   220);

    LoanListFrame(int w, int h) { super(w, h); }

    @Override
    public boolean hover(COORDINATE mCoo) {
        hoveredLoan = -1;
        boolean ret = super.hover(mCoo);
        if (ret) {
            // 48 px header height; calculate which row the cursor is over.
            int localY = mCoo.y() - (body().y1() + 48);
            if (localY >= 0) {
                int loan = scroll + localY / ROW_H;
                if (BankState.INSTANCE.loans.loanValid(loan)) hoveredLoan = loan;
            }
        }
        return ret;
    }

    @Override
    protected void clickA() {
        if (BankState.INSTANCE.loans.loanValid(hoveredLoan))
            BankState.INSTANCE.loans.selectedLoan = hoveredLoan;
    }

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

        int rows = Math.max(1, (body().height() - 56) / ROW_H);
        scroll = CLAMP.i(scroll, 0, Math.max(0, bank.loans.loanCount - rows));

        int x = body().x1() + 16;
        int y = body().y1() + 12;
        header.clear().add(TR.s("loans.contracted"));
        header.render(r, x, y);
        y += 36;

        if (bank.loans.loanCount == 0) {
            text.clear().normalify2().add(TR.s("loans.none"));
            text.render(r, x, y);
            return;
        }

        int end = Math.min(bank.loans.loanCount, scroll + rows);
        for (int i = scroll; i < end; i++) {
            if      (i == bank.loans.selectedLoan) COLOR.WHITE25.render(r, x - 6, body().x2() - 16, y - 2, y + ROW_H - 2);
            else if (i == hoveredLoan)             COLOR.WHITE15.render(r, x - 6, body().x2() - 16, y - 2, y + ROW_H - 2);

            text.clear().lablifySub().add(TR.s("label.contractNumber")).add(bank.loans.loans[i].id);
            text.render(r, x, y);

            text.clear().normalify2().add(TR.s("label.due")).add(' ');
            UiUtils.appendMoney(text, bank.loans.loans[i].debtRemaining, false);
            text.render(r, x + 180, y);

            text.clear().normalify2().add(TR.s("label.installment")).add(' ');
            UiUtils.appendMoney(text, bank.loans.loans[i].currentInstallment, false);
            text.render(r, x + 380, y);

            text.clear().normalify2().add(TR.s("label.remaining")).add(' ')
                    .add(bank.loans.loans[i].installmentsRemaining);
            text.render(r, x + 590, y);

            y += ROW_H;
        }
    }
}
