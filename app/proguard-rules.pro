# Adding a class of ours whose name is data (Gson/Moshi field binding, a resource or file name built
# from the class name, a navigation enum resolved by serial name)? Annotate it with @Keep instead of
# adding a rule here: the annotation survives a Move/Rename, a package path does not. Rules here are
# for code we cannot annotate — kits and third-party libraries — and those classes also belong in
# app/r8-critical-classes.txt.
#
# minify{Qa,Release}WithR8 verifies this file (app/r8-verification.gradle): a rule matching no class
# fails the build. Fix the rule; never delete it to get a green build.

# Keep source locations so local diagnostic stack traces remain readable.
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

# Typed navigation routes resolve enum arguments through Class.forName on the serial name,
# and Gson/Room persist enum constants by name. Keeping every enum name costs a few dozen
# kilobytes and removes the need to remember @Keep on each new route argument.
-keepnames class ** extends java.lang.Enum

# These SDKs lack complete consumer rules for their reflective JSON/RPC models.
# Keep their boundaries intact for the initial rollout. The bitcoin-family kits also
# load their checkpoint resource by the network class simple name, so renaming them
# breaks the kit at construction — the package here must match the kit's real package,
# which is not always its module name.
-keep class io.horizontalsystems.ethereumkit.** { *; }
-keep class io.horizontalsystems.erc20kit.** { *; }
-keep class io.horizontalsystems.nftkit.** { *; }
-keep class io.horizontalsystems.uniswapkit.** { *; }
-keep class io.horizontalsystems.oneinchkit.** { *; }
-keep class io.horizontalsystems.tronkit.** { *; }
-keep class io.horizontalsystems.stellarkit.** { *; }
-keep class io.horizontalsystems.tonkit.** { *; }
# TonAPI models ship inside the TON kit and are bound by Moshi's reflective Kotlin adapter.
-keep class io.tonapi.** { *; }
-keep class io.horizontalsystems.solanakit.** { *; }
-keep class io.horizontalsystems.bitcoincore.** { *; }
-keep class io.horizontalsystems.bitcoinkit.** { *; }
-keep class io.horizontalsystems.dashkit.** { *; }
-keep class io.horizontalsystems.litecoinkit.** { *; }
-keep class io.horizontalsystems.bitcoincash.** { *; }
-keep class io.horizontalsystems.ecash.** { *; }
-keep class chronik.** { *; }
-keep class cash.p.dogecoinkit.** { *; }
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
-whyareyoukeeping class com.reown.android.push.notifications.PushMessagingService
