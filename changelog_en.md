## 🚀 Version 1.1.261 Update
_Release date: November 1, 2026_

### ✨ New Features

- **Added Premium login activity monitoring**
  Successful, failed, and Duress Mode access attempts can be logged. Optional photos, Zcash notifications, and data retention and deletion controls are available.

- **Added support for GRAM W5 addresses**
  Receiving, sending, swaps, TON Connect, and Jetton tokens are supported for W5 wallets.

### ⚙️ Improvements

- **Refined the redesigned app interface**
  Colors and the main balance screen are now consistent across light and dark themes.

- **Clarified Zcash login notification text**
  Labels and descriptions now explain the feature and notification conditions more clearly.

- **Added the ability to disable Zcash login notifications**

- **BEAM synchronization now resumes automatically after the device wakes up**

- **New TON transactions now appear without a manual refresh**

- **Stellar now refreshes balances and transaction history after the internet connection is restored**

- **Improved two-step swaps**
  The second step can now be signed on the same screen as soon as the first step is complete.

- **Added completion time to swap details**

- **Updated memo limits to match network rules**
  Size is now measured in UTF-8 bytes separately for Zcash, Bitcoin, Litecoin, Dash, Dogecoin, Bitcoin Cash, and eCash.

- **Removed PayCore from the list of available swap providers**

### 🐛 Fixes

- **Fixed WalletConnect session handling when deleting a wallet**
  Sessions from a deleted wallet are no longer reassigned to another wallet. Temporary service connections were removed, and an error after connecting through a browser link was fixed.

- **Fixed a rare crash when leaving the asset screen**
  The app no longer closes when navigating back while the transaction list is scrolling or updating.

- **Fixed a crash on Android 13 when opening the key storage protection screen**

- **Fixed a crash when opening or returning to the app on some Xiaomi devices running Android 16**

- **Fixed returning from the QR scanner and enabling Premium**
  Resolved a crash after enabling Premium from the scanner and preserved scanned results when returning to a screen.

- **Removed a brief interface freeze during the first QR code scan**

- **Fixed values in Changelly swap details**
  The actual amount, exchange rate, and price impact are now displayed correctly.

- **Fixed backup options for Zcash wallets imported with a Spending Key**
  The app no longer offers a seed phrase backup when no seed phrase is available.

- **Fixed ENS resolution and offline Stellar transactions on older Android versions**

- **Fixed display, import, and backup of EVM private keys with leading zeros**

- **Fixed swaps from XLM to a new Stellar account**
  If the destination account is not yet active, it is created by the first payment.
