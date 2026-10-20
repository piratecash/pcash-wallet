package cash.p.terminal.modules.intro

import androidx.lifecycle.ViewModel
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.resources.Intro_Wallet_Screen2Description
import cash.p.terminal.resources.Intro_Wallet_Screen2Title
import cash.p.terminal.resources.Intro_Wallet_Screen3Description
import cash.p.terminal.resources.Intro_Wallet_Screen3Title
import cash.p.terminal.resources.Intro_Wallet_Screen4Description
import cash.p.terminal.resources.Intro_Wallet_Screen4Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_independence
import cash.p.terminal.resources.ic_independence_light
import cash.p.terminal.resources.ic_knowledge
import cash.p.terminal.resources.ic_knowledge_light
import cash.p.terminal.resources.ic_privacy
import cash.p.terminal.resources.ic_privacy_light

class IntroViewModel(
        private val localStorage: WalletOnboardingStorage
): ViewModel() {

    val slides = listOf(
        IntroModule.IntroSliderData(
            Res.string.Intro_Wallet_Screen2Title,
            Res.string.Intro_Wallet_Screen2Description,
            Res.drawable.ic_independence_light,
            Res.drawable.ic_independence
        ),
        IntroModule.IntroSliderData(
            Res.string.Intro_Wallet_Screen3Title,
            Res.string.Intro_Wallet_Screen3Description,
            Res.drawable.ic_knowledge_light,
            Res.drawable.ic_knowledge
        ),
        IntroModule.IntroSliderData(
            Res.string.Intro_Wallet_Screen4Title,
            Res.string.Intro_Wallet_Screen4Description,
            Res.drawable.ic_privacy_light,
            Res.drawable.ic_privacy
        ),
    )

    fun onStartClicked() {
        localStorage.mainShowedOnce = true
    }

}
