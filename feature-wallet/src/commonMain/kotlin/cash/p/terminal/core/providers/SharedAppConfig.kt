package cash.p.terminal.core.providers

import cash.p.terminal.network.data.EncodedSecrets
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

/** The part of the app configuration that does not depend on the platform build. */
object SharedAppConfig {

    val merkleIoKey by lazy { EncodedSecrets.MERKLE_IO_KEY }
    val openSeaApiKey by lazy { EncodedSecrets.OPEN_SEA_API_KEY }
    val trongridApiKeys: List<String> by lazy { EncodedSecrets.TRONGRID_API_KEYS.toKeyList() }
    val alchemySolanaApiKeys: List<String> by lazy { EncodedSecrets.ALCHEMY_SOLANA_API_KEYS.toKeyList() }
    const val chainalysisBaseUrl = "https://public.chainalysis.com/api/v1/"
    const val hashDitBaseUrl = "https://service.hashdit.io/v2/hashdit/"
    const val alphaAmlBaseUrl = "https://api.alpha-aml.com"
    const val reportEmail = "i@p.cash"
    const val payCoreSupportUrl = "https://t.me/paycore_supp"
    const val isSwapEnabled = true
    const val isPayCoreEnabled = true
    const val fiatDecimal: Int = 2

    val chainalysisApiKey by lazy { EncodedSecrets.CHAINALYSIS_API_KEY }
    val hashDitApiKey by lazy { EncodedSecrets.HASH_DIT_API_KEY }
    val alphaAmlApiKey by lazy { EncodedSecrets.ALPHA_AML_API_KEY }

    val spamCoinValueLimits: Map<String, BigDecimal> = mapOf(
        "USDT" to BigDecimal("0.01"),
        "USDC" to BigDecimal("0.01"),
        "DAI" to BigDecimal("0.01"),
        "BUSD" to BigDecimal("0.01"),
        "EURS" to BigDecimal("0.01"),
        "BSC-USD" to BigDecimal("0.01"),
        "TRX" to BigDecimal("0.1"),
        "XLM" to BigDecimal("0.01"),
        "SOL" to BigDecimal("0.000001"),
    )

    val donateAddresses: Map<BlockchainType, String> by lazy {
        mapOf(
            BlockchainType.Beam to
                "23oqENM4qYeLYVxFrLJKVzNjgMAmYVsN5ULEq6NeoHpRQmYZ8K9SmmCkNjeMdUS9143uUKLmbSgrFRujGL7aTTfb1kHPPx3R" +
                "tk1ACkNbx6C7n9emx7Aw4bT6EKqWwHErJccR2E1PLqcmguFyucRXwANvQiGPsEVBsXgfkNoKaKXixadDXVnYHtw5qXbzJj5Z" +
                "LK9nZDAibfUocAa6MTmJm9uUWarfdLyS2xKP6EUuAvtbhMt9WVVVnYhGaXtibYDfVfUTW8PMnP3LH2C1rarAXBPS51TzkKAY" +
                "QUs3zfncjQMTYe3uhRhAgWZacyjNJMRcERKF3XqrnJUaHA4dimB7n9zZ46Fg471iWx8JVeqJWtPDfrR1k7Bqbixz4EyyrUQo" +
                "jPgQ3PnTvi3YhQEX544s23kFYmzFwEsT3wDwhohFg27mzpx",
            BlockchainType.Bitcoin to "bc1q2ph64sryt6skegze6726fp98u44kjsc5exktap",
            BlockchainType.BitcoinCash to "bitcoincash:qp497gfxadpz30zxp7kltcq9c2jn57z34q5xuclrhf",
            BlockchainType.ECash to "ecash:qrzcal2fmm6vumxp3g2jndk0fepmt2racya9lc4yxy",
            BlockchainType.Litecoin to "LgevEuuQ8ht3rnd3YyQ6j88cXs49aCaoSU",
            BlockchainType.Cosanta to "Cbbp3meofT1ESU5p4d9ucXpXw9pxKCMEyi",
            BlockchainType.Dash to "Xv7U37XKp5d4fjvbeuganwhqXN7Sm4JJkt",
            BlockchainType.Dogecoin to "DUQx8grwwEgjmdC3WNrczxz33ub3S45x4q",
            BlockchainType.Zcash to "zs1hwyqs4mfrynq0ysjmhv8wuau5zam0gwpx8ujfv8epgyufkmmsp6t7cfk9y0th7qyx7fsc5azm08",
            BlockchainType.Ethereum to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.BinanceSmartChain to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.Polygon to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.PirateCash to "PB2vfGqfagNb12DyYTZBYWGnreyt7E4Pug",
            BlockchainType.Avalanche to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.Optimism to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.Base to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.ZkSync to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.RobinhoodChain to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.ArbitrumOne to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.Solana to "CefzHT5zCUncm3yhTLck9bCRYkbjHrKToT1GpPUyqCMa",
            BlockchainType.Gnosis to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.Fantom to "0x52be29951B0D10d5eFa48D58363a25fE5Cc097e9",
            BlockchainType.Thorchain to "thor1xpthl6uhkyepgefmnq5udq5r2298q84d3edtcn",
            BlockchainType.Ton to "UQCYTBH7n8OnQ6BgOfdkNRWF7socLJb9U-JMRcoz3UpL_0V6",
            BlockchainType.Tron to "TAaYtFBxJztC5pG1zMapvUFSfEyJLSLJM5",
            BlockchainType.Monero to
                    "4AzdEoZxeGMFkdtAxaNLAZakqEVsWpVb2at4u6966WGDiXkS7ZPyi7haeThTGUAWXVKDTmQ9DYTWRHMjGVSBW82xRQqPxkg",
            BlockchainType.Stellar to "GAZXDMWYHMPM2WF6FCWEBIMJITKKTU6MLHYLCFRVB3WMXTNPVEHBOXRE"
        ).toList().sortedBy { it.first.uid }.toMap()
    }
}
