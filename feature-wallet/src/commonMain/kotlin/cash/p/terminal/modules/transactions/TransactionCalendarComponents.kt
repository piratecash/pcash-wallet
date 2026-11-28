package cash.p.terminal.modules.transactions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_calendar_24
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.transactions_calendar_back_to_latest
import cash.p.terminal.resources.transactions_calendar_change_period
import cash.p.terminal.resources.transactions_calendar_from
import cash.p.terminal.resources.transactions_calendar_mode_period
import cash.p.terminal.resources.transactions_calendar_mode_to_date
import cash.p.terminal.resources.transactions_calendar_next_month
import cash.p.terminal.resources.transactions_calendar_period_day
import cash.p.terminal.resources.transactions_calendar_period_empty_all
import cash.p.terminal.resources.transactions_calendar_period_empty_approve
import cash.p.terminal.resources.transactions_calendar_period_empty_incoming
import cash.p.terminal.resources.transactions_calendar_period_empty_outgoing
import cash.p.terminal.resources.transactions_calendar_period_empty_swap
import cash.p.terminal.resources.transactions_calendar_period_end
import cash.p.terminal.resources.transactions_calendar_period_range
import cash.p.terminal.resources.transactions_calendar_period_selected
import cash.p.terminal.resources.transactions_calendar_period_up_to
import cash.p.terminal.resources.transactions_calendar_previous_month
import cash.p.terminal.resources.transactions_calendar_show
import cash.p.terminal.resources.transactions_calendar_show_up_to
import cash.p.terminal.resources.transactions_calendar_title
import cash.p.terminal.resources.transactions_calendar_to
import cash.p.terminal.resources.transactions_calendar_to_date_hint
import cash.p.terminal.ui_compose.components.BottomSheetSurface
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val IconButtonSize = 44.dp
private val DayCellHeight = 44.dp
private val DayCircleSize = 40.dp
private const val DAYS_IN_WEEK = 7

@Composable
fun TransactionCalendarSheet(
    today: LocalDate,
    appliedPeriod: TransactionPeriod?,
    onApply: (TransactionPeriod) -> Unit,
    onClose: () -> Unit,
    locale: Locale = Locale.getDefault(),
) {
    var state by remember {
        mutableStateOf(
            TransactionCalendarState.create(today, TransactionCalendarState.firstDayOfWeek(locale), appliedPeriod)
        )
    }
    TransactionCalendarSheetContent(
        state = state,
        locale = locale,
        onStateChange = { state = it },
        onApply = { state.period()?.let(onApply) },
        onClose = onClose,
    )
}

@Composable
internal fun TransactionCalendarSheetContent(
    state: TransactionCalendarState,
    locale: Locale,
    onStateChange: (TransactionCalendarState) -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    BottomSheetSurface {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SheetHandle()
            CalendarHeader(onClose = onClose)
            ModeChips(mode = state.mode, onSelect = { onStateChange(state.selectMode(it)) })
            DateFields(state = state, locale = locale, onFieldSelect = { onStateChange(state.selectField(it)) })
            MonthHeader(
                state = state,
                locale = locale,
                onPrevious = { onStateChange(state.showPreviousMonth()) },
                onNext = { onStateChange(state.showNextMonth()) },
            )
            WeekdayRow(weekdays = state.weekdays, locale = locale)
            DayGrid(cells = state.cells(), onDayTap = { onStateChange(state.onDayTap(it)) })
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp),
                title = stringResource(Res.string.transactions_calendar_show),
                onClick = onApply,
                enabled = state.canApply,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ComposeAppTheme.colors.textDisabled)
        )
    }
}

