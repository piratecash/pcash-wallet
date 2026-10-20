package cash.p.terminal.modules.intro

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

object IntroModule {

    data class IntroSliderData(
        val title: StringResource,
        val subtitle: StringResource,
        val imageLight: DrawableResource,
        val imageDark: DrawableResource
    )

}
