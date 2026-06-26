# Syxian Banking

A banking and financial systems mod for [Songs of Syx](https://store.steampowered.com/app/1358140/Songs_of_Syx/).

Adds a **Banking** tab to the settlement management interface with three sections:

## Features

### Savings
- Deposit and withdraw denars from a bank account
- Earn daily interest based on the world economy
- Option to reinvest interest back into the bank balance or receive it in the treasury
- Full operation history with scrollable log

### Loans
- Contract loans based on your net worth and current economic conditions
- Dynamic interest rates influenced by NPC kingdom wealth, economic stress and dispersion
- Daily installment payments deducted automatically from treasury
- Late payment penalties
- Prepay any loan early with an interest discount
- Up to 16 simultaneous active contracts

### Data
- Historical charts (last 48 days) for:
  - Savings rate
  - Bank balance
  - Loan rate
  - Late penalty rate

## Economy System

Rates are calculated daily from active NPC kingdom data:
- **Savings rate**: base 2% annual + stress/dispersion modifiers
- **Loan rate**: savings rate + 3% spread + additional risk modifiers
- **Loan capacity**: up to 30–55% of net worth depending on conditions
- Rates adjust gradually each day (not instant) to simulate market movement

## Localization

Supported languages: Czech, German, English, Spanish, French, Hungarian, Italian, Japanese, Korean, Dutch, Polish, Brazilian Portuguese, Russian, Turkish, Ukrainian, Simplified Chinese, Traditional Chinese.

## Installation

1. Copy the `Syxian Banking` folder to your Songs of Syx mods directory:
   - Windows: `%APPDATA%\songsofsyx\mods\`
2. Enable the mod in the game launcher
3. The Banking tab will appear in the settlement management screen

## Compatibility

- Game version: **v71**
- Mod version: **0.1**
