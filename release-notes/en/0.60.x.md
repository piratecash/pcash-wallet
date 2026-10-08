## 🚀 Version 0.60.1 Update
_Release date: September 22, 2026_

### ✨ New Features

- **Added YiFi as a new swap provider**

- **Added loyalty program diagnostics for SWAP 6 in the @piratecash_bot game**

### ⚙️ Improvements

- **Reduced app size through R8 optimization**

### 🐛 Fixes

- **Fixed a recurring crash on Android 16 when the app is backgrounded from the token screen**

- **Fixed ROI display on the @piratecash_bot game connection screen**

## 🚀 Version 0.60.0 Update
_Release date: September 15, 2026_

### ✨ New Features

- **Added SWAP 6 support for the @piratecash_bot game**

### ⚙️ Improvements

- **Clarified security warnings for sensitive wallet data**
  The wording now accounts for cases where the warning does not refer specifically to a private key.

### 🐛 Fixes

- **Fixed wallet recovery from Japanese seed phrases**
  Japanese BIP-39 phrases are now processed correctly and restore the expected addresses.

- **Fixed Litecoin synchronization getting stuck for certain address types**
  Balances and transaction history now update correctly. Unnecessary network and CPU usage caused by stalled synchronization has also been eliminated.

- **Fixed Monero View Key and Spend Key screens**
  The screens now display keys for the selected wallet instead of the active wallet. If a key is unavailable, the app shows a clear message instead of an empty value.

- **Fixed a rare crash on Android 16**
  Improved stability when navigating between screens or sending the app to the background.

- **Fixed the display of available app updates**
  The app now respects its installation source and no longer offers a version that is not yet available to the user on Google Play.

- **Restored access to help articles**
  Info buttons and wallet migration banners now open the correct articles instead of a “Page not found” screen.

- **Fixed incoming transaction status in certain scenarios**

- **The “Try Again” button is no longer shown while synchronization is already in progress**

- **Fixed colors in the transaction list and transaction details**
