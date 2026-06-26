package syxianbanking;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import game.faction.FCredits;
import game.faction.FACTIONS;
import game.faction.FCredits.CTYPE;
import game.faction.npc.FactionNPC;
import game.time.TIME;
import init.constant.C;
import init.sprite.UI.Icon;
import init.sprite.UI.UI;
import script.SCRIPT;
import settlement.main.SETT;
import snake2d.MButt;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.color.ColorImp;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.misc.ACTION;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.LIST;
import util.data.INT.IntImp;
import util.colors.GCOLOR;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GInputInt;
import util.gui.misc.GText;
import util.gui.table.GStaples;
import util.info.GFORMAT;
import util.text.DicTime;
import view.main.VIEW;
import view.ui.manage.IFullView;
import view.ui.manage.IManager;

public final class SyxianBanking implements SCRIPT {
    private static final BankRates RATES = new BankRates();

    @Override
    public CharSequence name() {
        return "Syxian Banking";
    }

    @Override
    public CharSequence desc() {
        return TR.s("mod.desc");
    }

    @Override
    public boolean isSelectable() {
        return false;
    }

    @Override
    public boolean forceInit() {
        return true;
    }

    @Override
    public SCRIPT_INSTANCE createInstance() {
        return new Instance();
    }

    private static final class TR {
        private static final String DEFAULT = "en";
        private static final Properties FALLBACK = new Properties();
        private static final Properties ACTIVE = new Properties();
        private static boolean loaded;

        static CharSequence s(String key) {
            ensureLoaded();
            String value = ACTIVE.getProperty(key);
            if (value == null) {
                value = FALLBACK.getProperty(key);
            }
            return value == null ? key : value;
        }

        private static void ensureLoaded() {
            if (loaded) {
                return;
            }
            loaded = true;
            load(FALLBACK, DEFAULT);
            String language = detectLanguage();
            if (!DEFAULT.equals(language)) {
                load(ACTIVE, language);
            }
        }

        private static void load(Properties target, String language) {
            String path = "/syxianbanking/lang/" + language + ".properties";
            try (InputStream in = SyxianBanking.class.getResourceAsStream(path)) {
                if (in != null) {
                    target.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        }

        private static String detectLanguage() {
            String appData = System.getenv("APPDATA");
            Path settings = appData == null || appData.isEmpty()
                    ? Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "songsofsyx", "settings",
                            "LauncherSettings.txt")
                    : Paths.get(appData, "songsofsyx", "settings", "LauncherSettings.txt");
            try {
                String text = new String(Files.readAllBytes(settings), StandardCharsets.UTF_8);
                int key = text.indexOf("LANGUAGE");
                if (key < 0) {
                    return DEFAULT;
                }
                int firstQuote = text.indexOf('"', key);
                int secondQuote = firstQuote < 0 ? -1 : text.indexOf('"', firstQuote + 1);
                if (firstQuote < 0 || secondQuote < 0) {
                    return DEFAULT;
                }
                return normalize(text.substring(firstQuote + 1, secondQuote));
            } catch (IOException ignored) {
                return DEFAULT;
            }
        }

        private static String normalize(String language) {
            if (language == null || language.isEmpty()) {
                return DEFAULT;
            }
            if ("cs".equals(language) || "de".equals(language) || "es-ES".equals(language) || "fr".equals(language)
                    || "hu".equals(language) || "it".equals(language) || "ja".equals(language)
                    || "ko".equals(language) || "nl".equals(language) || "pl".equals(language)
                    || "pt-BR".equals(language) || "ru".equals(language) || "tr".equals(language)
                    || "uk".equals(language) || "zh-CN".equals(language) || "zh-TW".equals(language)) {
                return language;
            }
            if (language.startsWith("es")) {
                return "es-ES";
            }
            if (language.startsWith("pt")) {
                return "pt-BR";
            }
            if (language.startsWith("zh")) {
                return language.contains("TW") || language.contains("tw") ? "zh-TW" : "zh-CN";
            }
            return DEFAULT;
        }
    }

    public static final class Instance implements SCRIPT_INSTANCE {
        private static IManager installedManager;
        private static BankingView bankingView;

        @Override
        public void update(double ds) {
            install();
            RATES.updateIfNeeded();
        }

        @Override
        public void save(FilePutter file) {
            RATES.save(file);
        }

        @Override
        public void load(FileGetter file) throws IOException {
            RATES.load(file);
        }

        @Override
        public boolean handleBrokenSavedState() {
            return true;
        }

