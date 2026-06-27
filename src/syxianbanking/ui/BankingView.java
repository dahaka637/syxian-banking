package syxianbanking.ui;

import init.sprite.UI.Icon;
import init.sprite.UI.UI;
import settlement.main.SETT;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.gui.renderable.RENDEROBJ;
import syxianbanking.TR;
import syxianbanking.ui.loans.LoansPanel;
import syxianbanking.ui.savings.SavingsPanel;
import util.gui.misc.GButt;
import view.ui.manage.IFullView;

/**
 * Main bank window — shown when the player clicks the bank button in the top tab bar.
 *
 * Layout: two tabs (Savings / Loans) at the top, with the corresponding panel below.
 * Tab switching is handled by ClickSwitch from the engine, which manages panel visibility.
 *
 * The university icon is used as the bank symbol; falls back to a generic icon
 * if no university room is available in the base game data.
 */
public final class BankingView extends IFullView {

    private final SavingsPanel savings = new SavingsPanel();
    private final LoansPanel   loans   = new LoansPanel();
    private final CLICKABLE.ClickSwitch switcher = new CLICKABLE.ClickSwitch(savings);

    public BankingView() {
        super("Syxian Banking", universityIcon());
        section.body().setWidth(WIDTH).setHeight(1);
        switcher.setD(DIR.N);
        section.addRelBody(8,  DIR.S, picker());
        section.addRelBody(16, DIR.S, switcher);
    }

    private static Icon universityIcon() {
        try {
            return SETT.ROOMS().UNIVERSITIES.get(0).icon;
        } catch (Exception e) {
            return UI.icons().l.vial;
        }
    }

    private GuiSection picker() {
        GuiSection tabs = new GuiSection();
        tabs.addRightC(0, tab(TR.s("tab.savings"), savings));
        tabs.addRightC(0, tab(TR.s("tab.loans"),   loans));
        return tabs;
    }

    private GButt.ButtPanel tab(CharSequence label, RENDEROBJ target) {
        GButt.ButtPanel button = new GButt.ButtPanel(label) {
            @Override protected void clickA()    { switcher.set(target); }
            @Override protected void renAction() { selectedSet(switcher.current() == target); }
        }.setDim(180, 32);
        button.hoverTitleSet(label);
        return button;
    }
}
