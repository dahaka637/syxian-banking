package syxianbanking;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import game.boosting.BOOSTABLE_O;
import game.boosting.BOOSTABLES;
import game.boosting.BSourceInfo;
import game.boosting.Booster;
import init.constant.C;
import script.SCRIPT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sets.LIST;
import syxianbanking.banking.BankSerializer;
import syxianbanking.banking.BankState;
import syxianbanking.ui.BankingView;
import util.gui.misc.GButt;
import view.main.VIEW;
import view.ui.manage.IFullView;
import view.ui.manage.IManager;

/**
 * Mod entry point for the Songs of Syx script engine.
 *
 * Implements SCRIPT — the interface the engine uses to discover and load mods.
 * Responsibilities here are intentionally minimal:
 *   - Declare mod metadata (name, desc).
 *   - Create the per-campaign script instance (createInstance).
 * All banking logic lives in BankState and its components.
 *
 * Inner class Instance:
 *   Created once per campaign. Delegates save/load to BankSerializer and
 *   injects the bank button into the main tab bar via reflection (see install()).
 *
 * Inflation suppression:
 *   The base game applies inflation as treasury * 0.2 / DEFALTION. Songs of Syx
 *   0.71.44 made Boostable.baseValue final, so changing it by reflection no
 *   longer reliably affects the live calculation. We add a fixed game boost to
 *   DEFALTION instead, keeping the normal Boostable.get(...) path intact.
 */
public final class SyxianBanking implements SCRIPT {

    @Override
    public CharSequence name() { return "Syxian Banking"; }

    @Override
    public CharSequence desc() { return TR.s("mod.desc"); }

    @Override
    public boolean isSelectable() {
        return false; // always active; does not appear in the player's mod selection list
    }

    @Override
    public boolean forceInit() { return true; }

    @Override
    public SCRIPT_INSTANCE createInstance() {
        BankState.INSTANCE.resetForNewContext();
        return new Instance();
    }

    // ---- Per-campaign script instance ----

    public static final class Instance implements SCRIPT_INSTANCE {

        // Tracks the manager where the button was installed to avoid reinstalling every frame.
        private static IManager    installedManager;
        private static BankingView bankingView;
        private static boolean     inflationSuppressed = false;

        @Override
        public void update(double ds) {
            install();
            suppressInflation();
            BankState.INSTANCE.updateIfNeeded();
        }

        @Override
        public void save(FilePutter file) { BankSerializer.save(file, BankState.INSTANCE); }

        @Override
        public void load(FileGetter file) throws IOException { BankSerializer.load(file, BankState.INSTANCE); }

        @Override
        public boolean handleBrokenSavedState() {
            // Reset to defaults rather than crashing the game on a corrupted save.
            BankState.INSTANCE.resetForNewContext();
            return true;
        }

        /**
         * Injects the bank button into the engine's main tab bar.
         * Called every frame via update(), but the actual injection only happens
         * once per IManager instance (detected by the installedManager reference).
         */
        private static void install() {
            try {
                IManager manager = VIEW.UI().manager;
                if (manager == null || installedManager == manager) return;

                bankingView = new BankingView();
                GuiSection top = getTop(manager);

                GButt.ButtPanel button = new GButt.ButtPanel(bankingView.icon) {
                    @Override protected void clickA()    { manager.show(bankingView); }
                    @Override protected void renAction() { selectedSet(isCurrent(manager, bankingView)); }
                };
                button.hoverInfoSet(bankingView.title);
                button.pad(16, 2);
                placeWithMainTabs(top, button);
                top.body().centerY(0, IManager.TOP_HEIGHT);
                installedManager = manager;
            } catch (Throwable e) {
                writeDebug("install failed: " + e.getClass().getName() + " - " + e.getMessage());
            }
        }

        // The engine does not expose the top bar publicly, so we read it via reflection.
        private static GuiSection getTop(IManager manager) throws ReflectiveOperationException {
            Field field = IManager.class.getDeclaredField("top");
            field.setAccessible(true);
            return (GuiSection) field.get(manager);
        }

        /**
         * Inserts the bank button to the left of the existing tabs and recentres the group.
         * Strategy:
         *   1. Find the X range of the existing main tabs.
         *   2. Place the new button at the left edge.
         *   3. Shift all original tabs right by the button width.
         *   4. Recentre the whole group horizontally on screen.
         */
        private static void placeWithMainTabs(GuiSection top, GButt.ButtPanel button) {
            LIST<RENDEROBJ> elements = top.elements();
            RENDEROBJ anchor = null;
            int tabX1 = C.WIDTH(), tabX2 = 0;

            for (int i = 0; i < elements.size(); i++) {
                RENDEROBJ el = elements.get(i);
                if (isMainTab(el)) {
                    if (anchor == null) anchor = el;
                    tabX1 = Math.min(tabX1, el.body().x1());
                    tabX2 = Math.max(tabX2, el.body().x2());
                }
            }

            if (anchor == null) { top.add(button); return; }

            button.body().moveX1(tabX1);
            button.body().centerY(anchor.body());
            top.add(button);

            int insertedWidth = button.body().width();
            for (int i = 0; i < elements.size(); i++) {
                RENDEROBJ el = elements.get(i);
                if (el != button && isMainTab(el)) el.body().incrX(insertedWidth);
            }

            int dx = (C.WIDTH() - ((tabX2 + insertedWidth) - tabX1)) / 2 - tabX1;
            for (int i = 0; i < elements.size(); i++) {
                RENDEROBJ el = elements.get(i);
                if (el == button || isMainTab(el)) el.body().incrX(dx);
            }
        }

        // Main tabs are in the top strip but not pinned to the far right corner.
        private static boolean isMainTab(RENDEROBJ element) {
            return element.body().y1() < IManager.TOP_HEIGHT && element.body().x2() < C.WIDTH() - 96;
        }

        private static boolean isCurrent(IManager manager, IFullView view) {
            try {
                Field field = IManager.class.getDeclaredField("current");
                field.setAccessible(true);
                return field.get(manager) == view;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }

        // DEFALTION is the anti-inflation divisor. Adding +9998 keeps its effective value at
        // 9999 through the game's normal boost pipeline, which suppresses inflation/deflation.
        private static void suppressInflation() {
            if (inflationSuppressed) return;
            try {
                new Booster(new BSourceInfo("Syxian Banking inflation suppression", null), false) {
                    @Override public double from() { return 9998.0; }
                    @Override public double to() { return 9998.0; }
                    @Override public double getValue(double input) { return 9998.0; }
                    @Override protected double pget(BOOSTABLE_O o) { return 1.0; }
                }.add(BOOSTABLES.CIVICS().DEFALTION);
                inflationSuppressed = true;
            } catch (Throwable e) {
                writeDebug("suppressInflation failed: " + e.getClass().getName() + " - " + e.getMessage());
            }
        }

        private static void writeDebug(String state) {
            try {
                Path file = Paths.get(System.getProperty("user.home"), "AppData", "Roaming",
                        "songsofsyx", "mods", "Syxian Banking", "debug_loaded.txt");
                Files.write(file,
                        ("Syxian Banking script " + state + System.lineSeparator()).getBytes(),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        }
    }
}