        private static void install() {
            try {
                IManager manager = VIEW.UI().manager;
                if (manager == null || installedManager == manager) {
                    return;
                }

                bankingView = new BankingView();
                GuiSection top = getTop(manager);
                GButt.ButtPanel button = new GButt.ButtPanel(bankingView.icon) {
                    @Override
                    protected void clickA() {
                        manager.show(bankingView);
                    }

                    @Override
                    protected void renAction() {
                        selectedSet(isCurrent(manager, bankingView));
                    }
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

        private static void writeDebug(String state) {
            try {
                Path file = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "songsofsyx", "mods",
                        "Syxian Banking", "debug_loaded.txt");
                Files.write(file, ("Syxian Banking script " + state + System.lineSeparator()).getBytes(),
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        }

        private static GuiSection getTop(IManager manager) throws ReflectiveOperationException {
            Field field = IManager.class.getDeclaredField("top");
            field.setAccessible(true);
            return (GuiSection) field.get(manager);
        }

        private static void placeWithMainTabs(GuiSection top, GButt.ButtPanel button) {
            LIST<RENDEROBJ> elements = top.elements();
            RENDEROBJ anchor = null;
            int tabX1 = C.WIDTH();
            int tabX2 = 0;

            for (int i = 0; i < elements.size(); i++) {
                RENDEROBJ element = elements.get(i);
                if (isMainTab(element)) {
                    if (anchor == null) {
                        anchor = element;
                    }
                    tabX1 = Math.min(tabX1, element.body().x1());
                    tabX2 = Math.max(tabX2, element.body().x2());
                }
            }

            if (anchor == null) {
                top.add(button);
                return;
            }

            button.body().moveX1(tabX1);
            button.body().centerY(anchor.body());
            top.add(button);

            int insertedWidth = button.body().width();
            for (int i = 0; i < elements.size(); i++) {
                RENDEROBJ element = elements.get(i);
                if (element != button && isMainTab(element)) {
                    element.body().incrX(insertedWidth);
                }
            }

            int groupWidth = (tabX2 + insertedWidth) - tabX1;
            int targetX1 = (C.WIDTH() - groupWidth) / 2;
            int dx = targetX1 - tabX1;

            for (int i = 0; i < elements.size(); i++) {
                RENDEROBJ element = elements.get(i);
                if (element == button || isMainTab(element)) {
                    element.body().incrX(dx);
                }
            }
        }

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

    }

    private static final class BankingView extends IFullView {
        private final SavingsPanel savings = new SavingsPanel();
        private final LoansPanel loans = new LoansPanel();
        private final DataPanel data = new DataPanel();
        private final CLICKABLE.ClickSwitch switcher = new CLICKABLE.ClickSwitch(savings);

        BankingView() {
            super("Syxian Banking", universityIcon());
            section.body().setWidth(WIDTH).setHeight(1);
            switcher.setD(DIR.N);
            section.addRelBody(8, DIR.S, picker());
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
            tabs.addRightC(0, tab(TR.s("tab.loans"), loans));
            tabs.addRightC(0, tab(TR.s("tab.data"), data));
            return tabs;
        }

        private GButt.ButtPanel tab(CharSequence label, RENDEROBJ target) {
            GButt.ButtPanel button = new GButt.ButtPanel(label) {
                @Override
                protected void clickA() {
                    switcher.set(target);
                }

                @Override
                protected void renAction() {
                    selectedSet(switcher.current() == target);
                }
            }.setDim(180, 32);
            button.hoverTitleSet(label);
            return button;
        }
    }

    private static final class BankRates {
        private static final Field F_INFLATION;
        static {
            Field f = null;
            try {
                f = FCredits.class.getDeclaredField("INFLATION");
                f.setAccessible(true);
            } catch (Throwable ignored) {}
            F_INFLATION = f;
        }

        private static final int SAVE_MARK = 0x53584235;
        private static final int MAX_OPERATIONS = 100;
        private static final int MAX_LOANS = 16;
        private static final int MAX_LOAN_HISTORY = 100;
        private static final int HISTORY_DAYS = 48;
        private static final double SAVINGS_ADJUSTMENT_SPEED = 0.08;
        private static final double LOAN_ADJUSTMENT_SPEED = 0.12;
        private static final double EARLY_PAYMENT_DISCOUNT_FACTOR = 0.75;
        private static final int OP_DEPOSIT = 0;
        private static final int OP_WITHDRAW = 1;
        private static final int OP_INTEREST = 2;
        private static final int LOAN_OP_CONTRACT = 0;
        private static final int LOAN_OP_PAYMENT = 1;
        private static final int LOAN_OP_PARTIAL = 2;
        private static final int LOAN_OP_PENALTY = 3;
        private static final int LOAN_OP_EARLY = 4;

        private int day = Integer.MIN_VALUE;
        private int kingdoms;
        private double averageWealth;
        private double totalEconomicWeight;
        private double averagePurchasingPower;
        private double economicDispersion;
        private double negativeStress;
        private double positiveStrength;
        private double playerTreasury;
        private double playerBankBalance;
        private double playerNetWorth;
        private double maxLoanAvailable;
        private double interestRemainder;
        private double latePenaltyRate;
        private double currentSavingsRate;
        private double currentLoanRate;
        private double targetSavingsRate;
        private double targetLoanRate;
        private boolean initialized;
        private boolean historyInitialized;
        private boolean reinvestSavingsInterest;
        private int historySamples;
        private int maxLoanInstallments;
        private String error;
        private final double[] savingsHistory = new double[HISTORY_DAYS];
        private final double[] loanHistory = new double[HISTORY_DAYS];
        private final double[] latePenaltyHistory = new double[HISTORY_DAYS];
        private final double[] bankBalanceHistory = new double[HISTORY_DAYS];
        private int operationCount;
        private final int[] operationTypes = new int[MAX_OPERATIONS];
        private final int[] operationDays = new int[MAX_OPERATIONS];
        private final double[] operationAmounts = new double[MAX_OPERATIONS];
        private final double[] operationBalances = new double[MAX_OPERATIONS];
        private int selectedLoan = -1;
        private int nextLoanId = 1;
        private int loanCount;
        private final int[] loanIds = new int[MAX_LOANS];
        private final int[] loanContractDay = new int[MAX_LOANS];
        private final int[] loanInstallmentsContracted = new int[MAX_LOANS];
        private final int[] loanInstallmentsRemaining = new int[MAX_LOANS];
        private final double[] loanOriginalPrincipal = new double[MAX_LOANS];
        private final double[] loanNetWorthAtContract = new double[MAX_LOANS];
        private final double[] loanBaseRate = new double[MAX_LOANS];
        private final double[] loanFinalRate = new double[MAX_LOANS];
        private final double[] loanPeriodRate = new double[MAX_LOANS];
        private final double[] loanContractedInstallment = new double[MAX_LOANS];
        private final double[] loanCurrentInstallment = new double[MAX_LOANS];
        private final double[] loanPenaltyRate = new double[MAX_LOANS];
        private final double[] loanPrincipalRemaining = new double[MAX_LOANS];
        private final double[] loanDebtRemaining = new double[MAX_LOANS];
        private final int[] loanHistoryCount = new int[MAX_LOANS];
        private final int[][] loanHistoryTypes = new int[MAX_LOANS][MAX_LOAN_HISTORY];
        private final int[][] loanHistoryDays = new int[MAX_LOANS][MAX_LOAN_HISTORY];
        private final double[][] loanHistoryAmounts = new double[MAX_LOANS][MAX_LOAN_HISTORY];
        private final double[][] loanHistoryDiscounts = new double[MAX_LOANS][MAX_LOAN_HISTORY];
        private final double[][] loanHistoryBalances = new double[MAX_LOANS][MAX_LOAN_HISTORY];

        void updateIfNeeded() {
            int currentDay = TIME.days().bitsSinceStart();
            if (currentDay == day) {
                return;
            }
            day = currentDay;
            calculate();
        }

        private void calculate() {
            try {
                kingdoms = 0;
                averageWealth = 0;
                totalEconomicWeight = 0;
                averagePurchasingPower = 0;
                economicDispersion = 0;
                negativeStress = 0;
                positiveStrength = 0;

                for (FactionNPC faction : FACTIONS.NPCs()) {
                    if (!faction.isActive()) {
                        continue;
                    }
                    averageWealth += FACTIONS.WORTH().faction(faction);
                    kingdoms++;
                }

                if (kingdoms == 0) {
                    error = TR.s("error.noKingdoms").toString();
                    return;
                }

                averageWealth /= kingdoms;

                for (FactionNPC faction : FACTIONS.NPCs()) {
                    if (!faction.isActive()) {
                        continue;
                    }
                    double purchasingPower = (faction.stockpile.creditScore() - 1.0) * 100.0;
                    double wealth = FACTIONS.WORTH().faction(faction);
                    double wealthFactor = averageWealth <= 0 ? 1.0 : wealth / averageWealth;
                    double economicWeight = economicWeight(wealthFactor);

                    averagePurchasingPower += purchasingPower * economicWeight;
                    totalEconomicWeight += economicWeight;
                }

                if (totalEconomicWeight <= 0) {
                    error = TR.s("error.invalidEconomicWeight").toString();
                    return;
                }

                averagePurchasingPower /= totalEconomicWeight;

                for (FactionNPC faction : FACTIONS.NPCs()) {
                    if (!faction.isActive()) {
                        continue;
                    }
                    double purchasingPower = (faction.stockpile.creditScore() - 1.0) * 100.0;
                    double wealth = FACTIONS.WORTH().faction(faction);
                    double wealthFactor = averageWealth <= 0 ? 1.0 : wealth / averageWealth;
                    double economicWeight = economicWeight(wealthFactor);

                    economicDispersion += Math.abs(purchasingPower - averagePurchasingPower) * economicWeight;
                    negativeStress += Math.max(0.0, -purchasingPower) * economicWeight;
                    positiveStrength += Math.max(0.0, purchasingPower) * economicWeight;
                }
                economicDispersion /= totalEconomicWeight;
                negativeStress /= totalEconomicWeight;
                positiveStrength /= totalEconomicWeight;

                targetSavingsRate = 2.0 + negativeStress * 0.35 + economicDispersion * 0.20
                        - positiveStrength * 0.10;
                targetSavingsRate = Math.max(0.0, targetSavingsRate);

                targetLoanRate = targetSavingsRate + 3.0 + negativeStress * 0.55 + economicDispersion * 0.35;
                targetLoanRate = Math.max(0.0, targetLoanRate);

                if (!initialized) {
                    currentSavingsRate = targetSavingsRate;
                    currentLoanRate = targetLoanRate;
                    initialized = true;
                } else {
                    currentSavingsRate += (targetSavingsRate - currentSavingsRate) * SAVINGS_ADJUSTMENT_SPEED;
                    currentLoanRate += (targetLoanRate - currentLoanRate) * LOAN_ADJUSTMENT_SPEED;
                }

                playerTreasury = FACTIONS.player().credits().credits();
                playerNetWorth = Math.max(0.0, FACTIONS.WORTH().faction(FACTIONS.player()));
                refreshLoanCapacity();
                double daysPerYear = TIME.years().bitConversion(TIME.days());
                latePenaltyRate = contractedPenaltyRate();
                applyDailyInterest(daysPerYear);
                processDailyLoans();
                refreshLoanCapacity();
                pushHistory();
                counterInflation();

                error = null;
            } catch (Throwable e) {
                error = TR.s("error.calculate").toString() + e.getClass().getSimpleName();
            }
        }

        private double economicWeight(double wealthFactor) {
            double safeFactor = Math.max(wealthFactor, 0.0);
            return Math.max(0.0, Math.log(safeFactor + 1.0) / Math.log(2.0));
        }

        private void counterInflation() {
            if (F_INFLATION == null) return;
            try {
                double todayInflation = F_INFLATION.getDouble(FACTIONS.player().credits());
                if (Math.abs(todayInflation) > 0.001) {
                    FACTIONS.player().credits().inc(-todayInflation, CTYPE.INFLATION);
                }
            } catch (Throwable ignored) {}
        }

        void save(FilePutter file) {
            file.bool(initialized);
            file.d(currentSavingsRate);
            file.d(currentLoanRate);
            file.d(targetSavingsRate);
            file.d(targetLoanRate);
            file.i(day);
            file.i(SAVE_MARK);
            file.d(playerBankBalance);
            file.d(interestRemainder);
            file.bool(reinvestSavingsInterest);
            file.i(operationCount);
            for (int i = 0; i < operationCount; i++) {
                file.i(operationTypes[i]);
                file.i(operationDays[i]);
                file.d(operationAmounts[i]);
                file.d(operationBalances[i]);
            }
            file.i(nextLoanId);
            file.i(loanCount);
            file.i(selectedLoan);
            for (int i = 0; i < loanCount; i++) {
                saveLoan(file, i);
            }
        }

        void load(FileGetter file) throws IOException {
            initialized = file.bool();
            currentSavingsRate = file.d();
            currentLoanRate = file.d();
            targetSavingsRate = file.d();
            targetLoanRate = file.d();
            day = file.i();
            resetBankSaveState();
            if (file.remainingInts() <= 0 || !file.test(SAVE_MARK)) {
                historyInitialized = false;
                historySamples = 0;
                return;
            }
            playerBankBalance = Math.max(0.0, sanitizeMoney(file.d()));
            interestRemainder = Math.max(0.0, sanitizeMoney(file.d()));
            reinvestSavingsInterest = file.bool();
            int savedOperations = CLAMP.i(file.i(), 0, MAX_OPERATIONS);
            for (int i = 0; i < savedOperations; i++) {
                int type = file.i();
                int opDay = file.i();
                double amount = sanitizeMoney(file.d());
                double balance = Math.max(0.0, sanitizeMoney(file.d()));
                if (operationTypeValid(type)) {
                    operationTypes[operationCount] = type;
                    operationDays[operationCount] = opDay;
                    operationAmounts[operationCount] = amount;
                    operationBalances[operationCount] = balance;
                    operationCount++;
                }
            }
            nextLoanId = Math.max(1, file.i());
            int savedLoans = CLAMP.i(file.i(), 0, MAX_LOANS);
            selectedLoan = file.i();
            loanCount = savedLoans;
            for (int i = 0; i < savedLoans; i++) {
                loadLoan(file, i);
            }
            if (selectedLoan < 0 || selectedLoan >= loanCount) {
                selectedLoan = loanCount > 0 ? 0 : -1;
            }
            historyInitialized = false;
            historySamples = 0;
        }

        private void resetBankSaveState() {
            playerBankBalance = 0;
            interestRemainder = 0;
            reinvestSavingsInterest = false;
            operationCount = 0;
            for (int i = 0; i < MAX_OPERATIONS; i++) {
                operationTypes[i] = 0;
                operationDays[i] = 0;
                operationAmounts[i] = 0;
                operationBalances[i] = 0;
            }
            nextLoanId = 1;
            loanCount = 0;
            selectedLoan = -1;
            for (int i = 0; i < MAX_LOANS; i++) {
                clearLoan(i);
            }
        }

        private void clearLoan(int i) {
            loanIds[i] = 0;
            loanContractDay[i] = 0;
            loanInstallmentsContracted[i] = 0;
            loanInstallmentsRemaining[i] = 0;
            loanOriginalPrincipal[i] = 0;
            loanNetWorthAtContract[i] = 0;
            loanBaseRate[i] = 0;
            loanFinalRate[i] = 0;
            loanPeriodRate[i] = 0;
            loanContractedInstallment[i] = 0;
            loanCurrentInstallment[i] = 0;
            loanPenaltyRate[i] = 0;
            loanPrincipalRemaining[i] = 0;
            loanDebtRemaining[i] = 0;
            loanHistoryCount[i] = 0;
            for (int h = 0; h < MAX_LOAN_HISTORY; h++) {
                loanHistoryTypes[i][h] = 0;
                loanHistoryDays[i][h] = 0;
                loanHistoryAmounts[i][h] = 0;
                loanHistoryDiscounts[i][h] = 0;
                loanHistoryBalances[i][h] = 0;
            }
        }

        private boolean operationTypeValid(int type) {
            return type >= OP_DEPOSIT && type <= OP_INTEREST;
        }

        private double sanitizeMoney(double value) {
            if (!Double.isFinite(value)) {
                return 0;
            }
            double max = Integer.MAX_VALUE * 64.0;
            if (value > max) {
                return max;
            }
            if (value < -max) {
                return -max;
            }
            return value;
        }

        private void saveLoan(FilePutter file, int i) {
            file.i(loanIds[i]);
            file.i(loanContractDay[i]);
            file.i(loanInstallmentsContracted[i]);
            file.i(loanInstallmentsRemaining[i]);
            file.d(loanOriginalPrincipal[i]);
            file.d(loanNetWorthAtContract[i]);
            file.d(loanBaseRate[i]);
            file.d(loanFinalRate[i]);
            file.d(loanPeriodRate[i]);
            file.d(loanContractedInstallment[i]);
            file.d(loanCurrentInstallment[i]);
            file.d(loanPenaltyRate[i]);
            file.d(loanPrincipalRemaining[i]);
            file.d(loanDebtRemaining[i]);
            file.i(loanHistoryCount[i]);
            for (int h = 0; h < loanHistoryCount[i]; h++) {
                file.i(loanHistoryTypes[i][h]);
                file.i(loanHistoryDays[i][h]);
                file.d(loanHistoryAmounts[i][h]);
                file.d(loanHistoryDiscounts[i][h]);
                file.d(loanHistoryBalances[i][h]);
            }
        }

        private void loadLoan(FileGetter file, int i) throws IOException {
            boolean store = i >= 0;
            int id = file.i();
            int contractDay = file.i();
            int installmentsContracted = file.i();
            int installmentsRemaining = file.i();
            double originalPrincipal = Math.max(0.0, sanitizeMoney(file.d()));
            double netWorthAtContract = Math.max(0.0, sanitizeMoney(file.d()));
            double baseRate = Math.max(0.0, sanitizeMoney(file.d()));
            double finalRate = Math.max(0.0, sanitizeMoney(file.d()));
            double periodRate = Math.max(0.0, sanitizeMoney(file.d()));
            double contractedInstallment = Math.max(0.0, sanitizeMoney(file.d()));
            double currentInstallment = Math.max(0.0, sanitizeMoney(file.d()));
            double penaltyRate = Math.max(0.0, sanitizeMoney(file.d()));
            double principalRemaining = Math.max(0.0, sanitizeMoney(file.d()));
            double debtRemaining = Math.max(0.0, sanitizeMoney(file.d()));
            int history = Math.max(0, file.i());

            if (store) {
                loanIds[i] = id;
                loanContractDay[i] = contractDay;
                loanInstallmentsContracted[i] = installmentsContracted;
                loanInstallmentsRemaining[i] = installmentsRemaining;
                loanOriginalPrincipal[i] = originalPrincipal;
                loanNetWorthAtContract[i] = netWorthAtContract;
                loanBaseRate[i] = baseRate;
                loanFinalRate[i] = finalRate;
                loanPeriodRate[i] = periodRate;
                loanContractedInstallment[i] = contractedInstallment;
                loanCurrentInstallment[i] = currentInstallment;
                loanPenaltyRate[i] = penaltyRate;
                loanPrincipalRemaining[i] = principalRemaining;
                loanDebtRemaining[i] = debtRemaining;
                loanHistoryCount[i] = Math.min(history, MAX_LOAN_HISTORY);
            }

            for (int h = 0; h < history; h++) {
                int type = file.i();
                int day = file.i();
                double amount = sanitizeMoney(file.d());
                double discount = Math.max(0.0, sanitizeMoney(file.d()));
                double balance = Math.max(0.0, sanitizeMoney(file.d()));
                if (store && h < MAX_LOAN_HISTORY) {
                    loanHistoryTypes[i][h] = type;
                    loanHistoryDays[i][h] = day;
                    loanHistoryAmounts[i][h] = amount;
                    loanHistoryDiscounts[i][h] = discount;
                    loanHistoryBalances[i][h] = balance;
                }
            }
        }

        void deposit(int amount) {
            if (amount <= 0) {
                return;
            }
            int available = availableTreasury();
            amount = Math.min(amount, available);
            if (amount <= 0) {
                return;
            }
            FACTIONS.player().credits().inc(-amount, CTYPE.MISC);
            playerBankBalance += amount;
            playerTreasury = FACTIONS.player().credits().credits();
            recordOperation(OP_DEPOSIT, amount);
        }

        void withdraw(int amount) {
            if (amount <= 0) {
                return;
            }
            amount = Math.min(amount, availableBankBalance());
            if (amount <= 0) {
                return;
            }
            playerBankBalance -= amount;
            FACTIONS.player().credits().inc(amount, CTYPE.MISC);
            playerTreasury = FACTIONS.player().credits().credits();
            recordOperation(OP_WITHDRAW, -amount);
        }

        int availableTreasury() {
            return Math.max(0, (int) Math.floor(FACTIONS.player().credits().credits()));
        }

        int availableBankBalance() {
            return Math.max(0, (int) Math.floor(playerBankBalance));
        }

        int availableLoanAmount() {
            refreshLoanCapacity();
            return Math.max(0, (int) Math.floor(maxLoanAvailable));
        }

        int maxLoanInstallmentsAllowed() {
            refreshLoanCapacity();
            return Math.max(1, maxLoanInstallments);
        }

        int availableEarlyPayment(int loan) {
            if (!loanValid(loan)) {
                return 0;
            }
            return Math.max(0, Math.min(availableTreasury(), (int) Math.ceil(requiredEarlyPaymentToSettle(loan))));
        }

        void contractLoan(int amount, int installments) {
            refreshLoanCapacity();
            if (loanCount >= MAX_LOANS) {
                return;
            }
            amount = Math.min(amount, availableLoanAmount());
            installments = CLAMP.i(installments, 1, maxLoanInstallmentsAllowed());
            if (amount <= 0 || installments <= 0) {
                return;
            }

            double worth = Math.max(playerNetWorth, 1.0);
            double finalRate = finalLoanRate(amount, installments, worth);
            double daysPerYear = Math.max(1.0, TIME.years().bitConversion(TIME.days()));
            double periodRate = Math.pow(1.0 + finalRate / 100.0, 1.0 / daysPerYear) - 1.0;
            double installment = installment(amount, periodRate, installments);
            double debt = Math.max(amount, installment * installments);

            int i = loanCount++;
            loanIds[i] = nextLoanId++;
            loanContractDay[i] = TIME.days().bitsSinceStart();
            loanInstallmentsContracted[i] = installments;
            loanInstallmentsRemaining[i] = installments;
            loanOriginalPrincipal[i] = amount;
            loanNetWorthAtContract[i] = playerNetWorth;
            loanBaseRate[i] = currentLoanRate;
            loanFinalRate[i] = finalRate;
            loanPeriodRate[i] = periodRate;
            loanContractedInstallment[i] = installment;
            loanCurrentInstallment[i] = installment;
            loanPenaltyRate[i] = contractedPenaltyRate();
            loanPrincipalRemaining[i] = amount;
            loanDebtRemaining[i] = debt;
            loanHistoryCount[i] = 0;

            FACTIONS.player().credits().inc(amount, CTYPE.MISC);
            playerTreasury = FACTIONS.player().credits().credits();
            selectedLoan = i;
            recordLoanHistory(i, LOAN_OP_CONTRACT, amount, 0, debt);
            refreshLoanCapacity();
        }

        void prepayLoan(int loan, int amount) {
            if (!loanValid(loan) || amount <= 0) {
                return;
            }
            amount = Math.min(amount, availableEarlyPayment(loan));
            if (amount <= 0) {
                return;
            }

            double requiredToSettle = requiredEarlyPaymentToSettle(loan);
            double paid = Math.min(amount, requiredToSettle);
            EarlyPaymentPreview preview = earlyPaymentPreview(loan, paid);
            double discount = preview.discount;
            double newDebt = preview.newDebt;
            double newPrincipal = preview.newPrincipal;

            FACTIONS.player().credits().inc(-paid, CTYPE.MISC);
            playerTreasury = FACTIONS.player().credits().credits();

            if (paid >= requiredToSettle || newDebt <= 0.5) {
                loanDebtRemaining[loan] = 0;
                loanPrincipalRemaining[loan] = 0;
                loanInstallmentsRemaining[loan] = 0;
                loanCurrentInstallment[loan] = 0;
                recordLoanHistory(loan, LOAN_OP_EARLY, paid, discount, 0);
                removeLoan(loan);
                return;
            }

            loanDebtRemaining[loan] = newDebt;
            loanPrincipalRemaining[loan] = newPrincipal;
            recalculateLoanInstallment(loan);
            recordLoanHistory(loan, LOAN_OP_EARLY, paid, discount, loanDebtRemaining[loan]);
            refreshLoanCapacity();
        }

        double previewEarlyPaymentDiscount(int loan, int amount) {
            if (!loanValid(loan) || amount <= 0) {
                return 0;
            }
            double paid = Math.min(amount, requiredEarlyPaymentToSettle(loan));
            return earlyPaymentPreview(loan, paid).discount;
        }

        double previewEarlyPaymentDebtAfter(int loan, int amount) {
            if (!loanValid(loan) || amount <= 0) {
                return loanValid(loan) ? loanDebtRemaining[loan] : 0;
            }
            double paid = Math.min(amount, requiredEarlyPaymentToSettle(loan));
            return earlyPaymentPreview(loan, paid).newDebt;
        }

        double requiredEarlyPaymentToSettle(int loan) {
            if (!loanValid(loan)) {
                return 0;
            }
            double debt = Math.max(0.0, loanDebtRemaining[loan]);
            double principal = Math.max(0.0, loanPrincipalRemaining[loan]);
            double interest = Math.max(0.0, debt - principal);
            double denominator = 1.0 + (interest * EARLY_PAYMENT_DISCOUNT_FACTOR / Math.max(debt, 1.0));
            double required = debt / Math.max(denominator, 1.0);
            return CLAMP.d(required, 0.0, debt);
        }

        private EarlyPaymentPreview earlyPaymentPreview(int loan, double paid) {
            double debtBefore = loanDebtRemaining[loan];
            double principalBefore = loanPrincipalRemaining[loan];
            paid = CLAMP.d(paid, 0.0, debtBefore);
            double interestRemaining = Math.max(0.0, debtBefore - principalBefore);
            double percentAmortized = paid / Math.max(debtBefore, 1.0);
            double discount = Math.min(interestRemaining,
                    interestRemaining * percentAmortized * EARLY_PAYMENT_DISCOUNT_FACTOR);
            double newPrincipal = principalBefore - Math.min(principalBefore, paid);
            double newDebt = debtBefore - paid - discount;
            newDebt = Math.max(newDebt, Math.max(0.0, newPrincipal));
            newDebt = Math.max(0.0, newDebt);
            if (paid >= requiredEarlyPaymentToSettle(loan) || newDebt <= 0.5) {
                newDebt = 0;
                newPrincipal = 0;
            }
            return new EarlyPaymentPreview(newDebt, newPrincipal, discount);
        }

        private static final class EarlyPaymentPreview {
            final double newDebt;
            final double newPrincipal;
            final double discount;

            EarlyPaymentPreview(double newDebt, double newPrincipal, double discount) {
                this.newDebt = newDebt;
                this.newPrincipal = newPrincipal;
                this.discount = discount;
            }
        }

        private void refreshLoanCapacity() {
            double outstanding = activeLoanDebt();
            double creditRatio = 0.30 + positiveStrength * 0.002 - negativeStress * 0.003
                    - economicDispersion * 0.0025 - currentLoanRate * 0.001;
            creditRatio = CLAMP.d(creditRatio, 0.05, 0.55);
            maxLoanAvailable = Math.max(0.0, playerNetWorth * creditRatio - outstanding);

            double leverage = playerNetWorth <= 0 ? 1.0 : outstanding / playerNetWorth;
            int terms = (int) Math.round(48 + positiveStrength * 0.12 - negativeStress * 0.22
                    - economicDispersion * 0.16 - currentLoanRate * 0.20 - leverage * 18.0);
            maxLoanInstallments = CLAMP.i(terms, 6, 72);
        }

        private double activeLoanDebt() {
            double debt = 0;
            for (int i = 0; i < loanCount; i++) {
                debt += Math.max(0.0, loanDebtRemaining[i]);
            }
            return debt;
        }

        private double finalLoanRate(double amount, int installments, double netWorth) {
            double leverage = amount / Math.max(netWorth, 1.0);
            double valuePremium = (Math.pow(1.0 + leverage, 3.0) - 1.0) * 5.0;
            double termPremium = Math.sqrt(installments / 12.0) * 2.0;
            return Math.max(0.0, currentLoanRate + valuePremium + termPremium);
        }

        double previewLoanFinalRate(int amount, int installments) {
            if (amount <= 0 || installments <= 0) {
                return 0;
            }
            return finalLoanRate(amount, installments, Math.max(playerNetWorth, 1.0));
        }

        double previewLoanInstallment(int amount, int installments) {
            if (amount <= 0 || installments <= 0) {
                return 0;
            }
            double daysPerYear = Math.max(1.0, TIME.years().bitConversion(TIME.days()));
            double finalRate = previewLoanFinalRate(amount, installments);
            double periodRate = Math.pow(1.0 + finalRate / 100.0, 1.0 / daysPerYear) - 1.0;
            return installment(amount, periodRate, installments);
        }

        double previewLoanTotalDue(int amount, int installments) {
            if (amount <= 0 || installments <= 0) {
                return 0;
            }
            return Math.max(amount, previewLoanInstallment(amount, installments) * installments);
        }

        double previewLoanTotalInterest(int amount, int installments) {
            return Math.max(0.0, previewLoanTotalDue(amount, installments) - amount);
        }

        private double installment(double amount, double periodRate, int installments) {
            if (installments <= 0) {
                return amount;
            }
            if (periodRate <= 0) {
                return amount / installments;
            }
            return amount * periodRate / (1.0 - Math.pow(1.0 + periodRate, -installments));
        }

        private double contractedPenaltyRate() {
            return Math.max(0.0, 0.5 + currentLoanRate * 0.03 + negativeStress * 0.05
                    + economicDispersion * 0.03);
        }

        private void processDailyLoans() {
            for (int i = loanCount - 1; i >= 0; i--) {
                processDailyLoan(i);
            }
        }

        private void processDailyLoan(int loan) {
            if (!loanValid(loan)) {
                return;
            }
            if (loanDebtRemaining[loan] <= 0.5) {
                removeLoan(loan);
                return;
            }
            if (loanInstallmentsRemaining[loan] <= 0) {
                loanInstallmentsRemaining[loan] = 1;
                recalculateLoanInstallment(loan);
            }

            double due = Math.min(loanCurrentInstallment[loan], loanDebtRemaining[loan]);
            int available = availableTreasury();
            double paid = Math.min(due, available);
            double debtBefore = loanDebtRemaining[loan];

            if (paid > 0) {
                FACTIONS.player().credits().inc(-paid, CTYPE.MISC);
                playerTreasury = FACTIONS.player().credits().credits();
                loanDebtRemaining[loan] = Math.max(0.0, loanDebtRemaining[loan] - paid);
                reducePrincipalFromPayment(loan, paid);
            }

            if (paid + 0.0001 >= due) {
                loanInstallmentsRemaining[loan] = Math.max(0, loanInstallmentsRemaining[loan] - 1);
                recordLoanHistory(loan, LOAN_OP_PAYMENT, paid, 0, loanDebtRemaining[loan]);
            } else {
                if (paid > 0) {
                    recordLoanHistory(loan, LOAN_OP_PARTIAL, paid, 0, loanDebtRemaining[loan]);
                }
                double penalty = loanDebtRemaining[loan] * (loanPenaltyRate[loan] / 100.0);
                if (penalty > 0) {
                    loanDebtRemaining[loan] += penalty;
                    recordLoanHistory(loan, LOAN_OP_PENALTY, penalty, 0, loanDebtRemaining[loan]);
                }
            }

            if (loanDebtRemaining[loan] <= 0.5 || (loanInstallmentsRemaining[loan] <= 0 && debtBefore <= due + 0.0001)) {
                removeLoan(loan);
                return;
            }
            recalculateLoanInstallment(loan);
        }

        private void reducePrincipalFromPayment(int loan, double paid) {
            double interestDue = Math.max(0.0, loanPrincipalRemaining[loan] * loanPeriodRate[loan]);
            double principalPaid = Math.max(0.0, paid - interestDue);
            loanPrincipalRemaining[loan] = Math.max(0.0, loanPrincipalRemaining[loan] - principalPaid);
            if (loanDebtRemaining[loan] < loanPrincipalRemaining[loan]) {
                loanPrincipalRemaining[loan] = loanDebtRemaining[loan];
            }
        }

        private void recalculateLoanInstallment(int loan) {
            if (!loanValid(loan)) {
                return;
            }
            if (loanDebtRemaining[loan] <= 0.5) {
                loanDebtRemaining[loan] = 0;
                loanPrincipalRemaining[loan] = 0;
                loanCurrentInstallment[loan] = 0;
                loanInstallmentsRemaining[loan] = 0;
                return;
            }
            if (loanInstallmentsRemaining[loan] <= 0) {
                loanInstallmentsRemaining[loan] = 1;
            }
            loanCurrentInstallment[loan] = loanDebtRemaining[loan] / loanInstallmentsRemaining[loan];
        }

        private boolean loanValid(int loan) {
            return loan >= 0 && loan < loanCount;
        }

        private void recordLoanHistory(int loan, int type, double amount, double discount, double balance) {
            if (!loanValid(loan)) {
                return;
            }
            for (int i = MAX_LOAN_HISTORY - 1; i > 0; i--) {
                loanHistoryTypes[loan][i] = loanHistoryTypes[loan][i - 1];
                loanHistoryDays[loan][i] = loanHistoryDays[loan][i - 1];
                loanHistoryAmounts[loan][i] = loanHistoryAmounts[loan][i - 1];
                loanHistoryDiscounts[loan][i] = loanHistoryDiscounts[loan][i - 1];
                loanHistoryBalances[loan][i] = loanHistoryBalances[loan][i - 1];
            }
            loanHistoryTypes[loan][0] = type;
            loanHistoryDays[loan][0] = TIME.days().bitsSinceStart();
            loanHistoryAmounts[loan][0] = amount;
            loanHistoryDiscounts[loan][0] = discount;
            loanHistoryBalances[loan][0] = balance;
            if (loanHistoryCount[loan] < MAX_LOAN_HISTORY) {
                loanHistoryCount[loan]++;
            }
        }

        private void removeLoan(int loan) {
            if (!loanValid(loan)) {
                return;
            }
            for (int i = loan; i < loanCount - 1; i++) {
                copyLoan(i + 1, i);
            }
            loanCount--;
            if (selectedLoan == loan) {
                selectedLoan = loanCount > 0 ? Math.min(loan, loanCount - 1) : -1;
            } else if (selectedLoan > loan) {
                selectedLoan--;
            }
            refreshLoanCapacity();
        }

        private void copyLoan(int from, int to) {
            loanIds[to] = loanIds[from];
            loanContractDay[to] = loanContractDay[from];
            loanInstallmentsContracted[to] = loanInstallmentsContracted[from];
            loanInstallmentsRemaining[to] = loanInstallmentsRemaining[from];
            loanOriginalPrincipal[to] = loanOriginalPrincipal[from];
            loanNetWorthAtContract[to] = loanNetWorthAtContract[from];
            loanBaseRate[to] = loanBaseRate[from];
            loanFinalRate[to] = loanFinalRate[from];
            loanPeriodRate[to] = loanPeriodRate[from];
            loanContractedInstallment[to] = loanContractedInstallment[from];
            loanCurrentInstallment[to] = loanCurrentInstallment[from];
            loanPenaltyRate[to] = loanPenaltyRate[from];
            loanPrincipalRemaining[to] = loanPrincipalRemaining[from];
            loanDebtRemaining[to] = loanDebtRemaining[from];
            loanHistoryCount[to] = loanHistoryCount[from];
            for (int h = 0; h < MAX_LOAN_HISTORY; h++) {
                loanHistoryTypes[to][h] = loanHistoryTypes[from][h];
                loanHistoryDays[to][h] = loanHistoryDays[from][h];
                loanHistoryAmounts[to][h] = loanHistoryAmounts[from][h];
                loanHistoryDiscounts[to][h] = loanHistoryDiscounts[from][h];
                loanHistoryBalances[to][h] = loanHistoryBalances[from][h];
            }
        }

        private void applyDailyInterest(double daysPerYear) {
            if (playerBankBalance <= 0 || currentSavingsRate <= 0 || daysPerYear <= 0) {
                return;
            }
            interestRemainder += playerBankBalance * (currentSavingsRate / 100.0) / daysPerYear;
            int payout = (int) Math.floor(interestRemainder);
            if (payout <= 0) {
                return;
            }
            interestRemainder -= payout;
            if (reinvestSavingsInterest) {
                playerBankBalance += payout;
            } else {
                FACTIONS.player().credits().inc(payout, CTYPE.MISC);
                playerTreasury = FACTIONS.player().credits().credits();
            }
            recordOperation(OP_INTEREST, payout);
        }

        private void recordOperation(int type, double amount) {
            for (int i = MAX_OPERATIONS - 1; i > 0; i--) {
                operationTypes[i] = operationTypes[i - 1];
                operationDays[i] = operationDays[i - 1];
                operationAmounts[i] = operationAmounts[i - 1];
                operationBalances[i] = operationBalances[i - 1];
            }
            operationTypes[0] = type;
            operationDays[0] = TIME.days().bitsSinceStart();
            operationAmounts[0] = amount;
            operationBalances[0] = playerBankBalance;
            if (operationCount < MAX_OPERATIONS) {
                operationCount++;
            }
        }

        private void pushHistory() {
            if (!historyInitialized) {
                historyInitialized = true;
            }
            push(savingsHistory, currentSavingsRate);
            push(loanHistory, currentLoanRate);
            push(latePenaltyHistory, latePenaltyRate);
            push(bankBalanceHistory, playerBankBalance);
            if (historySamples < HISTORY_DAYS) {
                historySamples++;
            }
        }

        private void push(double[] history, double value) {
            for (int i = 0; i < history.length - 1; i++) {
                history[i] = history[i + 1];
            }
            history[history.length - 1] = value;
        }

    }

    private static abstract class BankPanel extends GuiSection {
        static final int PANEL_W = 920;
        static final int PANEL_H = IFullView.HEIGHT - 72;

        BankPanel() {
            add(new RENDEROBJ.RenderDummy(PANEL_W, PANEL_H), 0, 0);
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            RATES.updateIfNeeded();
            super.render(r, ds);
        }
    }

    private static final class SavingsPanel extends BankPanel {
        SavingsPanel() {
            int x = 40;
            add(new SavingsSummary(840, 170), x, 0);

            GButt.ButtPanel deposit = new GButt.ButtPanel(TR.s("button.deposit")) {
                @Override
                protected void clickA() {
                    showSavingsPopup(true, this);
                }
            }.setDim(200, 38);
            deposit.hoverTitleSet(TR.s("hover.depositMoney"));
            GButt.ButtPanel withdraw = new GButt.ButtPanel(TR.s("button.withdraw")) {
                @Override
                protected void clickA() {
                    showSavingsPopup(false, this);
                }
            }.setDim(200, 38);
            withdraw.hoverTitleSet(TR.s("hover.withdrawMoney"));
            add(deposit, x + 210, 202);
            add(withdraw, x + 430, 202);
            add(new ReinvestToggle(420, 28), x + 210, 246);
            add(new OperationHistoryFrame(840, PANEL_H - 306), x, 296);
        }

        private static void showSavingsPopup(boolean deposit, CLICKABLE trigger) {
            RATES.updateIfNeeded();
            VIEW.inters().popup.show(new SavingsTransferPopup(deposit), trigger);
        }
    }

    private static final class SavingsTransferPopup extends GuiSection {
        private static final int POPUP_W = 420;
        private final boolean deposit;
        private final IntImp amount;
        private final int max;
        private final GText title = new GText(UI.FONT().H2, 80).lablify();

        SavingsTransferPopup(boolean deposit) {
            this.deposit = deposit;
            this.max = deposit ? RATES.availableTreasury() : RATES.availableBankBalance();
            this.amount = new IntImp(0, 0, Math.max(0, max));

            title.add(deposit ? TR.s("popup.depositSavings") : TR.s("popup.withdrawSavings"));
            title.adjustWidth();
            add(new RENDEROBJ.RenderDummy(POPUP_W, 1), 0, 0);
            add(title, (POPUP_W - title.width()) / 2, 0);

            add(new TransferInfo(360, 48, this), (POPUP_W - 360) / 2, 42);

            GInputInt input = new GInputInt(amount, true, true);
            GButt.ButtPanel maxButton = new GButt.ButtPanel(TR.s("button.max")) {
                @Override
                protected void clickA() {
                    amount.set(max);
                }
            }.setDim(92, 32);
            maxButton.hoverTitleSet(TR.s("hover.useMaxValue"));
            GuiSection inputRow = new GuiSection();
            inputRow.add(input);
            inputRow.addRightC(12, maxButton);
            add(inputRow, (POPUP_W - inputRow.body().width()) / 2, 104);

            GuiSection buttons = new GuiSection();
            GButt.ButtPanel ok = new GButt.ButtPanel(UI.icons().m.ok) {
                @Override
                protected void clickA() {
                    if (amount.get() <= 0) {
                        return;
                    }
                    if (deposit) {
                        RATES.deposit(amount.get());
                    } else {
                        RATES.withdraw(amount.get());
                    }
                    VIEW.inters().popup.close();
                }

                @Override
                protected void renAction() {
                    activeSet(amount.get() > 0 && amount.get() <= max);
                }
            };
            ok.hoverTitleSet(TR.s("hover.confirm"));
            GButt.ButtPanel cancel = new GButt.ButtPanel(UI.icons().m.cancel) {
                @Override
                protected void clickA() {
                    VIEW.inters().popup.close();
                }
            };
            cancel.hoverTitleSet(TR.s("hover.cancel"));
            buttons.add(ok);
            buttons.addRightC(0, cancel);
            add(buttons, (POPUP_W - buttons.body().width()) / 2, 152);

            pad(16, 16);
        }
    }

    private static final class TransferInfo extends RENDEROBJ.RenderImp {
        private final SavingsTransferPopup popup;
        private final GText text = new GText(UI.FONT().S, 160);

        TransferInfo(int w, int h, SavingsTransferPopup popup) {
            super(w, h);
            this.popup = popup;
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            text.clear().normalify2();
            if (popup.deposit) {
                text.add(TR.s("label.availableTreasury")).add(' ');
            } else {
                text.add(TR.s("label.availableSavings")).add(' ');
            }
            GFORMAT.i(text, popup.max);
            text.adjustWidth();
            text.render(r, body().cX() - text.width() / 2, body().y1());
            text.clear().normalify2().add(TR.s("label.value"));
            text.adjustWidth();
            text.render(r, body().cX() - text.width() / 2, body().y1() + 24);
        }
    }

    private static final class ReinvestToggle extends GuiSection {
        private final GText label = new GText(UI.FONT().S, 180);

        ReinvestToggle(int w, int h) {
            add(new RENDEROBJ.RenderDummy(w, h), 0, 0);
            GButt.Checkbox check = new GButt.Checkbox() {
                @Override
                protected void clickA() {
                    RATES.reinvestSavingsInterest = !RATES.reinvestSavingsInterest;
                }

                @Override
                protected void renAction() {
                    selectedSet(RATES.reinvestSavingsInterest);
                }
            };
            check.hoverTitleSet(TR.s("hover.reinvestTitle"));
            check.hoverInfoSet(TR.s("hover.reinvestInfo"));
            add(check, 0, (h - check.body().height()) / 2);
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            super.render(r, ds);
            label.clear().normalify2().add(TR.s("label.reinvest"));
            label.render(r, body().x1() + 30, body().y1() + 5);
        }
    }

    private static final class LoansPanel extends BankPanel {
        LoansPanel() {
            int x = 40;
            add(new LoanSummary(840, 150), x, 0);
            add(new LoanListFrame(840, 170), x, 166);

            GButt.ButtPanel loan = new GButt.ButtPanel(TR.s("button.contractLoan")) {
                @Override
                protected void clickA() {
                    RATES.updateIfNeeded();
                    VIEW.inters().popup.show(new LoanContractPopup(), this);
                }
            }.setDim(310, 40);
            loan.hoverTitleSet(TR.s("hover.contractLoan"));
            add(loan, x + 100, 352);

            GButt.ButtPanel prepay = new GButt.ButtPanel(TR.s("button.prepay")) {
                @Override
                protected void clickA() {
                    if (RATES.selectedLoan >= 0 && RATES.selectedLoan < RATES.loanCount) {
                        VIEW.inters().popup.show(new LoanPrepayPopup(RATES.selectedLoan), this);
                    }
                }

                @Override
                protected void renAction() {
                    activeSet(RATES.selectedLoan >= 0 && RATES.selectedLoan < RATES.loanCount);
                }
            }.setDim(310, 38);
            prepay.hoverTitleSet(TR.s("hover.amortizeLoan"));
            add(prepay, x + 430, 353);
            add(new LoanDetailsFrame(840, PANEL_H - 416), x, 404);
        }
    }

    private static final class LoanContractPopup extends GuiSection {
        private static final int POPUP_W = 560;
        private final int maxAmount;
        private final int maxInstallments;
        private final IntImp amount;
        private final IntImp installments;
        private final GText title = new GText(UI.FONT().H2, 120).lablify();

        LoanContractPopup() {
            RATES.updateIfNeeded();
            maxAmount = RATES.availableLoanAmount();
            maxInstallments = RATES.maxLoanInstallmentsAllowed();
            amount = new IntImp(0, 0, Math.max(0, maxAmount));
            installments = new IntImp(Math.min(12, maxInstallments), 1, Math.max(1, maxInstallments));

            title.add(TR.s("popup.contractLoan"));
            title.adjustWidth();
            add(new RENDEROBJ.RenderDummy(POPUP_W, 1), 0, 0);
            add(title, (POPUP_W - title.width()) / 2, 0);
            add(new LoanContractInfo(500, 154, this), (POPUP_W - 500) / 2, 42);

            GuiSection amountRow = inputRow(TR.s("label.value"), amount, maxAmount);
            add(amountRow, (POPUP_W - amountRow.body().width()) / 2, 214);
            GuiSection installmentRow = inputRow(TR.s("label.installments"), installments, maxInstallments);
            add(installmentRow, (POPUP_W - installmentRow.body().width()) / 2, 264);

            GuiSection buttons = new GuiSection();
            GButt.ButtPanel ok = new GButt.ButtPanel(UI.icons().m.ok) {
                @Override
                protected void clickA() {
                    if (amount.get() > 0 && installments.get() > 0 && RATES.loanCount < BankRates.MAX_LOANS) {
                        RATES.contractLoan(amount.get(), installments.get());
                        VIEW.inters().popup.close();
                    }
                }

                @Override
                protected void renAction() {
                    activeSet(amount.get() > 0 && amount.get() <= maxAmount && installments.get() > 0
                            && installments.get() <= maxInstallments && RATES.loanCount < BankRates.MAX_LOANS);
                }
            };
            ok.hoverTitleSet(TR.s("hover.confirm"));
            GButt.ButtPanel cancel = new GButt.ButtPanel(UI.icons().m.cancel) {
                @Override
                protected void clickA() {
                    VIEW.inters().popup.close();
                }
            };
            cancel.hoverTitleSet(TR.s("hover.cancel"));
            buttons.add(ok);
            buttons.addRightC(0, cancel);
            add(buttons, (POPUP_W - buttons.body().width()) / 2, 324);
            pad(16, 16);
        }

        private GuiSection inputRow(CharSequence label, IntImp target, int max) {
            GuiSection row = new GuiSection();
            row.add(new StaticLabel(label, 120, 32), 0, 0);
            GInputInt input = new GInputInt(target, true, true);
            row.add(input, 130, 0);
            GButt.ButtPanel maxButton = new GButt.ButtPanel(TR.s("button.max")) {
                @Override
                protected void clickA() {
                    target.set(max);
                }
            }.setDim(84, 32);
            maxButton.hoverTitleSet(TR.s("hover.useMax"));
            row.add(maxButton, input.body().x2() + 12, 0);
            return row;
        }
    }

    private static final class LoanContractInfo extends RENDEROBJ.RenderImp {
        private final LoanContractPopup popup;
        private final GText text = new GText(UI.FONT().S, 220);

        LoanContractInfo(int w, int h, LoanContractPopup popup) {
            super(w, h);
            this.popup = popup;
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            int amount = popup.amount.get();
            int installments = popup.installments.get();
            double finalRate = RATES.previewLoanFinalRate(amount, installments);
            double installment = RATES.previewLoanInstallment(amount, installments);
            double totalDue = RATES.previewLoanTotalDue(amount, installments);
            double totalInterest = RATES.previewLoanTotalInterest(amount, installments);
            int y = body().y1();
            text.clear().normalify2().add(TR.s("label.maxAvailable")).add(' ');
            appendMoney(text, popup.maxAmount, false);
            centerText(r, text, body().cX(), y);
            y += 22;
            text.clear().normalify2().add(TR.s("label.maxDailyInstallments")).add(' ').add(popup.maxInstallments);
            centerText(r, text, body().cX(), y);
            y += 26;
            text.clear().lablifySub().add(TR.s("label.contractPreview"));
            centerText(r, text, body().cX(), y);
            y += 24;
            text.clear().normalify2().add(TR.s("label.estimatedTotalRate")).add(' ');
            appendPercent(text, finalRate);
            centerText(r, text, body().cX(), y);
            y += 22;
            text.clear().normalify2().add(TR.s("label.estimatedDailyInstallment")).add(' ');
            appendMoney(text, installment, false);
            centerText(r, text, body().cX(), y);
            y += 22;
            text.clear().normalify2().add(TR.s("label.totalDue")).add(' ');
            appendMoney(text, totalDue, false);
            centerText(r, text, body().cX(), y);
            y += 22;
            text.clear().normalify2().add(TR.s("label.estimatedTotalInterest")).add(' ');
            appendMoney(text, totalInterest, false);
            centerText(r, text, body().cX(), y);
        }
    }

    private static final class LoanPrepayPopup extends GuiSection {
        private static final int POPUP_W = 460;
        private final int loan;
        private final int maxAmount;
        private final IntImp amount;
        private final GText title = new GText(UI.FONT().H2, 120).lablify();

        LoanPrepayPopup(int loan) {
            this.loan = loan;
            this.maxAmount = RATES.availableEarlyPayment(loan);
            this.amount = new IntImp(0, 0, Math.max(0, maxAmount));

            title.add(TR.s("popup.prepay"));
            title.adjustWidth();
            add(new RENDEROBJ.RenderDummy(POPUP_W, 1), 0, 0);
            add(title, (POPUP_W - title.width()) / 2, 0);
            add(new LoanPrepayInfo(400, 128, this), (POPUP_W - 400) / 2, 42);

            GuiSection row = new GuiSection();
            row.add(new StaticLabel(TR.s("label.value"), 90, 32), 0, 0);
            GInputInt input = new GInputInt(amount, true, true);
            row.add(input, 100, 0);
            GButt.ButtPanel maxButton = new GButt.ButtPanel(TR.s("button.max")) {
                @Override
                protected void clickA() {
                    amount.set(maxAmount);
                }
            }.setDim(84, 32);
            row.add(maxButton, input.body().x2() + 12, 0);
            add(row, (POPUP_W - row.body().width()) / 2, 188);

            GuiSection buttons = new GuiSection();
            GButt.ButtPanel ok = new GButt.ButtPanel(UI.icons().m.ok) {
                @Override
                protected void clickA() {
                    if (amount.get() > 0 && amount.get() <= maxAmount) {
                        RATES.prepayLoan(LoanPrepayPopup.this.loan, amount.get());
                        VIEW.inters().popup.close();
                    }
                }

                @Override
                protected void renAction() {
                    activeSet(amount.get() > 0 && amount.get() <= maxAmount);
                }
            };
            ok.hoverTitleSet(TR.s("hover.confirm"));
            GButt.ButtPanel cancel = new GButt.ButtPanel(UI.icons().m.cancel) {
                @Override
                protected void clickA() {
                    VIEW.inters().popup.close();
                }
            };
            cancel.hoverTitleSet(TR.s("hover.cancel"));
            buttons.add(ok);
            buttons.addRightC(0, cancel);
            add(buttons, (POPUP_W - buttons.body().width()) / 2, 244);
            pad(16, 16);
        }
    }

    private static final class LoanPrepayInfo extends RENDEROBJ.RenderImp {
        private final LoanPrepayPopup popup;
        private final GText text = new GText(UI.FONT().S, 220);

        LoanPrepayInfo(int w, int h, LoanPrepayPopup popup) {
            super(w, h);
            this.popup = popup;
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            int y = body().y1();
            int amount = popup.amount.get();
            double discount = RATES.previewEarlyPaymentDiscount(popup.loan, amount);
            double debtAfter = RATES.previewEarlyPaymentDebtAfter(popup.loan, amount);
            text.clear().normalify2().add(TR.s("label.availableTreasury")).add(' ');
            appendMoney(text, RATES.availableTreasury(), false);
            centerText(r, text, body().cX(), y);
            y += 22;
            text.clear().normalify2().add(TR.s("label.dueInContract")).add(' ');
            if (popup.loan >= 0 && popup.loan < RATES.loanCount) {
                appendMoney(text, RATES.loanDebtRemaining[popup.loan], false);
            } else {
                appendMoney(text, 0, false);
            }
            centerText(r, text, body().cX(), y);
            y += 22;
            text.clear().normalify2().add(TR.s("label.maxPrepay")).add(' ');
            appendMoney(text, popup.maxAmount, false);
            centerText(r, text, body().cX(), y);
            y += 24;
            text.clear().lablifySub().add(TR.s("label.prepayPreview"));
            centerText(r, text, body().cX(), y);
            y += 22;
            text.clear().normalify2().add(TR.s("label.estimatedDiscount")).add(' ');
            appendMoney(text, discount, false);
            centerText(r, text, body().cX(), y);
            y += 22;
            text.clear().normalify2().add(TR.s("label.debtAfterPayment")).add(' ');
            appendMoney(text, debtAfter, false);
            centerText(r, text, body().cX(), y);
        }
    }

    private static final class DataPanel extends BankPanel {
        DataPanel() {
            int x = 40;
            int y = 0;
            int chartH = 104;
            int gap = 18;
            add(new TextLabel(TR.s("data.savingsRateHistory"), 840), x, y);
            add(new HistoryChart(840, chartH, RATES.savingsHistory, COLOR.YELLOW100, TR.s("chart.savingsRate"), true,
                    false), x, y + 26);
            y += chartH + 26 + gap;
            add(new TextLabel(TR.s("data.bankBalanceHistory"), 840), x, y);
            add(new HistoryChart(840, chartH, RATES.bankBalanceHistory, COLOR.GREEN100, TR.s("chart.bankBalance"),
                    false, false), x, y + 26);
            y += chartH + 26 + gap;
            add(new TextLabel(TR.s("data.loanRateHistory"), 840), x, y);
            add(new HistoryChart(840, chartH, RATES.loanHistory, COLOR.RED100, TR.s("chart.loanRate"), true, false), x,
                    y + 26);
            y += chartH + 26 + gap;
            add(new TextLabel(TR.s("data.loanPenaltyHistory"), 840), x, y);
            add(new HistoryChart(840, chartH, RATES.latePenaltyHistory, COLOR.ORANGE100, TR.s("chart.loanPenalty"),
                    true, false), x, y + 26);
        }
    }

    private static final class SavingsSummary extends RENDEROBJ.RenderImp {
        private final GText header = new GText(UI.FONT().H2, 80).lablify();
        private final GText big = new GText(UI.FONT().M, 120);
        private final GText small = new GText(UI.FONT().S, 160);

        SavingsSummary(int w, int h) {
            super(w, h);
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            card(r, body().x1(), body().x2(), body().y1(), body().y2());
            int x = body().x1() + 16;
            int y = body().y1() + 12;
            header.clear().add(TR.s("savings.header"));
            header.render(r, x, y);
            y += 34;
            big.clear().lablify().add(TR.s("savings.annualRate")).add(' ');
            appendPercent(big, RATES.currentSavingsRate);
            big.render(r, x, y);
            y += 46;
            big.clear().lablify().add(TR.s("savings.currentBalance")).add(' ');
            appendMoney(big, RATES.playerBankBalance, false);
            big.render(r, x, y);
            y += 34;
            small.clear().normalify2();
            if (RATES.error != null) {
                small.color(COLOR.RED100).add(RATES.error);
            } else if (RATES.reinvestSavingsInterest) {
                small.add(TR.s("savings.interestReinvested"));
            } else {
                small.add(TR.s("savings.interestTreasury"));
            }
            small.render(r, x, y);
        }
    }

    private static final class OperationHistoryFrame extends CLICKABLE.ClickableAbs {
        private static final int ROW_H = 24;
        private int scroll;
        private final GText header = new GText(UI.FONT().H2, 120).lablify();
        private final GText text = new GText(UI.FONT().S, 220);

        OperationHistoryFrame(int w, int h) {
            super(w, h);
        }

        @Override
        protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
            card(r, body().x1(), body().x2(), body().y1(), body().y2());
            if (isHovered) {
                float wheel = MButt.clearWheelSpin();
                if (wheel > 0) {
                    scroll--;
                } else if (wheel < 0) {
                    scroll++;
                }
            }

            int rows = Math.max(1, (body().height() - 56) / ROW_H);
            int maxScroll = Math.max(0, RATES.operationCount - rows);
            scroll = CLAMP.i(scroll, 0, maxScroll);

            int x = body().x1() + 16;
            int y = body().y1() + 12;
            header.clear().add(TR.s("operations.header"));
            header.render(r, x, y);
            y += 36;

            if (RATES.operationCount == 0) {
                text.clear().normalify2().add(TR.s("operations.none"));
                text.render(r, x, y);
                return;
            }

            int end = Math.min(RATES.operationCount, scroll + rows);
            for (int i = scroll; i < end; i++) {
                COLOR color = operationColor(RATES.operationTypes[i]);
                text.clear().color(color);
                text.add(operationName(RATES.operationTypes[i]));
                text.render(r, x, y);

                text.clear().color(color);
                appendMoney(text, RATES.operationAmounts[i], true);
                text.render(r, x + 220, y);

                text.clear().normalify2().add(TR.s("label.balance")).add(' ');
                appendMoney(text, RATES.operationBalances[i], false);
                text.render(r, x + 360, y);

                text.clear().normalify2();
                appendAge(text, RATES.operationDays[i]);
                text.render(r, x + 610, y);
                y += ROW_H;
            }
        }

        private COLOR operationColor(int type) {
            if (type == BankRates.OP_WITHDRAW) {
                return COLOR.RED100;
            }
            if (type == BankRates.OP_INTEREST) {
                return COLOR.NYAN100;
            }
            return COLOR.GREEN100;
        }

        private CharSequence operationName(int type) {
            if (type == BankRates.OP_WITHDRAW) {
                return TR.s("operation.withdraw");
            }
            if (type == BankRates.OP_INTEREST) {
                return TR.s("operation.interest");
            }
            return TR.s("operation.deposit");
        }
    }

    private static final class StaticLabel extends RENDEROBJ.RenderImp {
        private final CharSequence label;
        private final GText text = new GText(UI.FONT().S, 100).normalify2();

        StaticLabel(CharSequence label, int w, int h) {
            super(w, h);
            this.label = label;
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            text.clear().normalify2().add(label);
            text.adjustWidth();
            text.render(r, body().x2() - text.width(), body().y1() + (body().height() - text.height()) / 2);
        }
    }

    private static final class LoanListFrame extends CLICKABLE.ClickableAbs {
        private static final int ROW_H = 28;
        private int hoveredLoan = -1;
        private int scroll;
        private final GText header = new GText(UI.FONT().H2, 140).lablify();
        private final GText text = new GText(UI.FONT().S, 220);

        LoanListFrame(int w, int h) {
            super(w, h);
        }

        @Override
        public boolean hover(COORDINATE mCoo) {
            hoveredLoan = -1;
            boolean ret = super.hover(mCoo);
            if (ret) {
                int localY = mCoo.y() - (body().y1() + 48);
                if (localY >= 0) {
                    int row = localY / ROW_H;
                    int loan = scroll + row;
                    if (loan >= 0 && loan < RATES.loanCount) {
                        hoveredLoan = loan;
                    }
                }
            }
            return ret;
        }

        @Override
        protected void clickA() {
            if (hoveredLoan >= 0 && hoveredLoan < RATES.loanCount) {
                RATES.selectedLoan = hoveredLoan;
            }
        }

        @Override
        protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
            card(r, body().x1(), body().x2(), body().y1(), body().y2());
            if (isHovered) {
                float wheel = MButt.clearWheelSpin();
                if (wheel > 0) {
                    scroll--;
                } else if (wheel < 0) {
                    scroll++;
                }
            }

            int rows = Math.max(1, (body().height() - 56) / ROW_H);
            scroll = CLAMP.i(scroll, 0, Math.max(0, RATES.loanCount - rows));

            int x = body().x1() + 16;
            int y = body().y1() + 12;
            header.clear().add(TR.s("loans.contracted"));
            header.render(r, x, y);
            y += 36;

            if (RATES.loanCount == 0) {
                text.clear().normalify2().add(TR.s("loans.none"));
                text.render(r, x, y);
                return;
            }

            int end = Math.min(RATES.loanCount, scroll + rows);
            for (int i = scroll; i < end; i++) {
                if (i == RATES.selectedLoan) {
                    COLOR.WHITE25.render(r, x - 6, body().x2() - 16, y - 2, y + ROW_H - 2);
                } else if (i == hoveredLoan) {
                    COLOR.WHITE15.render(r, x - 6, body().x2() - 16, y - 2, y + ROW_H - 2);
                }

                text.clear().lablifySub().add(TR.s("label.contractNumber")).add(RATES.loanIds[i]);
                text.render(r, x, y);

                text.clear().normalify2().add(TR.s("label.due")).add(' ');
                appendMoney(text, RATES.loanDebtRemaining[i], false);
                text.render(r, x + 180, y);

                text.clear().normalify2().add(TR.s("label.installment")).add(' ');
                appendMoney(text, RATES.loanCurrentInstallment[i], false);
                text.render(r, x + 380, y);

                text.clear().normalify2().add(TR.s("label.remaining")).add(' ').add(RATES.loanInstallmentsRemaining[i]);
                text.render(r, x + 590, y);
                y += ROW_H;
            }
        }
    }

    private static final class LoanDetailsFrame extends CLICKABLE.ClickableAbs {
        private static final int ROW_H = 23;
        private int scroll;
        private final GText header = new GText(UI.FONT().H2, 140).lablify();
        private final GText text = new GText(UI.FONT().S, 260);

        LoanDetailsFrame(int w, int h) {
            super(w, h);
        }

        @Override
        protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
            card(r, body().x1(), body().x2(), body().y1(), body().y2());
            int loan = RATES.selectedLoan;
            int x = body().x1() + 16;
            int y = body().y1() + 10;
            header.clear().add(TR.s("loanDetails.header"));
            header.render(r, x, y);
            y += 30;

            if (loan < 0 || loan >= RATES.loanCount) {
                text.clear().normalify2().add(TR.s("loanDetails.selectLoan"));
                text.render(r, x, y);
                return;
            }

            renderLoanStat(r, x, y, TR.s("loanDetails.finalRate"), RATES.loanFinalRate[loan], true);
            renderLoanMoney(r, x + 420, y, TR.s("loanDetails.borrowed"), RATES.loanOriginalPrincipal[loan]);
            y += 20;
            renderLoanStat(r, x, y, TR.s("loanDetails.latePenalty"), RATES.loanPenaltyRate[loan], true);
            renderLoanMoney(r, x + 420, y, TR.s("loanDetails.currentDue"), RATES.loanDebtRemaining[loan]);
            y += 20;
            double contractedInterest = RATES.loanContractedInstallment[loan] * RATES.loanInstallmentsContracted[loan]
                    - RATES.loanOriginalPrincipal[loan];
            renderLoanMoney(r, x, y, TR.s("loanDetails.contractedInterest"), Math.max(0.0, contractedInterest));
            renderLoanMoney(r, x + 420, y, TR.s("loanDetails.currentInstallment"), RATES.loanCurrentInstallment[loan]);
            y += 20;
            text.clear().normalify2().add(TR.s("loanDetails.remainingInstallments")).add(' ')
                    .add(RATES.loanInstallmentsRemaining[loan]).add('/')
                    .add(RATES.loanInstallmentsContracted[loan]);
            text.render(r, x, y);
            y += 22;

            GCOLOR.UI().border().render(r, x, body().x2() - 16, y, y + 1);
            y += 6;
            text.clear().lablifySub().add(TR.s("loanDetails.contractHistory"));
            text.render(r, x, y);
            y += 20;

            if (isHovered) {
                float wheel = MButt.clearWheelSpin();
                if (wheel > 0) {
                    scroll--;
                } else if (wheel < 0) {
                    scroll++;
                }
            }

            int rows = Math.max(1, (body().y2() - y - 8) / ROW_H);
            int maxScroll = Math.max(0, RATES.loanHistoryCount[loan] - rows);
            scroll = CLAMP.i(scroll, 0, maxScroll);

            if (RATES.loanHistoryCount[loan] == 0) {
                text.clear().normalify2().add(TR.s("loanDetails.noEntries"));
                text.render(r, x, y);
                return;
            }

            int end = Math.min(RATES.loanHistoryCount[loan], scroll + rows);
            for (int i = scroll; i < end; i++) {
                int type = RATES.loanHistoryTypes[loan][i];
                COLOR color = loanHistoryColor(type);
                text.clear().color(color).add(loanHistoryName(type));
                text.render(r, x, y);

                text.clear().color(color);
                appendMoney(text, loanHistoryDisplayAmount(loan, i), true);
                if (type == BankRates.LOAN_OP_EARLY && RATES.loanHistoryDiscounts[loan][i] > 0) {
                    text.add(" (").add(TR.s("label.discountShort")).add(' ');
                    appendMoney(text, RATES.loanHistoryDiscounts[loan][i], false);
                    text.add(')');
                }
                text.render(r, x + 190, y);

                text.clear().normalify2().add(TR.s("label.balance")).add(' ');
                appendMoney(text, RATES.loanHistoryBalances[loan][i], false);
                text.render(r, x + 460, y);

                text.clear().normalify2();
                appendAge(text, RATES.loanHistoryDays[loan][i]);
                text.render(r, x + 650, y);
                y += ROW_H;
            }
        }

        private void renderLoanStat(SPRITE_RENDERER r, int x, int y, CharSequence label, double value, boolean percent) {
            text.clear().normalify2().add(label).add(' ');
            if (percent) {
                appendPercent(text, value);
            } else {
                appendNumber(text, value, false);
            }
            text.render(r, x, y);
        }

        private void renderLoanMoney(SPRITE_RENDERER r, int x, int y, CharSequence label, double value) {
            text.clear().normalify2().add(label).add(' ');
            appendMoney(text, value, false);
            text.render(r, x, y);
        }

        private COLOR loanHistoryColor(int type) {
            if (type == BankRates.LOAN_OP_PENALTY) {
                return COLOR.RED100;
            }
            if (type == BankRates.LOAN_OP_EARLY) {
                return COLOR.NYAN100;
            }
            if (type == BankRates.LOAN_OP_CONTRACT) {
                return COLOR.YELLOW100;
            }
            if (type == BankRates.LOAN_OP_PARTIAL) {
                return COLOR.ORANGE100;
            }
            return COLOR.GREEN100;
        }

        private CharSequence loanHistoryName(int type) {
            if (type == BankRates.LOAN_OP_CONTRACT) {
                return TR.s("loanHistory.contract");
            }
            if (type == BankRates.LOAN_OP_PARTIAL) {
                return TR.s("loanHistory.partial");
            }
            if (type == BankRates.LOAN_OP_PENALTY) {
                return TR.s("loanHistory.penalty");
            }
            if (type == BankRates.LOAN_OP_EARLY) {
                return TR.s("loanHistory.early");
            }
            return TR.s("loanHistory.payment");
        }

        private double loanHistoryDisplayAmount(int loan, int history) {
            int type = RATES.loanHistoryTypes[loan][history];
            double amount = RATES.loanHistoryAmounts[loan][history];
            if (type == BankRates.LOAN_OP_PAYMENT || type == BankRates.LOAN_OP_PARTIAL
                    || type == BankRates.LOAN_OP_EARLY) {
                return -amount;
            }
            return amount;
        }
    }

