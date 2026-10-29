package cash.p.terminal.modules.markdown

import cash.p.terminal.core.App
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui.helpers.LinkHelper

fun HSNavigation.openMarkdownOrWeblink(url: String) {
    if (isMarkdownLink(url)) {
        slideFromRight(MarkdownPage(MarkdownPage.Input(url)))
    } else {
        LinkHelper.openLinkInAppBrowser(App.instance, url)
    }
}
