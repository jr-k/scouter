package app.scouter.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.WebView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import app.scouter.model.ActionItem
import app.scouter.model.Corners
import app.scouter.model.IconSpec
import app.scouter.model.ScouterAction
import app.scouter.model.ScouterDocument
import app.scouter.model.ScouterTheme
import app.scouter.model.UiStyle
import app.scouter.model.UiNode
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ScouterSheet(
    document: ScouterDocument,
    depth: Int,
    visible: Boolean,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onAction: (String, ScouterAction, String?, String) -> Unit,
) {
    val theme = document.theme
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val stackScale = (1f - depth * .025f).coerceAtLeast(.9f)
    val sheetShape = document.presentation.radius?.shapeOr(RoundedCornerShape(0.dp))
        ?: RoundedCornerShape(
            topStart = theme.cornerRadius.dp,
            topEnd = theme.cornerRadius.dp,
            bottomStart = 5.dp,
            bottomEnd = 5.dp,
        )
    val visibilityState = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) {
        visibilityState.targetState = visible
    }
    AnimatedVisibility(
        visibleState = visibilityState,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = .84f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialOffsetY = { fullHeight -> fullHeight },
        ) + fadeIn(tween(160)),
        exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(160)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = stackScale
                    alpha = (1f - depth * .13f).coerceAtLeast(.55f)
                }
                .heightIn(max = screenHeight * document.presentation.maxHeight)
                .clip(sheetShape)
                .background(color(theme.background))
                .border(
                    width = 1.dp,
                    color = color(theme.accent).copy(alpha = .18f),
                    shape = sheetShape,
                )
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 20.dp),
        ) {
            SheetChrome(document, onClose, onMinimize)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                RenderNode(document.content, theme, onAction)
            }
        }
    }
}