    private static final class LoanSummary extends RENDEROBJ.RenderImp {
        private final GText header = new GText(UI.FONT().H2, 80).lablify();
        private final GText big = new GText(UI.FONT().M, 140);
        private final GText small = new GText(UI.FONT().S, 180);

        LoanSummary(int w, int h) {
            super(w, h);
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            card(r, body().x1(), body().x2(), body().y1(), body().y2());
            int x = body().x1() + 16;
            int y = body().y1() + 12;
            header.clear().add(TR.s("loans.header"));
            header.render(r, x, y);
            y += 34;
            big.clear().lablify().add(TR.s("loans.annualRate")).add(' ');
            appendPercent(big, RATES.currentLoanRate);
            big.render(r, x, y);
            y += 30;
            if (RATES.error != null) {
                small.clear().normalify2().color(COLOR.RED100).add(RATES.error);
                small.render(r, x, y);
                return;
            }
            small.clear().normalify2().add(TR.s("loans.latePenalty")).add(' ');
            appendPercent(small, RATES.latePenaltyRate);
            small.render(r, x, y);
            y += 26;
            small.clear().normalify2().add(TR.s("label.maxAvailable")).add(' ');
            appendMoney(small, RATES.availableLoanAmount(), false);
            small.render(r, x, y);
            y += 24;
            small.clear().normalify2().add(TR.s("loans.maxInstallments")).add(' ')
                    .add(RATES.maxLoanInstallmentsAllowed());
            small.render(r, x, y);
        }
    }