@Composable
private fun CalendarHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_calendar_24),
            contentDescription = null,
            tint = ComposeAppTheme.colors.brandDefault,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(Res.string.transactions_calendar_title),
            style = ComposeAppTheme.typography.headline2,
            color = ComposeAppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        HsIconButton(onClick = onClose, minWidth = IconButtonSize, modifier = Modifier.size(IconButtonSize)) {
            Icon(
                painter = painterResource(Res.drawable.ic_close_24),
                contentDescription = stringResource(Res.string.Button_Close),
                tint = ComposeAppTheme.colors.iconSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ModeChips(mode: CalendarMode, onSelect: (CalendarMode) -> Unit) {
    Row(
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeChip(Res.string.transactions_calendar_mode_to_date, mode == CalendarMode.ToDate) {
            onSelect(CalendarMode.ToDate)
        }
        ModeChip(Res.string.transactions_calendar_mode_period, mode == CalendarMode.Period) {
            onSelect(CalendarMode.Period)
        }
    }
}

@Composable
private fun ModeChip(title: StringResource, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .background(if (selected) ComposeAppTheme.colors.brandDefault else ComposeAppTheme.colors.transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(title),
            style = if (selected) ComposeAppTheme.typography.subhead1 else ComposeAppTheme.typography.subhead2,
            color = if (selected) {
                ComposeAppTheme.colors.buttonPrimaryBrandContent
            } else {
                ComposeAppTheme.colors.textSecondary
            },
        )
    }
}

@Composable
private fun DateFields(state: TransactionCalendarState, locale: Locale, onFieldSelect: (PeriodField) -> Unit) {
    if (state.mode == CalendarMode.ToDate) {
        ToDateField(state = state, locale = locale)
    } else {
        PeriodFields(state = state, locale = locale, onFieldSelect = onFieldSelect)
    }
}

@Composable
private fun ToDateField(state: TransactionCalendarState, locale: Locale) {
    Column {
        DateField(
            label = stringResource(Res.string.transactions_calendar_show_up_to),
            value = state.date.format(locale, FormatStyle.LONG),
            active = true,
            modifier = Modifier.padding(horizontal = 24.dp),
            onClick = {},
        )
        Text(
            text = stringResource(Res.string.transactions_calendar_to_date_hint),
            style = ComposeAppTheme.typography.caption.copy(lineHeight = 16.sp),
            color = ComposeAppTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp),
        )
    }
}

@Composable
private fun PeriodFields(state: TransactionCalendarState, locale: Locale, onFieldSelect: (PeriodField) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PeriodField.entries.forEach { field ->
            val date = if (field == PeriodField.From) state.from else state.to
            DateField(
                label = stringResource(
                    if (field == PeriodField.From) Res.string.transactions_calendar_from
                    else Res.string.transactions_calendar_to
                ),
                value = date?.format(locale, FormatStyle.MEDIUM) ?: EMPTY_DATE,
                active = state.editing == field,
                modifier = Modifier.weight(1f),
                onClick = { onFieldSelect(field) },
            )
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (active) ComposeAppTheme.colors.brandDefault else ComposeAppTheme.colors.borderDefault
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ComposeAppTheme.colors.surfacePrimary)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = ComposeAppTheme.typography.caption, color = ComposeAppTheme.colors.textSecondary)
        Text(
            text = value,
            style = ComposeAppTheme.typography.body.copy(lineHeight = 20.sp),
            color = ComposeAppTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MonthHeader(
    state: TransactionCalendarState,
    locale: Locale,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).capitalized(locale),
            style = ComposeAppTheme.typography.headline2,
            color = ComposeAppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        MonthArrowButton(
            forward = false,
            enabled = true,
            description = stringResource(Res.string.transactions_calendar_previous_month),
            onClick = onPrevious,
        )
        MonthArrowButton(
            forward = true,
            enabled = state.canGoToNextMonth,
            description = stringResource(Res.string.transactions_calendar_next_month),
            onClick = onNext,
        )
    }
}