@Composable
private fun SheetChrome(
    document: ScouterDocument,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
) {
    val theme = document.theme
    Box(Modifier.fillMaxWidth().height(if (document.title == null) 36.dp else 52.dp)) {
        if (document.presentation.showHandle) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color(theme.muted).copy(alpha = .42f)),
            )
        }
        document.title?.let {
            IconLabel(
                text = it,
                icon = document.titleIcon,
                color = color(theme.text),
                fontSize = (14 * theme.fontScale).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        if (document.presentation.minimizable || document.presentation.dismissible) {
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                if (document.presentation.minimizable) {
                    TextButton(
                        onClick = onMinimize,
                        colors = ButtonDefaults.textButtonColors(contentColor = color(theme.muted)),
                    ) {
                        Text("Minimize", fontSize = (13 * theme.fontScale).sp)
                    }
                }
                if (document.presentation.dismissible) {
                    TextButton(
                        onClick = onClose,
                        colors = ButtonDefaults.textButtonColors(contentColor = color(theme.text)),
                    ) {
                        Text("Close", fontSize = (13 * theme.fontScale).sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RenderNode(
    node: UiNode,
    theme: ScouterTheme,
    onAction: (String, ScouterAction, String?, String) -> Unit,
    modifier: Modifier = Modifier,
    defaultFillOverride: Boolean? = null,
) {
    val nodeModifier = styledModifier(modifier, node.style, defaultFillOverride ?: node.defaultFill())
    when (node) {
        is UiNode.Column -> Column(
            modifier = nodeModifier,
            verticalArrangement = Arrangement.spacedBy(node.gap.dp),
            horizontalAlignment = node.align.horizontalAlignment(),
        ) {
            node.children.forEach { RenderNode(it, theme, onAction) }
        }
        is UiNode.Row -> Row(
            modifier = nodeModifier,
            horizontalArrangement = Arrangement.spacedBy(node.gap.dp),
            verticalAlignment = node.align.verticalAlignment(),
        ) {
            node.children.forEach { RenderRowNode(it, theme, onAction) }
        }
        is UiNode.Grid -> Column(
            modifier = nodeModifier,
            verticalArrangement = Arrangement.spacedBy(node.verticalGap.dp),
        ) {
            node.children.chunked(node.columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(node.horizontalGap.dp),
                ) {
                    row.forEach { child ->
                        Column(Modifier.weight(1f)) {
                            RenderNode(child, theme, onAction, Modifier.fillMaxWidth())
                        }
                    }
                    repeat(node.columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        is UiNode.Box -> Box(
            modifier = nodeModifier,
            contentAlignment = node.align.boxAlignment(),
        ) {
            Column { RenderNode(node.child, theme, onAction) }
        }
        is UiNode.Card -> Column(
            modifier = nodeModifier
                .clip(node.style.radius.shapeOr(RoundedCornerShape(18.dp)))
                .background(node.style.background?.let(::color) ?: tone(node.tone, theme).copy(alpha = .82f))
                .padding(node.padding.dp),
        ) {
            RenderNode(node.child, theme, onAction)
        }
        is UiNode.Text -> NodeText(node, theme, nodeModifier)
        is UiNode.Button -> NodeButton(node, theme, onAction, nodeModifier)
        is UiNode.HoldButton -> NodeHoldButton(node, theme, onAction, nodeModifier)
        is UiNode.SlideAction -> NodeSlideAction(node, theme, onAction, nodeModifier)
        is UiNode.Badge -> NodeBadge(node, theme, nodeModifier)
        is UiNode.Progress -> NodeProgress(node, theme, nodeModifier)
        is UiNode.Image -> AsyncImage(
            model = node.url,
            contentDescription = node.description,
            contentScale = when (node.contentScale.lowercase()) {
                "fit" -> ContentScale.Fit
                "inside" -> ContentScale.Inside
                "fillbounds", "fill_bounds" -> ContentScale.FillBounds
                else -> ContentScale.Crop
            },
            modifier = nodeModifier
                .then(if (node.style.height == null) Modifier.height(node.height.dp) else Modifier)
                .clip(node.style.radius.shapeOr(RoundedCornerShape(node.cornerRadius.dp))),
        )
        is UiNode.Checklist -> NodeChecklist(node, theme, nodeModifier, onAction)
        is UiNode.Link -> NodeLink(node, theme, nodeModifier, onAction)
        is UiNode.LinkList -> Column(
            modifier = nodeModifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            node.links.forEach { link ->
                NodeLink(
                    node = UiNode.Link(
                        id = link.id,
                        style = UiStyle(),
                        label = link.label,
                        url = link.url,
                        description = link.description,
                        icon = link.icon,
                    ),
                    theme = theme,
                    modifier = Modifier.fillMaxWidth(),
                    onAction = onAction,
                )
            }
        }
        is UiNode.ActionList -> NodeActionList(node, theme, nodeModifier, onAction)
        is UiNode.Map -> NodeMap(
            node,
            nodeModifier.then(if (node.style.height == null) Modifier.height(node.height.dp) else Modifier),
        )
        is UiNode.Video -> NodeVideo(
            node,
            nodeModifier.then(if (node.style.height == null) Modifier.height(node.height.dp) else Modifier),
        )
        is UiNode.Web -> NodeWeb(
            node,
            nodeModifier.then(if (node.style.height == null) Modifier.height(node.height.dp) else Modifier),
        )
        is UiNode.Divider -> HorizontalDivider(
            modifier = nodeModifier,
            color = node.color?.let(::color) ?: color(theme.muted).copy(alpha = .2f),
        )
        is UiNode.Spacer -> Spacer(nodeModifier.then(if (node.style.height == null) Modifier.height(node.height.dp) else Modifier))
    }
}

@Composable
private fun RowScope.RenderRowNode(
    node: UiNode,
    theme: ScouterTheme,
    onAction: (String, ScouterAction, String?, String) -> Unit,
) {
    val outer = node.style.weight?.let { Modifier.weight(it) } ?: Modifier
    Column(outer) {
        RenderNode(node, theme, onAction, defaultFillOverride = node.style.weight != null)
    }
}

@Composable
private fun NodeText(node: UiNode.Text, theme: ScouterTheme, modifier: Modifier) {
    val preset = when (node.variant.lowercase()) {
        "display" -> Triple(38f, FontWeight.Black, 40f)
        "title" -> Triple(24f, FontWeight.Bold, 29f)
        "subtitle" -> Triple(17f, FontWeight.SemiBold, 23f)
        "caption" -> Triple(12f, FontWeight.Medium, 17f)
        "mono" -> Triple(15f, FontWeight.Medium, 21f)
        else -> Triple(15f, FontWeight.Normal, 22f)
    }
    val textColor = node.color?.let(::color) ?: color(if (node.variant == "caption") theme.muted else theme.text)
    if (node.icon == null) {
        NodeTextContent(node, theme, preset, textColor, modifier)
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.icon.position == "start") {
                UiIcon(node.icon, textColor)
                Spacer(Modifier.width(node.icon.spacing.dp))
            }
            NodeTextContent(node, theme, preset, textColor, Modifier)
            if (node.icon.position == "end") {
                Spacer(Modifier.width(node.icon.spacing.dp))
                UiIcon(node.icon, textColor)
            }
        }
    }
}

@Composable
private fun NodeTextContent(
    node: UiNode.Text,
    theme: ScouterTheme,
    preset: Triple<Float, FontWeight, Float>,
    textColor: Color,
    modifier: Modifier,
) {
    Text(
        text = node.value,
        color = textColor,
        fontSize = ((node.fontSize ?: preset.first) * theme.fontScale).sp,
        fontWeight = node.fontWeight?.fontWeight() ?: preset.second,
        lineHeight = ((node.lineHeight ?: preset.third) * theme.fontScale).sp,
        letterSpacing = (node.letterSpacing ?: 0f).sp,
        fontFamily = when (node.fontFamily?.lowercase()) {
            "serif" -> FontFamily.Serif
            "mono", "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            else -> FontFamily.Default
        },
        fontStyle = if (node.italic) FontStyle.Italic else FontStyle.Normal,
        textAlign = node.align.textAlign(),
        maxLines = node.maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun NodeButton(
    node: UiNode.Button,
    theme: ScouterTheme,
    onAction: (String, ScouterAction, String?, String) -> Unit,
    modifier: Modifier,
) {
    val click = { onAction(node.id, node.action, null, "click") }
    val shape = node.style.radius.shapeOr(RoundedCornerShape(14.dp))
    val interactionSource = remember(node.id) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && node.enabled) node.pressedScale else 1f,
        animationSpec = tween(if (pressed) 70 else 140),
        label = "buttonPressScale",
    )
    val baseModifier = modifier
        .then(if (node.style.height == null) Modifier.height(48.dp) else Modifier)
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
    val primary = (node.color ?: node.style.background)?.let(::color) ?: color(theme.accent)
    val danger = (node.color ?: node.style.background)?.let(::color) ?: color(theme.danger)
    val explicitPressed = node.pressedColor?.let(::color)
    val primaryContainer by animateColorAsState(
        if (pressed) explicitPressed ?: lerp(primary, Color.Black, .16f) else primary,
        tween(90),
        label = "primaryButtonColor",
    )
    val dangerContainer by animateColorAsState(
        if (pressed) explicitPressed ?: lerp(danger, Color.Black, .16f) else danger,
        tween(90),
        label = "dangerButtonColor",
    )
    val quietContainer by animateColorAsState(
        if (pressed) explicitPressed ?: lerp(color(theme.surface), color(theme.text), .09f) else Color.Transparent,
        tween(90),
        label = "quietButtonColor",
    )
    when (node.variant.lowercase()) {
        "secondary" -> OutlinedButton(
            onClick = click,
            enabled = node.enabled,
            modifier = baseModifier,
            shape = shape,
            interactionSource = interactionSource,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = quietContainer,
                contentColor = node.textColor?.let(::color) ?: color(theme.text),
            ),
        ) { IconLabel(node.label, node.icon, fontWeight = FontWeight.SemiBold) }
        "ghost" -> TextButton(
            onClick = click,
            enabled = node.enabled,
            modifier = baseModifier,
            interactionSource = interactionSource,
            colors = ButtonDefaults.textButtonColors(
                containerColor = quietContainer,
                contentColor = node.textColor?.let(::color) ?: color(theme.accent),
            ),
        ) { IconLabel(node.label, node.icon, fontWeight = FontWeight.SemiBold) }
        "danger" -> Button(
            onClick = click,
            enabled = node.enabled,
            modifier = baseModifier,
            shape = shape,
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = dangerContainer,
                contentColor = node.textColor?.let(::color) ?: Color.Black,
            ),
        ) { IconLabel(node.label, node.icon, fontWeight = FontWeight.Bold) }
        else -> Button(
            onClick = click,
            enabled = node.enabled,
            modifier = baseModifier,
            shape = shape,
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryContainer,
                contentColor = node.textColor?.let(::color) ?: Color.Black,
            ),
        ) { IconLabel(node.label, node.icon, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun NodeHoldButton(
    node: UiNode.HoldButton,
    theme: ScouterTheme,
    onAction: (String, ScouterAction, String?, String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var held by remember(node.id) { mutableStateOf(false) }
    var completed by remember(node.id) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (held || completed) 1f else 0f,
        animationSpec = tween(if (held) node.durationMs else 180),
        label = "holdProgress",
    )
    LaunchedEffect(held, node.durationMs) {
        if (held) {
            delay(node.durationMs.toLong())
            if (held) {
                completed = true
                held = false
                vibrate(context, node.vibrateSequence)
                onAction(node.id, node.action, node.durationMs.toString(), "hold_complete")
            }
        }
    }
    val shape = node.style.radius.shapeOr(RoundedCornerShape(16.dp))
    val track = node.trackColor?.let(::color) ?: color(theme.surface)
    val fill = node.color?.let(::color) ?: color(theme.accent)
    Box(
        modifier = modifier
            .then(if (node.style.height == null) Modifier.height(54.dp) else Modifier)
            .clip(shape)
            .background(track)
            .graphicsLayer { alpha = if (node.enabled) 1f else .45f }
            .pointerInput(node.id, node.enabled, node.durationMs) {
                if (node.enabled) {
                    detectTapGestures(
                        onPress = {
                            completed = false
                            held = true
                            tryAwaitRelease()
                            held = false
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(progress)
                .height(54.dp)
                .background(fill),
        )
        IconLabel(
            text = if (completed) node.completedLabel ?: node.label else node.label,
            icon = node.icon,
            color = node.textColor?.let(::color) ?: color(theme.text),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NodeSlideAction(
    node: UiNode.SlideAction,
    theme: ScouterTheme,
    onAction: (String, ScouterAction, String?, String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val trackHeight = 64.dp
    val thumbSlotWidth = 64.dp
    val thumbWidthPx = with(density) { thumbSlotWidth.toPx() }
    var widthPx by remember(node.id) { mutableFloatStateOf(0f) }
    var dragOffset by remember(node.id) { mutableFloatStateOf(0f) }
    var completed by remember(node.id) { mutableStateOf(false) }
    val maxOffset = (widthPx - thumbWidthPx).coerceAtLeast(0f)
    val renderedOffset by animateFloatAsState(
        targetValue = dragOffset.coerceIn(0f, maxOffset),
        animationSpec = tween(120),
        label = "slideOffset",
    )
    val shape = node.style.radius.shapeOr(CircleShape)
    val accent = node.color?.let(::color) ?: color(theme.accent)
    val track = node.trackColor?.let(::color) ?: color(theme.surface)
    Box(
        modifier = modifier
            .then(if (node.style.height == null) Modifier.height(trackHeight) else Modifier)
            .clip(shape)
            .background(track)
            .border(1.dp, accent.copy(alpha = .24f), shape)
            .graphicsLayer { alpha = if (node.enabled) 1f else .45f }
            .onSizeChanged {
                widthPx = it.width.toFloat()
                dragOffset = dragOffset.coerceIn(0f, (widthPx - thumbWidthPx).coerceAtLeast(0f))
            }
            .pointerInput(node.id, node.enabled, maxOffset, node.threshold) {
                if (node.enabled) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (!completed) dragOffset = (dragOffset + amount).coerceIn(0f, maxOffset)
                        },
                        onDragCancel = { dragOffset = 0f },
                        onDragEnd = {
                            if (maxOffset > 0f && dragOffset / maxOffset >= node.threshold) {
                                completed = true
                                dragOffset = maxOffset
                                vibrate(context, node.vibrateSequence)
                                onAction(node.id, node.action, "1", "slide_complete")
                                scope.launch {
                                    delay(700)
                                    completed = false
                                    dragOffset = 0f
                                }
                            } else {
                                dragOffset = 0f
                            }
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(
                    if (widthPx == 0f) 0f
                    else ((renderedOffset + thumbWidthPx) / widthPx).coerceIn(0f, 1f),
                )
                .height(trackHeight)
                .clip(
                    RoundedCornerShape(
                        topEnd = trackHeight / 2,
                        bottomEnd = trackHeight / 2,
                    ),
                )
                .background(accent.copy(alpha = .16f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                IconLabel(
                    text = node.completedLabel ?: node.label,
                    icon = node.icon,
                    color = node.textColor?.let(::color) ?: color(theme.text),
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                ShimmerIconLabel(
                    text = node.label,
                    icon = node.icon,
                    color = node.textColor?.let(::color) ?: color(theme.text),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(renderedOffset.roundToInt(), 0) }
                .size(thumbSlotWidth),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = if (accent.luminance() > .5f) Color.Black else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun NodeBadge(node: UiNode.Badge, theme: ScouterTheme, modifier: Modifier) {
    IconLabel(
        text = node.label,
        icon = node.icon,
        color = if (node.tone == "accent") Color.Black else color(theme.text),
        fontSize = (12 * theme.fontScale).sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(node.style.radius.shapeOr(RoundedCornerShape(50)))
            .background(node.style.background?.let(::color) ?: tone(node.tone, theme))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun NodeProgress(node: UiNode.Progress, theme: ScouterTheme, modifier: Modifier) {
    Column(modifier) {
    if (node.label != null || node.showValue) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            node.label?.let { Text(it, color = color(theme.muted), fontSize = 12.sp) }
            if (node.showValue) Text("${(node.value * 100).toInt()}%", color = color(theme.accent), fontSize = 12.sp)
        }
        Spacer(Modifier.height(7.dp))
    }
    LinearProgressIndicator(
        progress = { node.value },
        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),
        color = node.color?.let(::color) ?: color(theme.accent),
        trackColor = node.trackColor?.let(::color) ?: color(theme.surface),
        drawStopIndicator = {},
    )
    }
}

@Composable
private fun NodeChecklist(
    node: UiNode.Checklist,
    theme: ScouterTheme,
    modifier: Modifier,
    onAction: (String, ScouterAction, String?, String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        node.items.forEach { item ->
            key(item.id) {
                var isChecked by remember(node.id, item.id, item.checked) { mutableStateOf(item.checked) }
                val toggle = {
                    if (item.enabled) {
                        isChecked = !isChecked
                        onAction(item.id, node.action, isChecked.toString(), "change")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = item.enabled, onClick = toggle)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { toggle() },
                        enabled = item.enabled,
                        colors = CheckboxDefaults.colors(
                            checkedColor = color(theme.accent),
                            checkmarkColor = Color.Black,
                            uncheckedColor = color(theme.muted),
                        ),
                    )
                    Text(
                        text = item.label,
                        color = color(if (item.enabled) theme.text else theme.muted),
                        fontSize = (15 * theme.fontScale).sp,
                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeLink(
    node: UiNode.Link,
    theme: ScouterTheme,
    modifier: Modifier,
    onAction: (String, ScouterAction, String?, String) -> Unit,
) {
    TextButton(
        onClick = { onAction(node.id, ScouterAction.OpenUrl(node.url, false), null, "click") },
        modifier = modifier,
        shape = node.style.radius.shapeOr(RoundedCornerShape(13.dp)),
        colors = ButtonDefaults.textButtonColors(
            containerColor = color(theme.surface),
            contentColor = color(theme.text),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            node.icon?.let { UiIcon(it, color(theme.accent)) }
            Column(Modifier.weight(1f)) {
                Text(node.label, fontWeight = FontWeight.SemiBold)
                node.description?.let {
                    Text(it, color = color(theme.muted), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Text("Open", color = color(theme.accent), fontSize = 13.sp)
        }
    }
}

@Composable
private fun NodeActionList(
    node: UiNode.ActionList,
    theme: ScouterTheme,
    modifier: Modifier,
    onAction: (String, ScouterAction, String?, String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        node.items.forEach { item ->
            key(item.id) {
                ActionListItem(item, theme, onAction)
            }
        }
    }
}

@Composable
private fun ActionListItem(
    item: ActionItem,
    theme: ScouterTheme,
    onAction: (String, ScouterAction, String?, String) -> Unit,
) {
    TextButton(
        onClick = { onAction(item.id, item.action, item.detail, "click") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = color(theme.surface),
            contentColor = color(theme.text),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item.icon?.let { UiIcon(it, color(theme.accent)) }
            Column(Modifier.weight(1f)) {
                Text(item.label, fontWeight = FontWeight.SemiBold)
                item.description?.let {
                    Text(it, color = color(theme.muted), fontSize = 12.sp, lineHeight = 17.sp)
                }
                item.detail?.takeIf { it != item.label }?.let {
                    Text(it, color = color(theme.muted), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Text(item.buttonLabel, color = color(theme.accent), fontSize = 13.sp)
        }
    }
}

@Composable
private fun NodeMap(node: UiNode.Map, modifier: Modifier) {
    val mapKey = "${node.latitude}:${node.longitude}:${node.zoom}:${node.mapType}:${node.gestures}:${node.controls}"
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                tag = mapKey
                onCreate(null)
                onStart()
                onResume()
                getMapAsync { configureGoogleMap(it, node) }
            }
        },
        update = { mapView ->
            if (mapView.tag != mapKey) {
                mapView.tag = mapKey
                mapView.getMapAsync { configureGoogleMap(it, node) }
            }
        },
        onRelease = {
            it.onPause()
            it.onStop()
            it.onDestroy()
        },
        modifier = modifier.clip(node.style.radius.shapeOr(RoundedCornerShape(18.dp))),
    )
}

private fun configureGoogleMap(map: GoogleMap, node: UiNode.Map) {
    val position = LatLng(node.latitude, node.longitude)
    map.clear()
    map.mapType = when (node.mapType.lowercase()) {
        "satellite" -> GoogleMap.MAP_TYPE_SATELLITE
        "terrain" -> GoogleMap.MAP_TYPE_TERRAIN
        "hybrid" -> GoogleMap.MAP_TYPE_HYBRID
        else -> GoogleMap.MAP_TYPE_NORMAL
    }
    map.uiSettings.apply {
        isScrollGesturesEnabled = node.gestures
        isZoomGesturesEnabled = node.gestures
        isRotateGesturesEnabled = node.gestures
        isTiltGesturesEnabled = node.gestures
        isZoomControlsEnabled = node.controls
        isMapToolbarEnabled = node.controls
        isCompassEnabled = node.controls
    }
    map.addMarker(
        MarkerOptions()
            .position(position)
            .title(node.markerLabel),
    )
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, node.zoom.toFloat()))
}

@Composable
private fun NodeVideo(node: UiNode.Video, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                tag = node.url
                if (node.controls) setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setVideoURI(Uri.parse(node.url))
                setOnPreparedListener { player ->
                    player.isLooping = false
                    if (node.autoplay) start() else seekTo(1)
                }
            }
        },
        update = {
            if (it.tag != node.url) {
                it.tag = node.url
                it.setVideoURI(Uri.parse(node.url))
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = modifier
            .clip(node.style.radius.shapeOr(RoundedCornerShape(18.dp)))
            .background(Color.Black),
    )
}

@Composable
private fun NodeWeb(node: UiNode.Web, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = node.javaScriptEnabled
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                loadDataWithBaseURL(null, node.html, "text/html", "UTF-8", null)
            }
        },
        update = {
            if (it.tag != node.html) {
                it.tag = node.html
                it.settings.javaScriptEnabled = node.javaScriptEnabled
                it.loadDataWithBaseURL(null, node.html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier.clip(node.style.radius.shapeOr(RoundedCornerShape(18.dp))),
    )
}

private fun color(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color.Magenta)

private fun tone(value: String, theme: ScouterTheme): Color = color(
    when (value.lowercase()) {
        "accent" -> theme.accent
        "danger" -> theme.danger
        "background" -> theme.background
        else -> theme.surface
    },
)

private fun String.horizontalAlignment(): Alignment.Horizontal = when (lowercase()) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun String.verticalAlignment(): Alignment.Vertical = when (lowercase()) {
    "top" -> Alignment.Top
    "bottom" -> Alignment.Bottom
    else -> Alignment.CenterVertically
}

private fun String.textAlign(): TextAlign = when (lowercase()) {
    "center" -> TextAlign.Center
    "end", "right" -> TextAlign.End
    else -> TextAlign.Start
}

private fun String.boxAlignment(): Alignment = when (lowercase()) {
    "topstart", "top_start" -> Alignment.TopStart
    "top", "topcenter", "top_center" -> Alignment.TopCenter
    "topend", "top_end" -> Alignment.TopEnd
    "start", "centerstart", "center_start" -> Alignment.CenterStart
    "end", "centerend", "center_end" -> Alignment.CenterEnd
    "bottomstart", "bottom_start" -> Alignment.BottomStart
    "bottom", "bottomcenter", "bottom_center" -> Alignment.BottomCenter
    "bottomend", "bottom_end" -> Alignment.BottomEnd
    else -> Alignment.Center
}

@Composable
private fun ShimmerIconLabel(
    text: String,
    icon: IconSpec?,
    color: Color,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "slideLabelShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "slideLabelShimmerProgress",
    )
    val center = widthPx * (-.35f + progress * 1.7f)
    val halfBand = widthPx * .22f
    val brush = Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = .52f),
            color,
            color.copy(alpha = .52f),
        ),
        start = Offset(center - halfBand, 0f),
        end = Offset(center + halfBand, 0f),
    )
    Row(
        modifier = Modifier.onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon?.position == "start") {
            UiIcon(icon, color)
            Spacer(Modifier.width(icon.spacing.dp))
        }
        Text(
            text = text,
            style = TextStyle(
                brush = brush,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        if (icon?.position == "end") {
            Spacer(Modifier.width(icon.spacing.dp))
            UiIcon(icon, color)
        }
    }
}

@Composable
private fun IconLabel(
    text: String,
    icon: IconSpec?,
    modifier: Modifier = Modifier,
    color: Color? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val resolvedColor = color ?: LocalContentColor.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon?.position == "start") {
            UiIcon(icon, resolvedColor)
            Spacer(Modifier.width(icon.spacing.dp))
        }
        Text(text = text, color = resolvedColor, fontSize = fontSize, fontWeight = fontWeight)
        if (icon?.position == "end") {
            Spacer(Modifier.width(icon.spacing.dp))
            UiIcon(icon, resolvedColor)
        }
    }
}

@Composable
private fun UiIcon(icon: IconSpec, fallbackColor: Color) {
    Icon(
        imageVector = icon.name.iconVector(),
        contentDescription = null,
        tint = icon.color?.let(::color) ?: fallbackColor,
        modifier = Modifier.size(icon.size.dp),
    )
}

private fun String.iconVector(): ImageVector = when (this) {
    "add" -> Icons.Filled.Add
    "back" -> Icons.Filled.ArrowBack
    "calendar" -> Icons.Filled.CalendarToday
    "call", "phone" -> Icons.Filled.Call
    "check" -> Icons.Filled.Check
    "close" -> Icons.Filled.Close
    "delete" -> Icons.Filled.Delete
    "directions" -> Icons.Filled.Directions
    "edit" -> Icons.Filled.Edit
    "email" -> Icons.Filled.Email
    "favorite" -> Icons.Filled.Favorite
    "home" -> Icons.Filled.Home
    "info" -> Icons.Filled.Info
    "link" -> Icons.Filled.Link
    "list" -> Icons.Filled.List
    "location" -> Icons.Filled.LocationOn
    "lock" -> Icons.Filled.Lock
    "unlock" -> Icons.Filled.LockOpen
    "menu" -> Icons.Filled.Menu
    "message" -> Icons.Filled.Message
    "more" -> Icons.Filled.MoreVert
    "notification" -> Icons.Filled.Notifications
    "open" -> Icons.Filled.OpenInNew
    "pause" -> Icons.Filled.Pause
    "person" -> Icons.Filled.Person
    "play" -> Icons.Filled.PlayArrow
    "search" -> Icons.Filled.Search
    "settings" -> Icons.Filled.Settings
    "share" -> Icons.Filled.Share
    "star" -> Icons.Filled.Star
    "task" -> Icons.Filled.TaskAlt
    "time" -> Icons.Filled.AccessTime
    "warning" -> Icons.Filled.Warning
    else -> Icons.Filled.Info
}

private fun UiNode.defaultFill(): Boolean = when (this) {
    is UiNode.Text, is UiNode.Badge, is UiNode.Spacer -> false
    else -> true
}

private fun styledModifier(initial: Modifier, style: UiStyle, defaultFill: Boolean): Modifier {
    var modifier = initial.padding(
        start = style.margin.start.dp,
        top = style.margin.top.dp,
        end = style.margin.end.dp,
        bottom = style.margin.bottom.dp,
    )
    modifier = when (style.width) {
        "fill" -> modifier.fillMaxWidth()
        "wrap" -> modifier
        null -> if (defaultFill) modifier.fillMaxWidth() else modifier
        else -> modifier.width(style.width.toFloat().dp)
    }
    style.height?.let { modifier = modifier.height(it.dp) }
    if (style.minHeight != null || style.maxHeight != null) {
        modifier = modifier.heightIn(
            min = style.minHeight?.dp ?: Dp.Unspecified,
            max = style.maxHeight?.dp ?: Dp.Unspecified,
        )
    }
    style.aspectRatio?.let { modifier = modifier.aspectRatio(it) }
    if (style.opacity < 1f) modifier = modifier.graphicsLayer { alpha = style.opacity }
    val shape = style.radius.shapeOr(RoundedCornerShape(0.dp))
    if (style.radius.hasRadius()) modifier = modifier.clip(shape)
    style.background?.let { modifier = modifier.background(color(it), shape) }
    if (style.borderColor != null && style.borderWidth > 0f) {
        modifier = modifier.border(style.borderWidth.dp, color(style.borderColor), shape)
    }
    return modifier.padding(
        start = style.padding.start.dp,
        top = style.padding.top.dp,
        end = style.padding.end.dp,
        bottom = style.padding.bottom.dp,
    )
}

private fun Corners.hasRadius(): Boolean =
    topStart > 0f || topEnd > 0f || bottomEnd > 0f || bottomStart > 0f

private fun Corners.shapeOr(fallback: RoundedCornerShape): RoundedCornerShape =
    if (hasRadius()) {
        RoundedCornerShape(
            topStart = topStart.dp,
            topEnd = topEnd.dp,
            bottomEnd = bottomEnd.dp,
            bottomStart = bottomStart.dp,
        )
    } else {
        fallback
    }

@Suppress("DEPRECATION")
private fun vibrate(context: Context, sequence: List<Long>) {
    if (sequence.isEmpty()) return
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (vibrator.hasVibrator()) {
        vibrator.vibrate(VibrationEffect.createWaveform(sequence.toLongArray(), -1))
    }
}

private fun String.fontWeight(): FontWeight = when (lowercase()) {
    "thin", "100" -> FontWeight.Thin
    "extralight", "200" -> FontWeight.ExtraLight
    "light", "300" -> FontWeight.Light
    "medium", "500" -> FontWeight.Medium
    "semibold", "600" -> FontWeight.SemiBold
    "bold", "700" -> FontWeight.Bold
    "extrabold", "800" -> FontWeight.ExtraBold
    "black", "900" -> FontWeight.Black
    else -> FontWeight.Normal
}