    private static final class EmptyLoansFrame extends RENDEROBJ.RenderImp {
        private final GText text = new GText(UI.FONT().H2, 120).lablifySub();
        private final GText small = new GText(UI.FONT().S, 180);

        EmptyLoansFrame(int w, int h) {
            super(w, h);
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            card(r, body().x1(), body().x2(), body().y1(), body().y2());
            int x = body().x1() + 16;
            int y = body().y1() + 36;
            text.clear().add(TR.s("loans.noneTitle"));
            text.render(r, x, y);
            y += 34;
            small.clear().normalify2().add(TR.s("loans.futureContracts"));
            small.render(r, x, y);
        }
    }

    private static final class TextLabel extends RENDEROBJ.RenderImp {
        private final CharSequence text;
        private final GText label = new GText(UI.FONT().S, 120).lablifySub();

        TextLabel(CharSequence text, int w) {
            super(w, 20);
            this.text = text;
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            label.clear().add(text);
            label.render(r, body().x1(), body().y1());
        }
    }

    private static final class HistoryChart extends GStaples {
        private final double[] values;
        private final COLOR color;
        private final CharSequence title;
        private final boolean percent;

        HistoryChart(int w, int h, double[] values, COLOR color, CharSequence title, boolean percent, boolean negative) {
            super(values.length, negative);
            this.values = values;
            this.color = color;
            this.title = title;
            this.percent = percent;
            body().setDim(w, h);
            normalizePlus(true);
        }

