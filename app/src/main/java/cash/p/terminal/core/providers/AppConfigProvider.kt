package cash.p.terminal.core.providers

import cash.p.terminal.BuildConfig
import cash.p.terminal.R
import cash.p.terminal.core.managers.Currencies
import cash.p.terminal.network.data.EncodedSecrets
import cash.p.terminal.wallet.MarketApiConfig
import io.horizontalsystems.core.entities.Currency
import cash.p.terminal.strings.helpers.Translator
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

object AppConfigProvider : AddressResolutionConfig {

    val appVersion by lazy { BuildConfig.VERSION_NAME }
    val appBuild by lazy { BuildConfig.VERSION_CODE }
    val isDebug by lazy { BuildConfig.DEBUG }
    val appGitHash by lazy { BuildConfig.GIT_HASH }
    val appGitBranch by lazy { BuildConfig.GIT_BRANCH }
    val companyWebPageLink = SharedAppConfig.companyWebPageLink
    val appWebPageLink = SharedAppConfig.appWebPageLink
    val analyticsLink = SharedAppConfig.analyticsLink
    val appGithubLink = SharedAppConfig.appGithubLink
    val githubApiBaseUrl by lazy { Translator.getString(R.string.githubApiBaseUrl) }
    val githubRawBaseUrl by lazy { Translator.getString(R.string.githubRawBaseUrl) }
    val githubApiProxyBaseUrl by lazy { Translator.getString(R.string.githubApiProxyBaseUrl) }
    val githubRawProxyBaseUrl by lazy { Translator.getString(R.string.githubRawProxyBaseUrl) }
    val appTwitterLink = SharedAppConfig.appTwitterLink
    val appTelegramLink = SharedAppConfig.appTelegramLink
    val appRedditLink = SharedAppConfig.appRedditLink
    const val walletConnectUrl = "relay.walletconnect.com"
    val walletConnectProjectId by lazy { EncodedSecrets.WALLET_CONNECT_V2_KEY }
    val walletConnectAppMetaDataName by lazy { Translator.getString(R.string.walletConnectAppMetaDataName) }
    val walletConnectAppMetaDataUrl by lazy { Translator.getString(R.string.walletConnectAppMetaDataUrl) }
    val walletConnectAppMetaDataIcon by lazy { Translator.getString(R.string.walletConnectAppMetaDataIcon) }

    val spamCoinValueLimits: Map<String, BigDecimal> get() = SharedAppConfig.spamCoinValueLimits

    override val blocksDecodedEthereumRpc by lazy {
        Translator.getString(R.string.blocksDecodedEthereumRpc)
    }
    val twitterBearerToken by lazy {
        EncodedSecrets.TWITTER_BEARER_TOKEN
    }
    val guidesUrl by lazy {
        Translator.getString(R.string.guidesUrl)
    }
    val faqUrl by lazy {
        Translator.getString(R.string.faqUrl)
    }
    val coinsJsonUrl by lazy {
        Translator.getString(R.string.coinsJsonUrl)
    }
    val providerCoinsJsonUrl by lazy {
        Translator.getString(R.string.providerCoinsJsonUrl)
    }

    val marketApiBaseUrl by lazy {
        Translator.getString(R.string.marketApiBaseUrl)
    }

    val marketApiKey = MarketApiConfig.API_KEY

    val openSeaApiKey get() = SharedAppConfig.openSeaApiKey

    val solscanApiKey by lazy {
        EncodedSecrets.SOLSCAN_API_KEY
    }

    val trongridApiKeys: List<String> get() = SharedAppConfig.trongridApiKeys

    val alchemySolanaApiKeys: List<String> get() = SharedAppConfig.alchemySolanaApiKeys

    override val udnApiKey by lazy {
        EncodedSecrets.UDN_API_KEY
    }

    val oneInchApiKey by lazy {
        EncodedSecrets.ONE_INCH_API_KEY
    }

    val chainalysisBaseUrl get() = SharedAppConfig.chainalysisBaseUrl

    val chainalysisApiKey get() = SharedAppConfig.chainalysisApiKey

    val hashDitBaseUrl get() = SharedAppConfig.hashDitBaseUrl

    val hashDitApiKey get() = SharedAppConfig.hashDitApiKey

    val premiumApiBaseUrl by lazy { BuildConfig.PREMIUM_API_BASE_URL }

    val merkleIoKey get() = SharedAppConfig.merkleIoKey

    val fiatDecimal: Int = 2

    val currencies: List<Currency> = Currencies.all

    val donateAddresses: Map<BlockchainType, String> get() = SharedAppConfig.donateAddresses
}
