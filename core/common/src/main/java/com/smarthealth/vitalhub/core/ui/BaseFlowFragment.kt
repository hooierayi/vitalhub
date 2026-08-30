package com.smarthealth.vitalhub.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment

abstract class BaseFlowFragment : Fragment() {
    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { VitalHubTheme { ScreenContent() } }
        }

    @Composable
    protected abstract fun ScreenContent()
}

object VitalColors {
    val Teal = Color(0xFF079A96)
    val TealDark = Color(0xFF008B87)
    val TealPale = Color(0xFFEAF7F6)
    val Blue = Color(0xFF0872F5)
    val BluePale = Color(0xFFF3F7FD)
    val Danger = Color(0xFFFF4B43)
    val Success = Color(0xFF25AF3E)
    val Background = Color(0xFFF8FAFC)
    val Surface = Color.White
    val Border = Color(0xFFE1E7EC)
    val Grid = Color(0xFFF0F3F5)
    val TextPrimary = Color(0xFF121D2B)
    val TextSecondary = Color(0xFF536174)
    val TextMuted = Color(0xFF8995A3)
}

@Composable
fun VitalHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VitalColors.Teal,
            secondary = VitalColors.Blue,
            background = VitalColors.Background,
            surface = VitalColors.Surface,
            error = VitalColors.Danger,
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
            titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        ),
        content = content,
    )
}

@Composable
fun FlowPage(
    scrollable: Boolean = true,
    horizontalPadding: Dp = 18.dp,
    navigationSafe: Boolean = true,
    bottomBarSafe: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = VitalColors.Background, modifier = Modifier.fillMaxSize()) {
        val sized = Modifier.fillMaxSize().let { if (navigationSafe) it.navigationBarsPadding() else it }
        val base = sized.padding(
            start = horizontalPadding,
            top = 12.dp,
            end = horizontalPadding,
            bottom = if (bottomBarSafe) 102.dp else 12.dp,
        )
        Column(
            modifier = if (scrollable) base.verticalScroll(rememberScrollState()) else base,
            content = content,
        )
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null, top: Dp = 16.dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = top, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = VitalColors.TextPrimary)
        trailing?.let { Text(it, fontSize = 14.sp, color = VitalColors.TextSecondary) }
    }
}

@Composable
fun StatusText(text: String, color: Color = VitalColors.Teal) {
    Text(text, color = color, fontWeight = FontWeight.Medium, fontSize = 15.sp)
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    background: Color = VitalColors.Surface,
    borderColor: Color = VitalColors.Border,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    spacing: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(13.dp))
            .border(1.dp, borderColor, RoundedCornerShape(13.dp))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun InfoLine(text: String, strong: Boolean = false, color: Color = VitalColors.TextSecondary) {
    Text(
        text = text,
        fontSize = if (strong) 17.sp else 14.sp,
        lineHeight = if (strong) 23.sp else 20.sp,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        color = color,
    )
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = VitalColors.TextSecondary)
        Text(value, fontSize = 14.sp, color = VitalColors.TextPrimary)
    }
}

enum class FlowButtonStyle { PRIMARY, BLUE, DANGER, OUTLINE }

@Composable
fun FlowButton(
    label: String,
    style: FlowButtonStyle = FlowButtonStyle.PRIMARY,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val color = when (style) {
        FlowButtonStyle.PRIMARY, FlowButtonStyle.OUTLINE -> VitalColors.Teal
        FlowButtonStyle.BLUE -> VitalColors.Blue
        FlowButtonStyle.DANGER -> VitalColors.Danger
    }
    if (style == FlowButtonStyle.OUTLINE) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(51.dp),
            shape = RoundedCornerShape(7.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = if (enabled) 1f else 0.38f)),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) { Text(label, color = color.copy(alpha = if (enabled) 1f else 0.38f), fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(51.dp),
            shape = RoundedCornerShape(7.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
fun FullWidthButton(label: String, style: FlowButtonStyle = FlowButtonStyle.PRIMARY, onClick: () -> Unit) {
    FlowButton(label, style, Modifier.fillMaxWidth(), onClick)
}

@Composable
fun ProgressTrack(progress: Float, color: Color = VitalColors.Teal, height: Dp = 6.dp) {
    Box(Modifier.fillMaxWidth().height(height).background(Color(0xFFE9EDF0), CircleShape)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).background(color, CircleShape))
    }
}

@Composable
fun RowScope.MetricCard(name: String, value: String, unit: String, valueColor: Color = VitalColors.Teal) {
    Column(
        Modifier.weight(1f).height(94.dp).background(Color(0xFFF6F8FA), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
        Text(value, fontSize = 25.sp, fontWeight = FontWeight.Medium, color = valueColor)
        Text(unit, fontSize = 12.sp, color = if (valueColor == VitalColors.Blue) VitalColors.Blue else VitalColors.TextSecondary)
    }
}

@Composable
fun MetricRow(vararg metrics: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        metrics.forEach { (name, value) ->
            val parts = value.split(" ", limit = 2)
            MetricCard(name, parts.first(), parts.getOrElse(1) { "" })
        }
    }
}

@Composable
fun SelectDot(selected: Boolean, multiple: Boolean = false) {
    Box(
        Modifier.width(20.dp).height(20.dp)
            .background(if (selected) VitalColors.Teal else Color.White, if (multiple) RoundedCornerShape(5.dp) else CircleShape)
            .border(1.dp, if (selected) VitalColors.Teal else Color(0xFFBCC7D2), if (multiple) RoundedCornerShape(5.dp) else CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ChoiceTile(text: String, selected: Boolean, modifier: Modifier = Modifier, multiple: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 44.dp).background(if (selected) VitalColors.TealPale else Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) Color(0xFF80C9C6) else VitalColors.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SelectDot(selected, multiple)
        Text(text, fontSize = 14.sp, lineHeight = 19.sp, color = VitalColors.TextPrimary, maxLines = 2)
    }
}