        @Override
        protected double getValue(int stapleI) {
            if (!hasData(stapleI)) {
                return 0;
            }
            return values[stapleI];
        }

        @Override
        protected void setColor(ColorImp c, int stapleI, double value) {
            if (!hasData(stapleI)) {
                c.set(GCOLOR.UI().bg());
                return;
            }
            c.set(color);
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
            int back = values.length - stapleI - 1;
            t.lablify();
            DicTime.setAgo(t, back * TIME.secondsPerDay());
            box.add(t);
            box.NL(4);
            t = box.text();
            if (percent) {
                appendPercent(t, values[stapleI]);
            } else {
                appendMoney(t, values[stapleI], true);
            }
            box.add(t);
        }

        private boolean hasData(int stapleI) {
            int firstValid = values.length - RATES.historySamples;
            return RATES.historySamples > 0 && stapleI >= firstValid;
        }
    }

    private static void card(SPRITE_RENDERER r, int x1, int x2, int y1, int y2) {
        GCOLOR.UI().border().render(r, x1, x2, y1, y2);
        GCOLOR.UI().bg().render(r, x1 + 1, x2 - 1, y1 + 1, y2 - 1);
        COLOR.WHITE15.render(r, x1 + 2, x2 - 2, y1 + 2, y2 - 2);
    }

    private static void appendAge(GText text, int day) {
        int back = Math.max(0, TIME.days().bitsSinceStart() - day);
        DicTime.setAgo(text, back * TIME.secondsPerDay());
    }

    private static void centerText(SPRITE_RENDERER r, GText text, int cx, int y) {
        text.adjustWidth();
        text.render(r, cx - text.width() / 2, y);
    }

    private static void appendPercent(GText text, double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded >= 0) {
            text.add('+');
        }
        text.add(rounded, 2).add('%');
    }

    private static void appendNumber(GText text, double value, boolean signed) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (signed && rounded >= 0) {
            text.add('+');
        }
        text.add(rounded);
    }

    private static void appendMoney(GText text, double value, boolean signed) {
        long rounded = Math.round(value);
        if (signed && rounded >= 0) {
            text.add('+');
        }
        text.add(rounded);
    }
}
