package cash.p.terminal.core

import cash.p.terminal.modules.settings.terms.TermsPage
import cash.p.terminal.navigation.HSNavigation

fun HSNavigation.navigateWithTermsAccepted(action: () -> Unit) {
    if (!getKoinInstance<ITermsManager>().allTermsAccepted) {
        slideFromBottomForResult<TermsPage.Result>(TermsPage()) { result ->
            if (result.termsAccepted) {
                action.invoke()
            }
        }
    } else {
        action.invoke()
    }
}