@Composable
private fun MonthArrowButton(forward: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    val color = if (enabled) ComposeAppTheme.colors.iconPrimary else ComposeAppTheme.colors.textDisabled
    HsIconButton(
        onClick = onClick,
        enabled = enabled,
        minWidth = IconButtonSize,
        modifier = Modifier.size(IconButtonSize),
    ) {
        Canvas(modifier = Modifier.size(24.dp).semantics { contentDescription = description }) {
            val tip = if (forward) 15f else 9f
            val tail = if (forward) 9f else 15f
            val path = Path().apply {
                moveTo(tail.dp.toPx(), 6.dp.toPx())
                lineTo(tip.dp.toPx(), 12.dp.toPx())
                lineTo(tail.dp.toPx(), 18.dp.toPx())
            }
            drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun WeekdayRow(weekdays: List<DayOfWeek>, locale: Locale) {
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        weekdays.forEach { day ->
            Box(modifier = Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).capitalized(locale),
                    style = ComposeAppTheme.typography.caption,
                    color = ComposeAppTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun DayGrid(cells: List<CalendarCell?>, onDayTap: (LocalDate) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row {
                week.forEach { cell -> DayCell(cell, onDayTap, Modifier.weight(1f)) }
                repeat(DAYS_IN_WEEK - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(cell: CalendarCell?, onDayTap: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(DayCellHeight)
            .padding(vertical = 2.dp)
            .rangeBand(cell?.band ?: RangeBand.None, ComposeAppTheme.colors.brandDefault15),
        contentAlignment = Alignment.Center,
    ) {
        if (cell != null) DayCircle(cell, onDayTap)
    }
}

@Composable
private fun DayCircle(cell: CalendarCell, onDayTap: (LocalDate) -> Unit) {
    val colors = ComposeAppTheme.colors
    val ring = if (cell.isToday && !cell.selected) Modifier.border(1.dp, colors.brandDefault, CircleShape) else Modifier
    Box(
        modifier = Modifier
            .size(DayCircleSize)
            .clip(CircleShape)
            .background(if (cell.selected) colors.brandDefault else colors.transparent)
            .then(ring)
            .clickable(enabled = cell.enabled, role = Role.Button) { onDayTap(cell.date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = if (cell.selected) ComposeAppTheme.typography.headline2 else ComposeAppTheme.typography.body,
            color = when {
                cell.selected -> colors.buttonPrimaryBrandContent
                cell.enabled -> colors.textPrimary
                else -> colors.textDisabled
            },
        )
    }
}

private fun Modifier.rangeBand(band: RangeBand, color: Color): Modifier = drawBehind {
    val half = Size(size.width / 2, size.height)
    // The band runs toward the next days, which lie on the left in RTL.
    val towardNextDays = if (layoutDirection == LayoutDirection.Ltr) Offset(half.width, 0f) else Offset.Zero
    val towardPreviousDays = if (layoutDirection == LayoutDirection.Ltr) Offset.Zero else Offset(half.width, 0f)
    when (band) {
        RangeBand.None -> Unit
        RangeBand.Full -> drawRect(color)
        RangeBand.StartHalf -> drawRect(color, towardNextDays, half)
        RangeBand.EndHalf -> drawRect(color, towardPreviousDays, half)
    }
}

@Composable
fun TransactionCalendarButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inactiveTint: Color = ComposeAppTheme.colors.iconPrimary,
) {
    HsIconButton(onClick = onClick, minWidth = IconButtonSize, modifier = modifier.size(IconButtonSize)) {
        Box(modifier = Modifier.size(IconButtonSize), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(Res.drawable.ic_calendar_24),
                contentDescription = stringResource(
                    if (active) {
                        Res.string.transactions_calendar_period_selected
                    } else {
                        Res.string.transactions_calendar_title
                    }
                ),
                tint = if (active) ComposeAppTheme.colors.brandDefault else inactiveTint,
                modifier = Modifier.size(24.dp),
            )
            if (active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 7.dp, end = 7.dp)
                        .size(12.dp)
                        .background(ComposeAppTheme.colors.backgroundBase, CircleShape)
                        .padding(2.dp)
                        .background(ComposeAppTheme.colors.brandDefault, CircleShape)
                )
            }
        }
    }
}

@Composable
fun TransactionPeriodBar(
    text: String,
    onClick: () -> Unit,
    onBackToLatest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp)
            .background(ComposeAppTheme.colors.brandDefault15, shape)
            .border(1.dp, ComposeAppTheme.colors.borderAccentSubtle, shape)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_calendar_24),
                contentDescription = stringResource(Res.string.transactions_calendar_change_period),
                tint = ComposeAppTheme.colors.brandDefault,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = ComposeAppTheme.typography.subhead2,
                color = ComposeAppTheme.colors.textPrimary,
            )
        }
        Box(
            modifier = Modifier
                .height(IconButtonSize)
                .clickable(role = Role.Button, onClick = onBackToLatest)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.transactions_calendar_back_to_latest),
                style = ComposeAppTheme.typography.subhead1,
                color = ComposeAppTheme.colors.brandDefault,
                maxLines = 1,
            )
        }
    }
}

/** Bar text for [period]; [oldest] stands in for the open start of a "to date" period. */
@Composable
fun transactionPeriodBarText(
    period: TransactionPeriod,
    oldest: LocalDate?,
    locale: Locale = Locale.getDefault(),
): String {
    val from = period.from ?: oldest
    val to = period.to.format(locale, FormatStyle.LONG)
    return when (from) {
        null -> stringResource(Res.string.transactions_calendar_period_up_to, to)
        period.to -> stringResource(Res.string.transactions_calendar_period_day, to)
        else -> stringResource(
            Res.string.transactions_calendar_period_range,
            from.format(locale, FormatStyle.LONG),
            to,
        )
    }
}

@Composable
fun TransactionPeriodEmptyView(
    tab: FilterTransactionType,
    onChangePeriod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CalendarCrossedIcon()
        Text(
            text = stringResource(tab.emptyPeriodText()),
            style = ComposeAppTheme.typography.subhead2,
            color = ComposeAppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        ButtonSecondaryDefault(
            title = stringResource(Res.string.transactions_calendar_change_period),
            onClick = onChangePeriod,
        )
    }
}

