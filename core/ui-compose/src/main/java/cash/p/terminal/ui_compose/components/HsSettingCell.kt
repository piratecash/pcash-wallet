package cash.p.terminal.ui_compose.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

@Composable
fun HsSettingCell(
    @StringRes title: Int,
    @DrawableRes icon: Int? = null,
    iconTint: Color? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) = EnableableSettingCell(stringResource(title), icon?.let { painterResource(it) }, iconTint, value, onClick, enabled)
