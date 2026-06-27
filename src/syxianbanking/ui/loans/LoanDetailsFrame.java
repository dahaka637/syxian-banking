package syxianbanking.ui.loans;

import init.sprite.UI.UI;
import snake2d.MButt;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.misc.CLAMP;
import syxianbanking.TR;
import syxianbanking.banking.BankState;
import syxianbanking.domain.Loan;
import syxianbanking.ui.UiUtils;
import util.colors.GCOLOR;
import util.gui.misc.GText;

/**
 * Detail view for the loan selected in LoanListFrame.
 *
 * Top section: contract stats (rate, penalty, principal, installment, progress).
 * Bottom section: scrollable event history for the selected loan.
 *
 * History colour coding:
 *   Yellow = contract created
 *   Green  = installment paid in full
 *   Orange = partial payment (insufficient funds)
 *   Red    = late penalty applied
 *   Cyan   = early repayment
 */
final class LoanDetailsFrame extends CLICKABLE.ClickableAbs {

    private static final int ROW_H = 23;
    private int scroll;
    private final GText header = new GText(UI.FONT().H2, 140).lablify();
    private final GText text   = new GText(UI.FONT().S,   260);

    LoanDetailsFrame(int w, int h) { super(w, h); }

    @Override
    protected void render(SPRITE_RENDERER r, float ds,
            boolean isActive, boolean isSelected, boolean isHovered) {
        UiUtils.card(r, body().x1(), body().x2(), body().y1(), body().y2());
        BankState bank    = BankState.INSTANCE;
        int       loanIdx = bank.loans.selectedLoan;
        int x = body().x1() + 16;
        int y = body().y1() + 10;

        header.clear().add(TR.s("loanDetails.header"));
        header.render(r, x, y);
        y += 30;

        if (!bank.loans.loanValid(loanIdx)) {
            text.clear().normalify2().add(TR.s("loanDetails.selectLoan"));
            text.render(r, x, y);
            return;
        }

        Loan loan = bank.loans.loans[loanIdx];

        renderStat(r, x,       y, TR.s("loanDetails.finalRate"),    loan.finalRate,  true);
        renderMoney(r, x + 420, y, TR.s("loanDetails.borrowed"),    loan.originalPrincipal);
        y += 20;

        renderStat(r, x,       y, TR.s("loanDetails.latePenalty"),  loan.penaltyRate, true);
        renderMoney(r, x + 420, y, TR.s("loanDetails.currentDue"),  loan.debtRemaining);
        y += 20;

        double contractedInterest = loan.contractedInstallment * loan.installmentsContracted - loan.originalPrincipal;
        renderMoney(r, x,       y, TR.s("loanDetails.contractedInterest"), Math.max(0.0, contractedInterest));
        renderMoney(r, x + 420, y, TR.s("loanDetails.currentInstallment"), loan.currentInstallment);
        y += 20;

        text.clear().normalify2().add(TR.s("loanDetails.remainingInstallments")).add(' ')
                .add(loan.installmentsRemaining).add('/').add(loan.installmentsContracted);
        text.render(r, x, y);
        y += 22;

        GCOLOR.UI().border().render(r, x, body().x2() - 16, y, y + 1);
        y += 6;
        text.clear().lablifySub().add(TR.s("loanDetails.contractHistory"));
        text.render(r, x, y);
        y += 20;

        if (isHovered) {
            float wheel = MButt.clearWheelSpin();
            if      (wheel > 0) scroll--;
            else if (wheel < 0) scroll++;
        }
        int rows      = Math.max(1, (body().y2() - y - 8) / ROW_H);
        int maxScroll = Math.max(0, loan.historyCount - rows);
        scroll = CLAMP.i(scroll, 0, maxScroll);

        if (loan.historyCount == 0) {
            text.clear().normalify2().add(TR.s("loanDetails.noEntries"));
            text.render(r, x, y);
            return;
        }

        int end = Math.min(loan.historyCount, scroll + rows);
        for (int i = scroll; i < end; i++) {
            int   type  = loan.historyTypes[i];
            COLOR color = historyColor(type);

            text.clear().color(color).add(historyName(type));
            text.render(r, x, y);

            text.clear().color(color);
            UiUtils.appendMoney(text, historyDisplayAmount(loan, i), true);
            if (type == Loan.OP_EARLY && loan.historyDiscounts[i] > 0) {
                text.add(" (").add(TR.s("label.discountShort")).add(' ');
                UiUtils.appendMoney(text, loan.historyDiscounts[i], false);
                text.add(')');
            }
            text.render(r, x + 190, y);

            text.clear().normalify2().add(TR.s("label.balance")).add(' ');
            UiUtils.appendMoney(text, loan.historyBalances[i], false);
            text.render(r, x + 460, y);

            text.clear().normalify2();
            UiUtils.appendAge(text, loan.historyDays[i]);
            text.render(r, x + 650, y);

            y += ROW_H;
        }
    }

    private void renderStat(SPRITE_RENDERER r, int x, int y, CharSequence label, double value, boolean percent) {
        text.clear().normalify2().add(label).add(' ');
        if (percent) UiUtils.appendPercent(text, value);
        else         UiUtils.appendNumber(text, value, false);
        text.render(r, x, y);
    }

    private void renderMoney(SPRITE_RENDERER r, int x, int y, CharSequence label, double value) {
        text.clear().normalify2().add(label).add(' ');
        UiUtils.appendMoney(text, value, false);
        text.render(r, x, y);
    }

    private COLOR historyColor(int type) {
        if (type == Loan.OP_PENALTY)  return COLOR.RED100;
        if (type == Loan.OP_EARLY)    return COLOR.NYAN100;
        if (type == Loan.OP_CONTRACT) return COLOR.YELLOW100;
        if (type == Loan.OP_PARTIAL)  return COLOR.ORANGE100;
        return COLOR.GREEN100;
    }

    private CharSequence historyName(int type) {
        if (type == Loan.OP_CONTRACT) return TR.s("loanHistory.contract");
        if (type == Loan.OP_PARTIAL)  return TR.s("loanHistory.partial");
        if (type == Loan.OP_PENALTY)  return TR.s("loanHistory.penalty");
        if (type == Loan.OP_EARLY)    return TR.s("loanHistory.early");
        return TR.s("loanHistory.payment");
    }

    // Payments and repayments are shown negative (money leaving the player).
    private double historyDisplayAmount(Loan loan, int i) {
        int type = loan.historyTypes[i];
        if (type == Loan.OP_PAYMENT || type == Loan.OP_PARTIAL || type == Loan.OP_EARLY)
            return -loan.historyAmounts[i];
        return loan.historyAmounts[i];
    }
}
