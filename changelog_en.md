## 🚀 Version 2.0.265 Update
_Release date: November 23, 2026_

### ✨ New Features

- **Added Ledger hardware wallet support over USB**
  Users can create Ledger wallets, add supported networks, verify addresses, and confirm operations on the device. Supported networks include the Bitcoin family, EVM networks and tokens, Solana, TRON, Stellar, TON, THORChain, transparent and unified Zcash addresses, and Monero. Monero synchronization works through the connected Ledger and recovers after interruptions, while the transparent Zcash address matches Ledger Live. Swaps, offline signing, and WalletConnect are available where supported.

- **Added Bitcoin message signing**
  Users can prove ownership of a BTC address without sending a transaction. Standard BIP-137 and BIP-322 formats are supported according to the address type and hardware wallet capabilities.

- **Expanded the staking income chart**
  It now shows the coin ticker and historical income value in the user's currency. Users can switch between coin amounts, currency values, or both.

### 🔒 Security and Privacy

- **Strengthened WalletConnect phishing protection**
  The app warns about known scam sites, domain mismatches, and unverified sites. The unsafe blind hash signing method `eth_sign` is no longer supported.

- **Added transaction details for TON Connect and Stellar WalletConnect**
  Recipients, amounts, and assets are shown before confirmation. Unknown or potentially dangerous actions include an additional warning.

- **Added protection against overlays from other apps**
  Third-party floating windows can no longer cover wallet screens or transaction confirmations.

- **Improved clipboard protection for recovery phrases and private keys**
  Copied secrets are marked as sensitive and automatically removed after 60 seconds without affecting content copied afterward.

- **Isolated TON Connect sessions in Duress Mode**
  The list now shows only sessions belonging to wallets available at the current protection level.

- **Improved file backup reliability**
  A backup is marked as saved only after the file is written successfully. If writing fails, the backup reminder remains visible.

- **Added protection against malicious TON NFTs**
  Before sending or burning a suspicious NFT, the app checks the expected result and warns when the NFT would remain in the wallet while the fee is lost.

### ⚙️ Improvements

- **Accelerated Zcash synchronization**
  Full wallet restoration is approximately 2.3 times faster with a direct connection. Recovery from short chain reorganizations was improved, and temporary input reservations can no longer lock funds indefinitely.

- **Improved transaction history search**
  Partial words and operation names now return consistent results across every tab. Long histories can be searched further for older matching transactions.

- **Added TRON Energy and Bandwidth details**
  Send and swap screens show available and required resources, the expected TRX fee, and address activation costs. Transaction details show the actual resource usage.

- **Improved TON NFT display**
  The reason for restricted images is now shown, the official P.CASH collection is recognized correctly, and sent NFTs are marked as created by this wallet.

- **Improved wallet management and interface consistency**
  Deleted wallets disappear immediately, Shamir wallets provide quick access to backup, extra whitespace is removed from wallet names, passcode creation titles are clearer, and coin icons use a consistent circular background.

### 🐛 Fixes

- **Fixed EVM node rejection handling**
  Transactions rejected by a node are no longer reported as successfully sent.

- **Fixed navigation after sending a transaction**
  After a successful operation, the app returns to the asset screen or asset list instead of reopening the send form.

- **Fixed balance and error display during swaps**
  Hidden balances remain hidden in asset selection lists, and a temporary insufficient balance error no longer appears while the fee is being recalculated.

- **Fixed a rare crash involving real-time transaction notifications in the background**
