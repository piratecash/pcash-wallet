# Crashlytics needs source locations from the same build as mapping.txt.
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Legacy Gson models use unannotated field names as their persisted/wire format.
-keep class cash.p.terminal.modules.backuplocal.BackupLocalModule$* { <fields>; <init>(...); }
-keep class cash.p.terminal.modules.backuplocal.fullbackup.* { <fields>; <init>(...); }
-keep class cash.p.terminal.modules.contacts.ContactsRepository$ContactJson** { <fields>; <init>(...); }
-keep class cash.p.terminal.entities.** { <fields>; <init>(...); }
-keep class cash.p.terminal.wallet.models.** { <fields>; <init>(...); }
-keep class cash.p.terminal.wallet.providers.HistoricalCoinPriceResponse { <fields>; <init>(...); }
-keep class cash.p.terminal.wallet.providers.ChartStart { <fields>; <init>(...); }
-keep class cash.p.terminal.wallet.providers.SignalResponse { <fields>; <init>(...); }
-keep class cash.p.terminal.wallet.providers.CryptoCompareProvider$PostsResponse { <fields>; <init>(...); }
-keep class cash.p.terminal.wallet.providers.CryptoCompareProvider$PostItem { <fields>; <init>(...); }
-keep class cash.p.terminal.wallet.providers.TopCollectionRaw { <fields>; <init>(...); }
-keep class cash.p.terminal.core.address.ChainalysisAddressValidator$* { <fields>; <init>(...); }
-keep class cash.p.terminal.core.address.HashDitAddressValidator$* { <fields>; <init>(...); }
-keep class cash.p.terminal.core.providers.EvmLabelProvider$* { <fields>; <init>(...); }
-keep class cash.p.terminal.core.providers.nft.** { <fields>; <init>(...); }
-keep class cash.p.terminal.modules.coin.tweets.* { <fields>; <init>(...); }
-keep class cash.p.terminal.modules.multiswap.providers.AllBridgeAPI$Response$** { <fields>; <init>(...); }
-keep class cash.p.terminal.modules.multiswap.providers.ThornodeAPI$Response$** { <fields>; <init>(...); }
-keep class cash.p.terminal.modules.walletconnect.WCWalletRequestHandler$* { <fields>; <init>(...); }
-keep class cash.p.terminal.modules.walletconnect.request.sendtransaction.WCEthereumTransaction { <fields>; <init>(...); }
-keep class cash.p.terminal.modules.walletconnect.stellar.WCActionStellarSignXdr$Params { <fields>; <init>(...); }
-keep class cash.p.terminal.modules.walletconnect.stellar.WCActionStellarSignAndSubmitXdr$Params { <fields>; <init>(...); }
-keep class cash.p.terminal.widgets.MarketWidgetState { <fields>; <init>(...); }
-keep class cash.p.terminal.widgets.MarketWidgetItem { <fields>; <init>(...); }

# Gson also persists these enum names in backups.
-keep enum cash.p.terminal.core.managers.RestoreSettingType { *; }
-keep enum cash.p.terminal.wallet.balance.BalanceViewType { *; }
-keep enum cash.p.terminal.modules.theme.ThemeType { *; }

# The class name is the existing SharedPreferences filename.
-keepnames class cash.p.terminal.strings.helpers.LocaleHelper

# Navigation XML resolves Parcelable argument classes by their original names.
-keep,allowoptimization class cash.p.terminal.** implements android.os.Parcelable

# These SDKs lack complete consumer rules for their reflective JSON/RPC models.
# Keep their boundaries intact for the initial rollout, as upstream does.
-keep class io.horizontalsystems.ethereumkit.** { *; }
-keep class io.horizontalsystems.erc20kit.** { *; }
-keep class io.horizontalsystems.nftkit.** { *; }
-keep class io.horizontalsystems.uniswapkit.** { *; }
-keep class io.horizontalsystems.oneinchkit.** { *; }
-keep class io.horizontalsystems.tronkit.** { *; }
-keep class io.horizontalsystems.stellarkit.** { *; }
-keep class io.horizontalsystems.tonkit.** { *; }
-keep class io.horizontalsystems.solanakit.** { *; }
-keep class io.horizontalsystems.bitcoincore.** { *; }
-keep class io.horizontalsystems.bitcoinkit.** { *; }
-keep class io.horizontalsystems.dashkit.** { *; }
-keep class io.horizontalsystems.litecoinkit.** { *; }
-keep class io.horizontalsystems.bitcoincashkit.** { *; }
-keep class io.horizontalsystems.ecashkit.** { *; }
-keep class io.horizontalsystems.dogecoinkit.** { *; }
-keep class io.horizontalsystems.cosantakit.** { *; }
-keep class io.horizontalsystems.piratecashkit.** { *; }
-keep class io.horizontalsystems.hodler.** { *; }
-keep class io.horizontalsystems.hdwalletkit.** { *; }
-keep class io.horizontalsystems.feeratekit.** { *; }
-keep class org.stellar.sdk.** { *; }
-keep class com.solana.** { *; }
-keep class org.sol4k.** { *; }

# ABI decoding reads generic superclasses; retain anonymous TypeReference subclasses.
-keep class org.web3j.** { *; }
-keep class * extends org.web3j.abi.TypeReference { *; }
-keep class * implements org.web3j.abi.datatypes.Type { *; }
-keep class org.bouncycastle.** { *; }

# JNI callbacks and constructors are resolved by name from native libraries.
-keep class com.m2049r.xmrwallet.model.** { *; }
-keep class com.m2049r.xmrwallet.ledger.** { *; }
-keep class com.m2049r.xmrwallet.service.BluetoothService { *; }
-keep class cash.z.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class org.torproject.** { *; }
-keep class org.dashj.** { *; }
-keep class fr.acinq.secp256k1.** { *; }
-keep class org.bitcoin.NativeSecp256k1 { *; }
-keep class org.bitcoin.NativeSecp256k1Util { *; }
-keep class org.bitcoin.Secp256k1Context { *; }
-keep class com.goterl.** { *; }
-keep class org.libsodium.** { *; }
-keep class uniffi.** { *; }

# Tangem uses Moshi's Kotlin reflection and looks up R.string fields by name.
-keep class com.tangem.** { *; }

# Package-relative JSON resources and their reflective configuration models.
-keep class com.unstoppabledomains.** { *; }

# Stellar SDK's Lombok annotations are compile-time only.
-dontwarn lombok.Generated
-dontwarn lombok.NonNull

# Desktop AWT, optional Netty diagnostics, and EdDSA's non-Android key fallback.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn sun.security.x509.X509Key
