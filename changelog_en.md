## 🚀 Version 1.2.262 Update
_Release date: November 11, 2026_

### ✨ New Features

- **Added Shamir Backup support**
  When creating a wallet, its backup can be split into multiple shares with a chosen recovery threshold, such as 2 of 3. Creation and recovery are available to all users.

- **Added protection when funding TRON accounts with multisig or restricted permissions**
  Before receiving TRX or TRC-20 tokens or starting a swap, the app warns when one seed phrase may not be sufficient to control the funds. Detection of active accounts with a zero TRX balance was also fixed.

- **Added detailed progress for swaps through Unstoppable**
  A new screen shows swap steps, amounts, statuses, transaction IDs, explorer links, and provider support contacts. Data refreshes automatically until the swap is complete.

### ⚙️ Improvements

- **Updated the BEAM SDK to version 0.3.2**
  Incoming transaction processing is now more secure.

- **Updated the Zcash SDK to version 1.5.0**
  Added support for the new fee rules starting at block 3,590,000. Maximum send calculations, actual fee display, and outgoing transaction amounts were also improved.

- **Improved TON balance checks for Jetton transfers and swaps**
  The app now accounts for the full gas and network fee and shows how much TON is missing before the operation is submitted.

- **Reduced the preparation time for sending Monero from Trezor wallets**

- **Improved asset list loading on the Receive and Send screens**
  Added loading indicators, pagination for large lists, and a clear message when a search returns no results.

- **Improved Tor connection recovery**
  Tor now reconnects after the app returns from the background without requiring an app restart.

- **Optimized internal app components**
  Removed unused code without changing available coin analytics or transaction history.

### 🐛 Fixes

- **Fixed QR scanner behavior during fast recognition**
  Scan results are now reliably returned to the screen, and scanner resources are released correctly after closing.

- **Fixed security settings in hidden wallets**
  Hidden wallets no longer show main or Duress PIN settings that do not apply to them.

- **Fixed EIP-7702 operation display in EVM networks**
  Token transfers through delegated accounts and relayer services are now correctly recognized in transaction history.

- **Fixed RBF transaction acceleration for Bitcoin, Litecoin, and other supported networks**
  The acceleration fee is now always higher than the original fee, and the confirmed replacement transaction ID is displayed correctly.

- **Fixed pending CEX swap details**
  Until final data is available, the app now shows “In progress” instead of zero amounts and an incorrect price impact.

- **Fixed decimal amount input on Android 8–12**

- **Fixed localization of the passcode creation screen for the Premium login activity feature**

## 🚀 Version 1.1.261 Update
_Release date: November 5, 2026_

### ✨ New Features

- **Added Premium login activity monitoring**
  Successful, failed, and Duress Mode access attempts can be logged. Optional photos, Zcash notifications, and data retention and deletion controls are available.

- **Added support for GRAM W5 addresses**
  Receiving, sending, swaps, TON Connect, and Jetton tokens are supported for W5 wallets.

### ⚙️ Improvements

- **Updated the Zcash SDK for the NU7 network upgrade**
  Synchronization, transaction sending, history, and memos will continue to work after NU7 activation.

- **Improved Ethereum RPC failure handling**
  The app automatically uses fallback connections when the selected node is unavailable. WalletConnect operations can now retry fee loading and request submission.

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

- **Fixed a crash when rejecting a WalletConnect request**
  Expired or repeated requests can now be closed without crashing the app or leaving the buttons unresponsive.

- **Fixed a rare crash when leaving the asset screen**
  The app no longer closes when navigating back while the transaction list is scrolling or updating.

- **Fixed a crash on Android 13 when opening the key storage protection screen**

- **Fixed a crash when opening or returning to the app on some Xiaomi devices running Android 16**

- **Fixed returning from the QR scanner and enabling Premium**
  Resolved a crash after enabling Premium from the scanner and preserved scanned results when returning to a screen.

- **Removed a brief interface freeze during the first QR code scan**

- **Fixed a rare crash while synchronizing Litecoin with MWEB enabled**

- **Fixed values in Changelly swap details**
  The actual amount, exchange rate, and price impact are now displayed correctly.

- **Fixed backup options for Zcash wallets imported with a Spending Key**
  The app no longer offers a seed phrase backup when no seed phrase is available.

- **Fixed ENS resolution and offline Stellar transactions on older Android versions**

- **Fixed display, import, and backup of EVM private keys with leading zeros**

- **Fixed swaps from XLM to a new Stellar account**
  If the destination account is not yet active, it is created by the first payment.

## 🚀 Version 1.0.260 Update
_Release date: October 16, 2026_

### ✨ New Features

- **Introduced a completely redesigned app interface**

- **Added the Regular address type for BEAM**

### ⚙️ Improvements

- **Improved BEP-20 token synchronization**
  Newly added tokens now synchronize after being added to the wallet.

- **Added Monero balance display when there is no active connection**

### 🐛 Fixes

- **Fixed Zcash synchronization when running the app in LDPlayer**

- **Fixed adding new tokens to Tangem cards**