@Composable
private fun CalendarCrossedIcon() {
    val tint = ComposeAppTheme.colors.textSecondary
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(ComposeAppTheme.colors.surfacePrimary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_calendar_24),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(48.dp),
        )
        Canvas(modifier = Modifier.size(48.dp)) {
            val unit = size.width / 24f
            val width = 1.8f * unit
            drawLine(tint, Offset(10f * unit, 13.5f * unit), Offset(14f * unit, 17.5f * unit), width, StrokeCap.Round)
            drawLine(tint, Offset(14f * unit, 13.5f * unit), Offset(10f * unit, 17.5f * unit), width, StrokeCap.Round)
        }
    }
}

private fun FilterTransactionType.emptyPeriodText(): StringResource = when (this) {
    FilterTransactionType.All -> Res.string.transactions_calendar_period_empty_all
    FilterTransactionType.Incoming -> Res.string.transactions_calendar_period_empty_incoming
    FilterTransactionType.Outgoing -> Res.string.transactions_calendar_period_empty_outgoing
    FilterTransactionType.Swap -> Res.string.transactions_calendar_period_empty_swap
    FilterTransactionType.Approve -> Res.string.transactions_calendar_period_empty_approve
}

@Composable
fun TransactionPeriodEndFooter(modifier: Modifier = Modifier) {
    subhead2_grey(
        text = stringResource(Res.string.transactions_calendar_period_end),
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 20.dp, end = 32.dp),
    )
}

private const val EMPTY_DATE = "—"

private fun LocalDate.format(locale: Locale, style: FormatStyle): String =
    DateTimeFormatter.ofLocalizedDate(style).withLocale(locale).format(this)

private fun String.capitalized(locale: Locale): String = replaceFirstChar { it.titlecase(locale) }

private val PreviewLocale = Locale.forLanguageTag("ru-RU")
private val PreviewToday = LocalDate.of(2026, 11, 23)

@Composable
private fun CalendarSheetPreview(state: TransactionCalendarState) {
    ComposeAppTheme {
        TransactionCalendarSheetContent(
            state = state,
            locale = PreviewLocale,
            onStateChange = {},
            onApply = {},
            onClose = {},
        )
    }
}

private fun previewState(period: TransactionPeriod? = null) =
    TransactionCalendarState.create(PreviewToday, DayOfWeek.MONDAY, period)

@Preview
@Composable
private fun TransactionCalendarSheetToDatePreview() {
    CalendarSheetPreview(previewState(TransactionPeriod(null, LocalDate.of(2026, 11, 12))))
}

@Preview
@Composable
private fun TransactionCalendarSheetRangePreview() {
    CalendarSheetPreview(
        previewState(TransactionPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 28)))
    )
}

@Preview
@Composable
private fun TransactionCalendarSheetSingleDayPreview() {
    CalendarSheetPreview(
        previewState().selectMode(CalendarMode.Period).onDayTap(LocalDate.of(2026, 11, 5))
    )
}

@Preview
@Composable
private fun TransactionCalendarSheetEmptyPeriodPreview() {
    CalendarSheetPreview(previewState().selectMode(CalendarMode.Period))
}

@Preview
@Composable
private fun TransactionCalendarButtonPreview() {
    ComposeAppTheme {
        Row {
            TransactionCalendarButton(active = false, onClick = {})
            TransactionCalendarButton(active = true, onClick = {})
        }
    }
}

@Preview
@Composable
private fun TransactionPeriodBarPreview() {
    ComposeAppTheme {
        Column(modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)) {
            TransactionPeriodBar(
                text = transactionPeriodBarText(
                    TransactionPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 28)), null, PreviewLocale,
                ),
                onClick = {},
                onBackToLatest = {},
            )
            TransactionPeriodBar(
                text = transactionPeriodBarText(
                    TransactionPeriod(LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 3)), null, PreviewLocale,
                ),
                onClick = {},
                onBackToLatest = {},
            )
            TransactionPeriodBar(
                text = transactionPeriodBarText(
                    TransactionPeriod(null, LocalDate.of(2026, 11, 12)), LocalDate.of(2025, 3, 4), PreviewLocale,
                ),
                onClick = {},
                onBackToLatest = {},
            )
            TransactionPeriodBar(
                text = transactionPeriodBarText(
                    TransactionPeriod(null, LocalDate.of(2026, 11, 12)), null, PreviewLocale,
                ),
                onClick = {},
                onBackToLatest = {},
            )
        }
    }
}

@Preview
@Composable
private fun TransactionPeriodEmptyViewPreview() {
    ComposeAppTheme {
        Column(modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)) {
            FilterTransactionType.entries.forEach { tab ->
                Box(modifier = Modifier.height(400.dp)) {
                    TransactionPeriodEmptyView(tab = tab, onChangePeriod = {})
                }
            }
        }
    }
}

@Preview
@Composable
private fun TransactionPeriodEndFooterPreview() {
    ComposeAppTheme {
        Box(modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)) {
            TransactionPeriodEndFooter()
        }
    }
}
