## 🚀 Version 1.1.261 Update
_Release date: October 31, 2026_

### ✨ New Features

- **Added Premium login activity monitoring**
  Successful, failed, and Duress Mode access attempts can be logged. Optional photos, Zcash notifications, and data retention and deletion controls are available.

- **Added support for GRAM W5 addresses**
  Receiving, sending, swaps, TON Connect, and Jetton tokens are supported for W5 wallets.

### ⚙️ Improvements

- **Improved blockchain data updates**
  New TON transactions appear automatically, Stellar refreshes after the connection is restored, and BEAM synchronization resumes after the device wakes up.

- **Improved two-step swaps**
  The second step can now be signed on the same screen as soon as the first step is complete.

- **Added completion time to swap details**

- **Updated memo limits to match network rules**
  Size is now measured in UTF-8 bytes separately for Zcash, Bitcoin, Litecoin, Dash, Dogecoin, Bitcoin Cash, and eCash.

- **Removed PayCore from the list of available swap providers**

### 🐛 Fixes

- **Improved app stability on Android 13 and Android 16**
  Fixed crashes on the key storage protection screen and when opening or returning to the app on some Xiaomi devices.

- **Fixed QR code flows**
  Resolved a crash after enabling Premium from the scanner, preserved scanned results when returning to a screen, and removed a brief freeze during the first scan.

- **Fixed values in Changelly swap details**
  The actual amount, exchange rate, and price impact are now displayed correctly.

- **Fixed backup options for Zcash wallets imported with a Spending Key**
  The app no longer offers a seed phrase backup when no seed phrase is available.

- **Fixed ENS resolution and offline Stellar transactions on older Android versions**

- **Fixed display, import, and backup of EVM private keys with leading zeros**

- **Fixed swaps from XLM to a new Stellar account**
  If the destination account is not yet active, it is created by the first payment.
