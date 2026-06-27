# Syxian Banking

A simple banking system mod for [Songs of Syx](https://store.steampowered.com/app/1358140/Songs_of_Syx/) that adds a bank tab to the settlement management interface.

## What it does

- Deposit and withdraw denars into a savings account
- Take loans with automatic daily installments
- Prepay loans early with an interest discount
- View operation history and rate charts for the last few days

Interest rates are dynamic, calculated from the world economy — NPC kingdom wealth, economic stress and disparity between factions.

## Installation

1. Copy the `Syxian Banking` folder to `%APPDATA%\songsofsyx\mods\`
2. Enable the mod in the game launcher

## Code structure

```
src/syxianbanking/
├── SyxianBanking.java               Entry point (SCRIPT); injects the bank button into the top bar
├── TR.java                          i18n; loads .properties files and resolves translation keys
│
├── domain/
│   └── Loan.java                    Loan value object — holds all fields of a single active contract
│
├── banking/
│   ├── BankConstants.java           All numeric constants and binary save format markers
│   ├── BankState.java               Daily settlement orchestrator; exposes singleton INSTANCE
│   ├── RateCalculator.java          3-pass NPC scan → dynamic market interest rates
│   ├── SavingsAccount.java          Balance, deposits, withdrawals, daily interest and operation log
│   ├── LoanManager.java             Loan origination, daily payments, early repayment, credit capacity
│   ├── BankSerializer.java          Binary save/load with V1 + V2 format and backward compatibility
│   └── Sanitize.java                Package-private NaN/Infinity/overflow guards
│
└── ui/
    ├── BankPanel.java               Abstract base; drives updateIfNeeded() on every render frame
    ├── BankingView.java             Main window with Savings / Loans tab switcher
    ├── HistoryChart.java            Rolling bar chart for rate and balance history
    ├── UiUtils.java                 Shared rendering helpers (card borders, money/percent formatting)
    ├── StaticLabel.java             Right-aligned label for popup input rows
    ├── TextLabel.java               Left-aligned sub-title for chart headings
    │
    ├── savings/
    │   ├── SavingsPanel.java        Savings tab layout
    │   ├── SavingsSummary.java      Rate + balance summary card
    │   ├── SavingsTransferPopup.java  Deposit / withdraw popup
    │   ├── TransferInfo.java        Available amount display inside the popup
    │   ├── ReinvestToggle.java      Compound vs simple interest checkbox
    │   └── OperationHistoryFrame.java  Scrollable operation log
    │
    └── loans/
        ├── LoansPanel.java          Loans tab layout
        ├── LoanSummary.java         Rate + credit summary card
        ├── LoanListFrame.java       Scrollable list of active loans
        ├── LoanDetailsFrame.java    Stats + event history for the selected loan
        ├── EmptyLoansFrame.java     Placeholder card when no loans are active
        ├── LoanContractPopup.java   New loan popup with live rate/installment preview
        ├── LoanContractInfo.java    Preview panel inside the contract popup
        ├── LoanPrepayPopup.java     Early repayment popup with discount preview
        └── LoanPrepayInfo.java      Preview panel inside the prepay popup
```

## Compatibility

- Songs of Syx **v71**
- Mod version: **0.2.0**
