/*
 * Copyright (C) 2026 IchthysOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crdroid.settings.fragments.lockscreen.editor2

import android.graphics.Color as AColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settings.R
import com.android.settings.ichthys.guard.ui.theme.Guard
import com.android.settings.ichthys.guard.ui.theme.GuardTokens
import com.android.settingslib.ichthys.clock.DepthClockView
import com.android.settingslib.ichthys.clock.LockNotifications
import com.android.settingslib.ichthys.clock.LockWeather
import com.android.settingslib.ichthys.clock.ProjectedClock
import com.android.settingslib.ichthys.clock.SquareDigits
import com.android.settingslib.ichthys.sky.LivingSky
import com.crdroid.settings.fragments.lockscreen.ClockUtils

/**
 * IchthysOS Shoal: the editor's colours, fonts and corners, all from the theme (GuardTheme around
 * the editor reads the framework's ichthys_* tokens). Nothing in the editor's own chrome is a hex.
 */
private val t: GuardTokens
    @Composable get() = Guard.tokens

/**
 * The sheet over the lower third for [sheet]: its name and Reset in a header, the Clock sheet's
 * parts as chips, and its content scrolling under them. Every change goes into the draft only
 * ([onDraft]; [onCommit] closes a slider's undo step as the finger lifts).
 */
@Composable
fun EditorSheetHost(
    sheet: EditorSheet,
    clockTab: ClockTab,
    state: EditorState,
    catalog: LockShortcuts.Catalog?,
    geo: LockGeometry,
    onDraft: (LockDesign) -> Unit,
    onCommit: () -> Unit,
    onWallpaper: (WallpaperDraft) -> Unit,
    onClockTab: (ClockTab) -> Unit,
    onSkyMinute: (Float?) -> Unit = {},
    onAutoFit: () -> Unit = {},
    onReset: () -> Unit = {},
) {
    val tk = t
    val title = when (sheet) {
        EditorSheet.WALLPAPER -> R.string.ichthys_ed2_wallpaper
        EditorSheet.CLOCK -> R.string.ichthys_ed2_clock
        EditorSheet.NOTIFICATIONS -> R.string.ichthys_ed2_notifications
        EditorSheet.SHORTCUTS -> R.string.ichthys_ed2_shortcuts
        EditorSheet.FINGERPRINT -> R.string.ichthys_ed2_fingerprint
        EditorSheet.LOOKS -> R.string.ichthys_ed2_looks
        EditorSheet.NONE -> R.string.ichthys_ed2_clock
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(title), fontFamily = tk.display, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = tk.text, modifier = Modifier.weight(1f))
            OutlineAction(stringResource(R.string.ichthys_editor_reset), onReset)
        }
        if (sheet == EditorSheet.CLOCK) {
            val tabs = listOf(
                ClockTab.FACE to R.string.ichthys_ed2_tab_face,
                ClockTab.COLOUR to R.string.ichthys_ed2_tab_colour,
                ClockTab.LAYOUT to R.string.ichthys_ed2_layout,
                ClockTab.DATE to R.string.ichthys_ed2_date_line,
                ClockTab.WEATHER to R.string.ichthys_ed2_weather,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                tabs.forEach { (tab, label) ->
                    val on = tab == clockTab
                    Text(
                        stringResource(label).uppercase(),
                        fontFamily = tk.mono, fontSize = 10.sp, letterSpacing = 1.1.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) tk.text else tk.muted,
                        modifier = Modifier
                            .clickable { onClockTab(tab) }
                            .padding(horizontal = 6.dp)
                            .padding(top = 8.dp, bottom = 10.dp)
                            .drawBehind {
                                if (on) {
                                    val h = 2.dp.toPx()
                                    drawRect(tk.accent, Offset(0f, size.height + 10.dp.toPx() - h), Size(size.width, h))
                                }
                            },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(tk.hairlineWidth).background(tk.hairline))
        }
        // IchthysOS polish: another part's content cross-fades in (no jump).
        AnimatedContent(
            targetState = sheet to clockTab,
            transitionSpec = { (fadeIn(tween(160, delayMillis = 40)) togetherWith fadeOut(tween(90))) },
            label = "sheet",
        ) { (shown, tab) ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 12.dp)) {
                when (shown) {
                    EditorSheet.CLOCK ->
                        when (tab) {
                            ClockTab.FACE -> FaceSheet(state, onDraft)
                            ClockTab.COLOUR -> ColourGroup(state, onDraft, onCommit)
                            ClockTab.LAYOUT -> LayoutSheet(state, geo, onDraft, onCommit, onAutoFit)
                            ClockTab.DATE -> DateSheet(state.draft, onDraft)
                            ClockTab.WEATHER -> WeatherSheet(state.draft, onDraft, onCommit)
                        }
                    EditorSheet.NOTIFICATIONS -> NotificationSheet(state, onDraft, onCommit)
                    EditorSheet.SHORTCUTS -> ShortcutsSheet(state.draft, catalog, onDraft)
                    EditorSheet.FINGERPRINT -> FingerprintSheet(state.draft, onDraft)
                    EditorSheet.WALLPAPER -> WallpaperSheet(state, onWallpaper, onDraft, onCommit, onSkyMinute)
                    EditorSheet.LOOKS -> LooksSheet(state, onDraft)
                    EditorSheet.NONE -> Unit
                }
            }
        }
    }
}

// ---- building blocks -------------------------------------------------------------------------

/** A group's name, mono caps; [trailing] sits at its end (a value, an Auto chip). */
@Composable
private fun Label(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), fontFamily = t.mono, fontSize = 10.sp, letterSpacing = 1.2.sp,
            color = t.muted, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        content()
    }
}

/**
 * A Shoal card: outlined by a hairline (filled instead on tonal surfaces); the accent outlines the
 * chosen one.
 */
@Composable
private fun Modifier.shoalCard(on: Boolean, shape: Shape): Modifier {
    val tk = t
    return clip(shape)
        .then(if (tk.hairlineSurfaces) Modifier else Modifier.background(tk.card))
        .then(
            when {
                on -> Modifier.border(1.5.dp, tk.accent, shape)
                tk.hairlineSurfaces -> Modifier.border(tk.hairlineWidth, tk.hairline, shape)
                else -> Modifier
            })
}

/** A choice shown as a picture of itself (a small lock screen) with its name under it. */
@Composable
private fun PictureChip(label: String, on: Boolean, onClick: () -> Unit, width: Dp = 64.dp,
    thumb: @Composable () -> Unit) {
    Column(
        Modifier.width(width).shoalCard(on, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(width * 2f).clip(RoundedCornerShape(9.dp))) { thumb() }
        // Two lines, always (the cards stay one height): "Touch heat", "Shoal Lattice" fit whole.
        Text(label, fontFamily = t.mono, fontSize = 10.sp, letterSpacing = 0.4.sp, lineHeight = 12.sp,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (on) t.accent else t.muted, modifier = Modifier.padding(top = 5.dp, bottom = 3.dp))
    }
}

/**
 * [d] with the clock in its home place (no move, turn or size): what the sheets' thumbnails
 * show. The same object while only the place changes, so dragging the clock doesn't rebuild
 * every thumbnail on every frame (each is a whole lock screen with its clock view).
 * IchthysOS: and at full opacity: the clock opacity is the live clock's (edited in the Colour
 * group, shown on the big preview); a thumbnail shows the option itself (a 29 % clock made every
 * face card nearly black). Dragging the opacity slider no longer rebuilds the thumbnails either.
 */
@Composable
private fun rememberAtHome(d: LockDesign): LockDesign {
    val home = d.copy(dx = 0f, dy = 0f, scale = 1f, rot = 0f, opacity = 100, look = "")
    return remember(home) { home }
}

/** A clock face as just the clock (no wallpaper, no lock screen around it), with its name. */
@Composable
private fun FaceChip(label: String, on: Boolean, onClick: () -> Unit, thumb: @Composable () -> Unit) {
    Column(
        Modifier.width(112.dp).shoalCard(on, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(FACE_CARD_H).clip(RoundedCornerShape(9.dp)).background(t.ink)) {
            thumb()
        }
        Text(label, fontFamily = t.mono, fontSize = 10.sp, letterSpacing = 0.4.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (on) t.accent else t.muted, modifier = Modifier.padding(top = 5.dp, bottom = 3.dp))
    }
}

/**
 * [design]'s clock alone, upright, on nothing: the lock screen is drawn whole at the card's width
 * (no weather, no wallpaper), then framed on what the face really shows (the preview's measured
 * clock), centred and fitted into the card, so tall, wide and small faces all show whole.
 */
@Composable
private fun ClockOnly(design: LockDesign) {
    val screen = rememberScreen()
    val pad = with(LocalDensity.current) { 8.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardW = constraints.maxWidth.toFloat()
        val cardH = constraints.maxHeight.toFloat()
        val fullH = maxWidth * (screen.height.toFloat() / screen.width)
        // IchthysOS: the face at full opacity (the clock opacity is the live clock's, not the face's).
        val shown = remember(design) {
            design.copy(depth = DepthClockView.DEPTH_OFF, weather = false, placement = DepthClockView.PLACEMENT_FLAT,
                opacity = 100)
        }
        var clock by remember(shown) { mutableStateOf<Rect?>(null) }
        Box(
            Modifier.fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .requiredHeight(fullH)
                .graphicsLayer {
                    val r = clock
                    if (r == null || r.width <= 0f || r.height <= 0f) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val s = minOf((cardW - 2 * pad) / r.width, (cardH - 2 * pad) / r.height,
                        CLOCK_CARD_MAX_ZOOM)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = s
                    scaleY = s
                    translationX = cardW / 2f - r.center.x * s
                    translationY = cardH / 2f - r.center.y * s
                }
        ) {
            LockScreenPreview(shown, LockPicture.EMPTY, Modifier.fillMaxSize(), chrome = false,
                rounded = false, onLayout = { l ->
                    // The face with its date line (the Depth face's is its own element). The
                    // layout's rectangles are on the screen (px); the preview is k as big.
                    val c = l.rects[LockElement.CLOCK]
                    val dt = l.rects[LockElement.DATE]
                    val u = if (c != null && dt != null) {
                        Rect(minOf(c.left, dt.left), minOf(c.top, dt.top), maxOf(c.right, dt.right),
                            maxOf(c.bottom, dt.bottom))
                    } else c ?: dt
                    val r = u?.let {
                        Rect(it.left * l.k, it.top * l.k, it.right * l.k, it.bottom * l.k)
                    }
                    if (r != clock) clock = r
                })
        }
    }
}

/** How far a small face may be blown up to fill its card (the plain faces stay readable). */
private const val CLOCK_CARD_MAX_ZOOM = 1.3f
private val FACE_CARD_H = 124.dp

/** Some digits in a face's own type, with its name: the font picker's card. */
@Composable
private fun TypeCard(label: String, sample: String, on: Boolean, onClick: () -> Unit,
    family: FontFamily?, square: Boolean = false, style: TextStyle = TextStyle()) {
    val tk = t
    Column(
        Modifier.width(78.dp).shoalCard(on, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(top = 10.dp, bottom = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(40.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // IchthysOS: the Square font has no typeface; the text keeps the card its size (unseen)
            // and the digits are drawn over it as the Depth face draws them.
            Text(sample, fontSize = 30.sp, fontFamily = family, maxLines = 1,
                color = if (square) Color.Transparent else tk.text,
                style = if (square) TextStyle() else style)
            if (square) {
                val ink = tk.text
                Box(
                    Modifier.matchParentSize().drawWithCache {
                        val textSize = 26.sp.toPx()
                        val path = android.graphics.Path()
                        val w = SquareDigits.advance(textSize) * sample.length
                        SquareDigits.textPath(sample, 0, sample.length, (size.width - w) / 2f,
                            (size.height + SquareDigits.capHeight(textSize)) / 2f, textSize, path)
                        val glyphs = path.asComposePath()
                        onDrawBehind { drawPath(glyphs, ink) }
                    }
                )
            }
        }
        Text(label, fontFamily = tk.mono, fontSize = 10.sp, letterSpacing = 0.4.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = if (on) tk.accent else tk.muted,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
    }
}

/**
 * A mono caps chip: the chosen one is filled with the accent. [glyph] draws a small picture before
 * the label (in the label's colour).
 */
@Composable
private fun MonoChip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    height: Dp = 36.dp, enabled: Boolean = true, glyph: (DrawScope.(Color) -> Unit)? = null) {
    val tk = t
    val shape = RoundedCornerShape(9.dp)
    val ink = if (on) tk.onAccent else tk.text
    Row(
        modifier.height(height).alpha(if (enabled) 1f else 0.45f).clip(shape)
            .background(if (on) tk.accent else Color.Transparent)
            .border(1.dp, if (on) tk.accent else tk.hairline, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (glyph != null) Canvas(Modifier.size(22.dp, 16.dp)) { glyph(ink) }
        Text(label.uppercase(), fontFamily = tk.mono, fontSize = 10.sp, letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

/** A row of mutually exclusive options (more than three scroll, at their own widths). */
@Composable
private fun Segmented(options: List<String>, selected: Int,
    glyphs: List<DrawScope.(Color) -> Unit>? = null, onSelect: (Int) -> Unit) {
    if (options.size > 3) {
        ChipRow {
            options.forEachIndexed { i, label -> MonoChip(label, i == selected, { onSelect(i) }) }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            MonoChip(label, i == selected, { onSelect(i) }, Modifier.weight(1f),
                height = if (glyphs != null) 44.dp else 38.dp, glyph = glyphs?.get(i))
        }
    }
}

/** Three lines of text, ragged the way [align] lines them up (left, centre, right on screen). */
private fun alignGlyph(align: Int): DrawScope.(Color) -> Unit = { c ->
    val u = size.width / 22f
    val stroke = 1.6.dp.toPx()
    listOf(3f to 18f, 8f to 11f, 13f to 15f).forEach { (y, w) ->
        val x0 = when (align) {
            0 -> 2f
            1 -> (22f - w) / 2f
            else -> 20f - w
        }
        drawLine(c, Offset(x0 * u, y * u), Offset((x0 + w) * u, y * u), stroke, StrokeCap.Round)
    }
}

/** A colour as a rounded square; the chosen one gets an accent ring. */
@Composable
private fun Swatch(color: Color, on: Boolean, onClick: () -> Unit, brush: Brush? = null) {
    val tk = t
    val outer = RoundedCornerShape(13.dp)
    val inner = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(44.dp)
            .then(if (on) Modifier.border(2.dp, tk.accent, outer) else Modifier)
            .clickable(onClick = onClick)
            .padding(5.dp)
            .clip(inner)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
            .border(1.dp, tk.hairline, inner)
    )
}

@Composable
private fun ShoalSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val tk = t
    Slider(
        value = value, onValueChange = onChange, valueRange = range, modifier = modifier.fillMaxWidth(),
        onValueChangeFinished = onFinished,
        colors = SliderDefaults.colors(thumbColor = tk.accent, activeTrackColor = tk.accent,
            inactiveTrackColor = tk.track, activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent),
    )
}

/** A mono caps name with its value at the end, and the slider under them. */
@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    shown: String, onFinished: () -> Unit = {}, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), fontFamily = t.mono, fontSize = 10.sp, letterSpacing = 1.2.sp,
                color = t.muted, modifier = Modifier.weight(1f))
            Text(shown, fontFamily = t.mono, fontSize = 11.sp, color = t.text)
        }
        ShoalSlider(value, onChange, range, onFinished = onFinished)
    }
}

/** Shoal's switch: a rounded square track, filled with the accent when on, a square knob. */
@Composable
private fun ShoalToggle(on: Boolean, enabled: Boolean) {
    val tk = t
    val x by animateDpAsState(if (on) 23.dp else 3.dp, spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "knob")
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(46.dp, 26.dp).alpha(if (enabled) 1f else 0.38f).clip(shape)
            .background(if (on) tk.accent else Color.Transparent)
            .border(1.dp, if (on) tk.accent else tk.outline, shape)
    ) {
        Box(Modifier.offset(x = x, y = 3.dp).size(18.dp).clip(RoundedCornerShape(5.dp))
            .background(if (on) tk.onAccent else tk.text))
    }
}

/** An on/off row: a name (and what it does) and the switch, under a hairline. */
@Composable
private fun SwitchRow(label: String, on: Boolean, enabled: Boolean = true, hint: String? = null,
    onChange: (Boolean) -> Unit) {
    val tk = t
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(tk.hairlineWidth).background(tk.hairline))
    Row(
        Modifier.fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onChange(!on) } else Modifier)
            .heightIn(min = 54.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (enabled) tk.text else tk.muted)
            if (hint != null) {
                Text(hint, fontSize = 11.sp, color = tk.muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        ShoalToggle(on, enabled)
    }
}

/** A small outlined action (Reset, Save, Now); [accent] for the one that matters. */
@Composable
private fun OutlineAction(label: String, onClick: () -> Unit, accent: Boolean = false, enabled: Boolean = true) {
    val tk = t
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier.height(34.dp).alpha(if (enabled) 1f else 0.45f).clip(shape)
            .border(1.dp, if (accent) tk.accent else tk.outline, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), fontFamily = tk.mono, fontSize = 10.sp, letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold, color = if (accent) tk.accent else tk.text)
    }
}

/** A line of explanation under a group. */
@Composable
private fun Note(text: String) {
    Text(text, fontSize = 11.sp, color = t.muted,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
}

// ---- the clock: face ---------------------------------------------------------------------------

/** The clock's face, type and finish, the glass. */
@Composable
private fun FaceSheet(state: EditorState, onDraft: (LockDesign) -> Unit) {
    val d = state.draft
    val tk = t
    val names = remember { ClockUtils.getClockNames() }
    // Newest faces first (Depth, One UI, …); built only as they scroll into view.
    val faces = remember { ClockUtils.CLOCK_LAYOUTS.indices.reversed().toList() }
    Label(stringResource(R.string.ichthys_ed2_faces))
    val home = rememberAtHome(d)
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(faces.size, key = { faces[it] }) { i ->
            val style = faces[i]
            val face = remember(home, style) { home.copy(style = style) }
            FaceChip(names[style], style == d.style, { onDraft(d.copy(style = style, look = "custom")) }) {
                ClockOnly(face)
            }
        }
    }
    if (d.isDepthFace) {
        Label(stringResource(R.string.ichthys_ed2_font))
        val fonts =
            listOf(DepthClockView.FONT_ROUNDED to R.string.ichthys_ed2_font_rounded,
                DepthClockView.FONT_SOFT to R.string.ichthys_ed2_font_soft,
                DepthClockView.FONT_TEKO to R.string.ichthys_ed2_font_teko,
                DepthClockView.FONT_CONDENSED to R.string.ichthys_ed2_font_condensed,
                DepthClockView.FONT_POSTER to R.string.ichthys_ed2_font_poster,
                DepthClockView.FONT_COMPACT to R.string.ichthys_ed2_font_compact,
                DepthClockView.FONT_GROTESK to R.string.ichthys_ed2_font_grotesk,
                DepthClockView.FONT_HEAVY to R.string.ichthys_ed2_font_heavy,
                DepthClockView.FONT_BUBBLE to R.string.ichthys_ed2_font_bubble,
                DepthClockView.FONT_BLOCK to R.string.ichthys_ed2_font_block,
                DepthClockView.FONT_INLINE to R.string.ichthys_ed2_font_inline,
                DepthClockView.FONT_TALL to R.string.ichthys_ed2_font_tall,
                DepthClockView.FONT_FLEX to R.string.ichthys_ed2_font_flex,
                DepthClockView.FONT_WIDE to R.string.ichthys_ed2_font_wide,
                DepthClockView.FONT_THIN to R.string.ichthys_ed2_font_thin,
                DepthClockView.FONT_SERIF to R.string.ichthys_ed2_font_serif,
                DepthClockView.FONT_MONO to R.string.ichthys_ed2_font_mono,
                DepthClockView.FONT_SQUARE to R.string.ichthys_ed2_font_square)
        ChipRow {
            fonts.forEach { (font, label) ->
                val family = remember(font) { digitFamily(font) }
                TypeCard(stringResource(label), "12", d.font == font,
                    { onDraft(d.copy(font = font, look = "custom")) }, family,
                    square = font == DepthClockView.FONT_SQUARE)
            }
        }

        // IchthysOS: the Depth face's finish: solid, a gradient, glass the picture shows through,
        // or the heat a touch leaves. (On the wall the projected style is the finish.)
        if (!d.isProjected) {
            Label(stringResource(R.string.ichthys_ed2_finish))
            val family = remember(d.font) {
                digitFamily(if (d.font == DepthClockView.FONT_SQUARE) DepthClockView.FONT_ROUNDED else d.font)
            }
            val finishes =
                listOf(DepthClockView.FILL_SOLID to R.string.ichthys_ed2_finish_solid,
                    DepthClockView.FILL_GRADIENT to R.string.ichthys_ed2_finish_gradient,
                    DepthClockView.FILL_GLASS to R.string.ichthys_ed2_finish_glass,
                    DepthClockView.FILL_HEAT to R.string.ichthys_depth_fill_live)
            ChipRow {
                finishes.forEach { (fill, label) ->
                    val look = when (fill) {
                        DepthClockView.FILL_GRADIENT ->
                            TextStyle(brush = Brush.verticalGradient(listOf(tk.text, tk.accent)))
                        DepthClockView.FILL_GLASS ->
                            TextStyle(color = tk.text.copy(alpha = 0.9f), drawStyle = Stroke(width = 2.2f))
                        DepthClockView.FILL_HEAT ->
                            TextStyle(brush = Brush.radialGradient(listOf(tk.accent, tk.text.copy(alpha = 0.35f))))
                        else -> TextStyle(color = tk.text)
                    }
                    TypeCard(stringResource(label), "10", d.fill == fill,
                        { onDraft(d.copy(fill = fill, look = "custom")) }, family, style = look)
                }
            }

            // IchthysOS: the glass: clear or frosted (each as a picture of itself), and how milky
            // it is, from smoky to milky, or Auto, which picks it from the picture.
            if (d.fill == DepthClockView.FILL_GLASS) {
                Label(stringResource(R.string.ichthys_ed2_finish_glass))
                ChipRow {
                    listOf(0 to R.string.ichthys_ed2_glass_clear, 1 to R.string.ichthys_ed2_glass_frosted)
                        .forEach { (frost, label) ->
                            val glass = remember(home, frost) { home.copy(glassFrost = frost) }
                            PictureChip(stringResource(label), d.glassFrost.coerceIn(0, 1) == frost,
                                { onDraft(d.copy(glassFrost = frost, look = "custom")) }) {
                                MiniLock(glass, state.picture, Modifier.fillMaxSize())
                            }
                        }
                }
                val auto = d.glassOpacity < 0
                Label(stringResource(R.string.ichthys_ed2_glass_opacity)) {
                    MonoChip(stringResource(R.string.ichthys_ed2_auto), auto,
                        { onDraft(d.copy(glassOpacity = if (auto) 50 else -1, look = "custom")) },
                        height = 28.dp)
                }
                if (!auto) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        ShoalSlider(d.glassOpacity.toFloat(),
                            { onDraft(d.copy(glassOpacity = it.toInt(), look = "custom")) }, 0f..100f)
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ichthys_ed2_glass_smoky).uppercase(), fontFamily = tk.mono,
                                fontSize = 10.sp, letterSpacing = 1.sp, color = tk.muted, modifier = Modifier.weight(1f))
                            Text("${d.glassOpacity}", fontFamily = tk.mono, fontSize = 11.sp, color = tk.text)
                            Text(stringResource(R.string.ichthys_ed2_glass_milky).uppercase(), fontFamily = tk.mono,
                                fontSize = 10.sp, letterSpacing = 1.sp, color = tk.muted, textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Note(stringResource(R.string.ichthys_ed2_glass_auto_note))
                }
            }
        }
    }
    Spacer(Modifier.size(8.dp))
}

private fun digitFamily(font: Int): FontFamily =
    FontFamily(androidx.compose.ui.text.font.Typeface(DepthClockView.digitTypeface(font)))

@Composable
private fun ColourGroup(state: EditorState, onDraft: (LockDesign) -> Unit, onCommit: () -> Unit) {
    val d = state.draft
    val context = LocalContext.current
    val accent = remember { Color(context.getColor(android.R.color.system_accent1_100)) }
    Label(stringResource(R.string.ichthys_ed2_colour_wallpaper))
    ChipRow {
        Swatch(Color.White, d.colorMode == LockKeys.MODE_DEFAULT,
            { onDraft(d.copy(colorMode = LockKeys.MODE_DEFAULT, look = "custom")) })
        Swatch(accent, d.colorMode == LockKeys.MODE_ACCENT,
            { onDraft(d.copy(colorMode = LockKeys.MODE_ACCENT, look = "custom")) })
        state.picture.palette.forEach { c ->
            Swatch(Color(c), d.colorMode == LockKeys.MODE_CUSTOM && d.color == c,
                { onDraft(d.copy(colorMode = LockKeys.MODE_CUSTOM, color = c, look = "custom")) })
        }
    }
    Label(stringResource(R.string.ichthys_ed2_colour_custom))
    val hsv = remember(d.color) { FloatArray(3).also { AColor.colorToHSV(d.color, it) } }
    // IchthysOS: the slider keeps its own hue. Read back from the colour, 360 came back as 0
    // (the same red) and a pale colour lost its hue, so the thumb jumped to the start.
    val hue = remember { mutableFloatStateOf(hsv[0]) }
    val ownColor = remember { intArrayOf(d.color) }
    if (d.color != ownColor[0]) {
        ownColor[0] = d.color
        hue.floatValue = hsv[0]
    }
    SliderRow(stringResource(R.string.ichthys_ed2_hue), hue.floatValue, 0f..360f,
        "${hue.floatValue.toInt()}°", onCommit) { h ->
        val c = AColor.HSVToColor(floatArrayOf(h, hsv[1].coerceAtLeast(0.3f), hsv[2].coerceAtLeast(0.9f)))
        hue.floatValue = h
        ownColor[0] = c
        onDraft(d.copy(colorMode = LockKeys.MODE_CUSTOM, color = c, look = "custom"))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_saturation), hsv[1], 0f..1f, "${(hsv[1] * 100).toInt()} %",
        onCommit) { sat ->
        val c = AColor.HSVToColor(floatArrayOf(hue.floatValue, sat, hsv[2].coerceAtLeast(0.5f)))
        ownColor[0] = c
        onDraft(d.copy(colorMode = LockKeys.MODE_CUSTOM, color = c, look = "custom"))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_opacity), d.opacity.toFloat(), 20f..100f, "${d.opacity} %",
        onCommit) {
        onDraft(d.copy(opacity = it.toInt(), look = "custom"))
    }
}

// ---- the clock: layout (size, depth, placement, the surface) ----------------------------------

@Composable
private fun LayoutSheet(state: EditorState, geo: LockGeometry, onDraft: (LockDesign) -> Unit,
    onCommit: () -> Unit, onAutoFit: () -> Unit) {
    val d = state.draft
    val home = rememberAtHome(d)
    if (!d.isProjected) {
        SliderRow(stringResource(R.string.ichthys_ed2_size), d.scale, 0.4f..1.8f, "${(d.scale * 100).toInt()} %",
            onCommit) {
            onDraft(d.copy(scale = it, look = "custom"))
        }
        SliderRow(stringResource(R.string.ichthys_ed2_rotation), d.rot, -90f..90f, "${d.rot.toInt()}°", onCommit) {
            onDraft(d.copy(rot = if (kotlin.math.abs(it) < 3f) 0f else it, look = "custom"))
        }
    }

    Label(stringResource(R.string.ichthys_ed2_tab_depth))
    // IchthysOS: the digits are never distorted any more (DepthClockView fits the whole clock
    // evenly in every mode), so "Shape" is what Overlap does now; a saved Shape shows as Overlap.
    val modes =
        listOf(DepthClockView.DEPTH_OFF to R.string.ichthys_ed2_depth_off,
            DepthClockView.DEPTH_OVERLAP to R.string.ichthys_ed2_depth_overlap,
            DepthClockView.DEPTH_XRAY to R.string.ichthys_ed2_depth_xray)
    ChipRow {
        modes.forEach { (mode, label) ->
            val on = d.depth == mode ||
                (mode == DepthClockView.DEPTH_OVERLAP && d.depth == DepthClockView.DEPTH_SHAPE)
            val shown = remember(home, mode, state.picture) { withDepth(home, mode, state.picture, geo) }
            PictureChip(stringResource(label), on, { onDraft(withDepth(d, mode, state.picture, geo)) }) {
                MiniLock(shown, state.picture, Modifier.fillMaxSize())
            }
        }
    }
    if (state.picture.cutout == null) Note(stringResource(R.string.ichthys_ed2_depth_no_subject))

    // IchthysOS projected clock: the Depth face's digits flat over the picture, or on a surface of
    // it (ProjectedClock); then its styles, its surface kinds and the surface's own controls. The
    // handles are dragged on the preview.
    if (d.isDepthFace) {
        Label(stringResource(R.string.ichthys_ed2_placement))
        ChipRow {
            listOf(DepthClockView.PLACEMENT_FLAT to R.string.ichthys_ed2_placement_flat,
                DepthClockView.PLACEMENT_PROJECTED to R.string.ichthys_ed2_placement_projected)
                .forEach { (mode, label) ->
                    val shown = remember(home, mode, state.picture) { home.copy(placement = mode) }
                    PictureChip(stringResource(label), d.placement == mode,
                        { onDraft(d.copy(placement = mode, look = "custom")) }) {
                        MiniLock(shown, state.picture, Modifier.fillMaxSize())
                    }
                }
        }
        if (d.isProjected) {
            Label(stringResource(R.string.ichthys_ed2_proj_style))
            val styles = listOf(
                ProjectedClock.STYLE_PAINTED to R.string.ichthys_ed2_proj_painted,
                ProjectedClock.STYLE_LIGHT to R.string.ichthys_ed2_proj_light,
                ProjectedClock.STYLE_ENGRAVED to R.string.ichthys_ed2_proj_engraved,
                ProjectedClock.STYLE_SIGNAGE to R.string.ichthys_ed2_proj_signage,
                ProjectedClock.STYLE_BEAM to R.string.ichthys_ed2_proj_beam,
                ProjectedClock.STYLE_SHADOW to R.string.ichthys_ed2_proj_shadow,
            )
            ChipRow {
                styles.forEach { (style, label) ->
                    val shown = remember(home, style, state.picture) { home.copy(projectedStyle = style) }
                    PictureChip(stringResource(label), d.projectedStyle == style,
                        { onDraft(d.copy(projectedStyle = style, look = "custom")) }) {
                        MiniLock(shown, state.picture, Modifier.fillMaxSize())
                    }
                }
            }
            if (d.projectedStyle == ProjectedClock.STYLE_BEAM) Note(stringResource(R.string.ichthys_ed2_proj_beam_note))

            Label(stringResource(R.string.ichthys_ed2_surface))
            val current = d.resolvedSurface(state.picture)
            val kinds = listOf(
                ProjectedClock.KIND_PLANE to R.string.ichthys_ed2_surface_plane,
                ProjectedClock.KIND_FOLD to R.string.ichthys_ed2_surface_fold,
                ProjectedClock.KIND_CURVE to R.string.ichthys_ed2_surface_curve,
                ProjectedClock.KIND_MESH to R.string.ichthys_ed2_surface_mesh,
            )
            ChipRow {
                kinds.forEach { (kind, label) ->
                    val shown = remember(home, kind, current) {
                        home.copy(surface = current.asKind(kind), surfaceKind = kind)
                    }
                    PictureChip(stringResource(label), current.kind == kind, {
                        onDraft(d.copy(surface = current.asKind(kind), surfaceKind = kind, look = "custom"))
                    }) {
                        MiniLock(shown, state.picture, Modifier.fillMaxSize())
                    }
                }
            }
            Note(stringResource(
                when (current.kind) {
                    ProjectedClock.KIND_FOLD -> R.string.ichthys_ed2_surface_fold_hint
                    ProjectedClock.KIND_CURVE -> R.string.ichthys_ed2_surface_curve_hint
                    ProjectedClock.KIND_MESH -> R.string.ichthys_ed2_surface_mesh_hint
                    else -> R.string.ichthys_ed2_projected_hint
                }))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlineAction(
                    stringResource(if (state.fitting) R.string.ichthys_ed2_fit_working else R.string.ichthys_ed2_fit_auto),
                    onAutoFit, accent = true, enabled = !state.fitting && state.picture.source != null)
                if (current.kind == ProjectedClock.KIND_PLANE) {
                    OutlineAction(stringResource(R.string.ichthys_ed2_projected_flat), {
                        // A rectangle of the digit block's own proportions where the plane is.
                        val block = geo.depthBlock(d.font, d.datePos, d.showDate)
                        val aspect = block.halfWidth * 2f / maxOf(1f, block.height)
                        onDraft(d.copy(
                            surface = ProjectedClock.Surface(ProjectedClock.KIND_PLANE,
                                ProjectedClock.flatQuad(current.corners(), aspect, geo.width, geo.height)),
                            look = "custom"))
                    })
                }
                OutlineAction(stringResource(R.string.ichthys_ed2_projected_reset), {
                    onDraft(d.copy(surface = ProjectedClock.Surface.default(current.kind), look = "custom"))
                })
            }
            if (state.fitLines.isNotEmpty()) Note(stringResource(R.string.ichthys_ed2_fit_lines, state.fitLines.size))
            if (current.kind == ProjectedClock.KIND_FOLD) {
                Label(stringResource(R.string.ichthys_ed2_fold_split))
                Segmented(
                    listOf(stringResource(R.string.ichthys_ed2_fold_split_1), stringResource(R.string.ichthys_ed2_fold_split_2),
                        stringResource(R.string.ichthys_ed2_fold_split_3)),
                    current.split - 1,
                ) { onDraft(d.copy(surface = current.withSplit(it + 1), look = "custom")) }
            }
            if (current.kind == ProjectedClock.KIND_CURVE) {
                SliderRow(stringResource(R.string.ichthys_ed2_curve_cylinder), current.bend[2], -1f..1f,
                    "${(current.bend[2] * 100).toInt()} %", onCommit) {
                    onDraft(d.copy(surface = current.withBend(2, if (kotlin.math.abs(it) < 0.04f) 0f else it),
                        look = "custom"))
                }
            }
        }
    }

    if (!d.isProjected) {
        Spacer(Modifier.size(14.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(t.hairlineWidth).background(t.hairline))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(stringResource(R.string.ichthys_ed2_move), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = t.text)
                Text(stringResource(R.string.ichthys_ed2_move_hint), fontSize = 11.sp, color = t.muted,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(12.dp))
            OutlineAction(stringResource(R.string.ichthys_editor_reset),
                { onDraft(d.copy(dx = 0f, dy = 0f, scale = 1f, rot = 0f, look = "custom")) })
        }
    }
    Spacer(Modifier.size(6.dp))
}

/** The date line: what it shows, where it goes, how it lines up. */
@Composable
private fun DateSheet(d: LockDesign, onDraft: (LockDesign) -> Unit) {
    if (!d.isDepthFace) {
        Note(stringResource(R.string.ichthys_ed2_date_other_face))
        return
    }
    Label(stringResource(R.string.ichthys_ed2_date_shows))
    Segmented(
        listOf(stringResource(R.string.ichthys_ed2_date), stringResource(R.string.ichthys_ed2_details),
            stringResource(R.string.ichthys_ed2_hidden)),
        when {
            !d.showDate -> 2
            d.details -> 1
            else -> 0
        },
    ) {
        onDraft(d.copy(showDate = it != 2, details = it == 1, look = "custom"))
    }
    if (d.showDate) {
        Label(stringResource(R.string.ichthys_ed2_position))
        Segmented(listOf(stringResource(R.string.ichthys_ed2_above), stringResource(R.string.ichthys_ed2_below)),
            d.datePos) { onDraft(d.copy(datePos = it, look = "custom")) }
        Label(stringResource(R.string.ichthys_ed2_align))
        AlignChoice(d) { onDraft(d.copy(dateAlign = it, look = "custom")) }
        Note(stringResource(R.string.ichthys_ed2_date_drag_hint))
    }
    Spacer(Modifier.size(6.dp))
}

/** Left, centre or right, each with a picture of its ragged lines (the settings' order differs). */
@Composable
private fun AlignChoice(d: LockDesign, onAlign: (Int) -> Unit) {
    val shown = when (d.dateAlign) {
        DepthClockView.ALIGN_START -> 0
        DepthClockView.ALIGN_END -> 2
        else -> 1
    }
    Segmented(
        listOf(stringResource(R.string.ichthys_ed2_left), stringResource(R.string.ichthys_ed2_centre),
            stringResource(R.string.ichthys_ed2_right)),
        shown,
        glyphs = listOf(alignGlyph(0), alignGlyph(1), alignGlyph(2)),
    ) {
        onAlign(
            when (it) {
                0 -> DepthClockView.ALIGN_START
                2 -> DepthClockView.ALIGN_END
                else -> DepthClockView.ALIGN_CENTRE
            })
    }
}

/**
 * [d] in depth [mode]. Shape is the Depth face's: choosing it switches to that face and, when the
 * clock would come out squashed (factor under 0.7), picks the size that fills the room above the
 * subject with digits of a natural height (still pinchable afterwards).
 */
fun withDepth(d: LockDesign, mode: Int, picture: LockPicture, geo: LockGeometry): LockDesign {
    if (mode != DepthClockView.DEPTH_SHAPE) return d.copy(depth = mode, look = "custom")
    var s = d.copy(depth = mode, style = DEPTH_STYLE, rot = 0f, look = "custom")
    if (picture.outline != null && geo.shapeFactor(picture.outline, s) < 0.7f) {
        s = s.copy(scale = geo.sizeForShape(picture.outline, s))
    }
    return s
}

// ---- weather ---------------------------------------------------------------------------------

/**
 * IchthysOS lock screen weather: on/off, what the line shows, where it goes (also by dragging it on
 * the preview), its icons as a row of the packs' own pictures, and its size.
 */
@Composable
private fun WeatherSheet(d: LockDesign, onDraft: (LockDesign) -> Unit, onCommit: () -> Unit) {
    val context = LocalContext.current
    // The sheet follows the weather arriving (the preview shows a stand-in until then).
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val l: () -> Unit = { version++ }
        LockWeather.addListener(context, l)
        onDispose { LockWeather.removeListener(l) }
    }
    val real = remember(version) { LockWeather.current }
    fun edit(n: LockDesign) = onDraft(n.copy(look = "custom"))

    SwitchRow(stringResource(R.string.ichthys_ed2_weather_show), d.weather) { edit(d.copy(weather = it)) }
    if (real == null) {
        Note(stringResource(R.string.ichthys_ed2_weather_no_data))
        Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
            OutlineAction(stringResource(R.string.ichthys_ed2_weather_settings), {
                try {
                    context.startActivity(
                        com.android.internal.util.crdroid.OmniJawsClient.get().getSettingsIntent()
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // OmniJaws is not installed.
                }
            })
        }
    }
    if (!d.weather) {
        Spacer(Modifier.size(6.dp))
        return
    }

    Label(stringResource(R.string.ichthys_ed2_date_shows))
    ChipRow {
        MonoChip(stringResource(R.string.ichthys_ed2_weather_temp), d.weatherTemp,
            { edit(d.copy(weatherTemp = !d.weatherTemp)) })
        MonoChip(stringResource(R.string.ichthys_ed2_weather_condition), d.weatherCondition,
            { edit(d.copy(weatherCondition = !d.weatherCondition)) })
        MonoChip(stringResource(R.string.ichthys_ed2_weather_highlow), d.weatherHighLow,
            { edit(d.copy(weatherHighLow = !d.weatherHighLow)) })
        MonoChip(stringResource(R.string.ichthys_ed2_weather_location), d.weatherLocation,
            { edit(d.copy(weatherLocation = !d.weatherLocation)) })
    }

    Label(stringResource(R.string.ichthys_ed2_position))
    // Next to the date only where there is a date line of the Depth face to sit in.
    val places = ArrayList<Pair<Int, String>>()
    if (d.isDepthFace && d.showDate) {
        places.add(LockWeather.POS_DATE to stringResource(R.string.ichthys_ed2_weather_by_date))
    }
    places.add(LockWeather.POS_UNDER to stringResource(R.string.ichthys_ed2_weather_under))
    places.add(LockWeather.POS_TOP_START to stringResource(R.string.ichthys_ed2_weather_top_left))
    places.add(LockWeather.POS_TOP_END to stringResource(R.string.ichthys_ed2_weather_top_right))
    val place = d.weatherPlace
    Segmented(places.map { it.second }, places.indexOfFirst { it.first == place }.coerceAtLeast(0)) {
        edit(d.copy(weatherPos = places[it].first))
    }
    if (place == LockWeather.POS_UNDER) {
        // Under the clock it lines up like the date line (one alignment for both).
        Label(stringResource(R.string.ichthys_ed2_align))
        AlignChoice(d) { edit(d.copy(dateAlign = it)) }
    }

    Label(stringResource(R.string.ichthys_ed2_weather_icons))
    val now = real ?: LockWeather.sample
    val code = now?.code ?: 30
    val follow = now?.iconPack ?: "org.omnirom.omnijaws.tabler"
    ChipRow {
        WeatherIconChip(stringResource(R.string.ichthys_ed2_weather_icons_follow), follow, code,
            d.weatherIcons == LockWeather.ICONS_FOLLOW) { edit(d.copy(weatherIcons = LockWeather.ICONS_FOLLOW)) }
        WeatherIconChip(stringResource(R.string.ichthys_ed2_none), null, code,
            d.weatherIcons == LockWeather.ICONS_NONE) { edit(d.copy(weatherIcons = LockWeather.ICONS_NONE)) }
        LockWeather.ICON_PACKS.forEach { (pack, name) ->
            WeatherIconChip(name, pack, code, d.weatherIcons == pack) { edit(d.copy(weatherIcons = pack)) }
        }
    }

    // The slider's own name is the heading (no second "Size" above it).
    SliderRow(stringResource(R.string.ichthys_ed2_size), d.weatherSize.toFloat(), 70f..150f,
        "${d.weatherSize} %", onCommit) { edit(d.copy(weatherSize = it.toInt())) }
    Spacer(Modifier.size(8.dp))
}

/** One icon pack as its own picture of the weather now ([pack] null: no icon). */
@Composable
private fun WeatherIconChip(label: String, pack: String?, code: Int, on: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val tk = t
    val tint = tk.text.toArgb()
    val image =
        remember(pack, code, tint) {
            pack?.let { p ->
                LockWeather.icon(context, p, code)?.let { dr ->
                    val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                    val m = dr.mutate()
                    if (LockWeather.isMono(p)) m.setTint(tint)
                    m.setBounds(0, 0, 96, 96)
                    m.draw(android.graphics.Canvas(b))
                    b.asImageBitmap()
                }
            }
        }
    Column(
        Modifier.width(72.dp).shoalCard(on, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(top = 10.dp, bottom = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (image != null) Image(image, null, Modifier.size(30.dp))
            else Text("∅", fontSize = 18.sp, color = tk.text)
        }
        // Two lines, always, as the picture cards' names ("Weather app" fits whole).
        Text(label, fontFamily = tk.mono, fontSize = 10.sp, letterSpacing = 0.4.sp, lineHeight = 12.sp,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (on) tk.accent else tk.muted, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
    }
}

// ---- notifications: the Quiet dock -------------------------------------------------------------

/**
 * IchthysOS Quiet dock: how notifications sit (dock, cards, glance, each as a picture of itself
 * with its chrome), the cards' anchor (a slider, or the card dragged on the preview; Auto follows
 * the clock), the dock's idle dim, and what the lock screen may show.
 */
@Composable
private fun NotificationSheet(state: EditorState, onDraft: (LockDesign) -> Unit, onCommit: () -> Unit) {
    val d = state.draft
    SwitchRow(stringResource(R.string.ichthys_ed2_notif_show), d.showNotifications) {
        onDraft(d.copy(showNotifications = it))
    }
    if (!d.showNotifications) {
        Spacer(Modifier.size(6.dp))
        return
    }
    Label(stringResource(R.string.ichthys_ed2_notif_mode))
    val home = rememberAtHome(d)
    val modes = listOf(
        LockNotifications.MODE_DOCK to R.string.ichthys_ed2_notif_dock,
        LockNotifications.MODE_CARDS to R.string.ichthys_ed2_notif_cards,
        LockNotifications.MODE_GLANCE to R.string.ichthys_ed2_notif_glance,
    )
    ChipRow {
        modes.forEach { (mode, label) ->
            val shown = remember(home, mode) { home.copy(notifMode = mode) }
            PictureChip(stringResource(label), d.notifMode == mode,
                { onDraft(d.copy(notifMode = mode, look = "custom")) }, width = 72.dp) {
                MiniLockChrome(shown, state.picture, Modifier.fillMaxSize())
            }
        }
    }
    Note(stringResource(
        when (d.notifMode) {
            LockNotifications.MODE_CARDS -> R.string.ichthys_ed2_notif_cards_hint
            LockNotifications.MODE_GLANCE -> R.string.ichthys_ed2_notif_glance_hint
            else -> R.string.ichthys_ed2_notif_dock_hint
        }))
    if (d.notifMode == LockNotifications.MODE_CARDS) {
        val auto = d.notifAnchor == LockNotifications.ANCHOR_AUTO
        Label(stringResource(R.string.ichthys_ed2_notif_anchor)) {
            MonoChip(stringResource(R.string.ichthys_ed2_auto), auto,
                { onDraft(d.copy(notifAnchor = if (auto) 45 else LockNotifications.ANCHOR_AUTO, look = "custom")) },
                height = 28.dp)
        }
        if (!auto) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                ShoalSlider(d.notifAnchor.toFloat(),
                    { onDraft(d.copy(notifAnchor = LockNotifications.clampAnchor(it.toInt()), look = "custom")) },
                    LockNotifications.ANCHOR_MIN.toFloat()..LockNotifications.ANCHOR_MAX.toFloat(),
                    onFinished = onCommit)
                Text("${d.notifAnchor} %", fontFamily = t.mono, fontSize = 11.sp, color = t.text,
                    modifier = Modifier.align(Alignment.End))
            }
        }
        Note(stringResource(R.string.ichthys_ed2_notif_anchor_hint))
    } else {
        SwitchRow(stringResource(R.string.ichthys_ed2_dock_dim), d.dockDim,
            hint = stringResource(R.string.ichthys_ed2_dock_dim_hint)) {
            onDraft(d.copy(dockDim = it, look = "custom"))
        }
    }
    SwitchRow(stringResource(R.string.ichthys_ed2_notif_content), d.privateNotifications,
        enabled = d.notifMode != LockNotifications.MODE_GLANCE) {
        onDraft(d.copy(privateNotifications = it))
    }
    Spacer(Modifier.size(6.dp))
}

// ---- shortcuts -------------------------------------------------------------------------------

@Composable
private fun ShortcutsSheet(d: LockDesign, catalog: LockShortcuts.Catalog?, onDraft: (LockDesign) -> Unit) {
    var start by remember { mutableStateOf(true) }
    Spacer(Modifier.size(10.dp))
    Segmented(listOf(stringResource(R.string.ichthys_ed2_shortcut_left), stringResource(R.string.ichthys_ed2_shortcut_right)),
        if (start) 0 else 1) { start = it == 0 }
    if (catalog == null) {
        Note(stringResource(R.string.ichthys_ed2_shortcuts_unavailable))
        return
    }
    val current = if (start) d.shortcutStart else d.shortcutEnd
    fun pick(id: String) = onDraft(if (start) d.copy(shortcutStart = id) else d.copy(shortcutEnd = id))
    val tk = t
    val tint = tk.text.toArgb()
    val items = listOf<LockShortcuts.Affordance?>(null) + catalog.all
    Spacer(Modifier.size(6.dp))
    // Rows of four.
    items.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { a ->
                val id = a?.id ?: ""
                val on = id == current
                Column(
                    Modifier.weight(1f).shoalCard(on, RoundedCornerShape(12.dp))
                        .clickable { pick(id) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val icon = remember(a, tint) {
                        a?.icon?.let { dr ->
                            val b = android.graphics.Bitmap.createBitmap(72, 72, android.graphics.Bitmap.Config.ARGB_8888)
                            val m = dr.mutate()
                            m.setTint(tint)
                            m.setBounds(0, 0, 72, 72)
                            m.draw(android.graphics.Canvas(b))
                            b.asImageBitmap()
                        }
                    }
                    if (icon != null) Image(icon, null, Modifier.size(24.dp))
                    else Text("∅", fontSize = 18.sp, color = tk.text)
                    // Two lines, always ("Device controls", "Do Not Disturb" fit whole).
                    Text(a?.name ?: stringResource(R.string.ichthys_ed2_none), fontFamily = tk.mono,
                        fontSize = 10.sp, letterSpacing = 0.4.sp, lineHeight = 12.sp,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        color = if (on) tk.accent else tk.muted, minLines = 2, maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                    // IchthysOS: one SystemUI can't show yet (Device controls without a
                    // controls app, a wallet without cards): it says so.
                    if (a != null && !a.enabled) {
                        Text(stringResource(R.string.ichthys_ed2_shortcut_needs_setup), fontSize = 9.sp,
                            color = tk.muted, maxLines = 1)
                    }
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Spacer(Modifier.size(6.dp))
}

// ---- fingerprint ------------------------------------------------------------------------------

/**
 * IchthysOS: the fingerprint icon on the sensor, as its styles (each drawn as itself), and the
 * animation a touch plays. Colours and the rest stay on the Fingerprint effects page.
 */
@Composable
private fun FingerprintSheet(d: LockDesign, onDraft: (LockDesign) -> Unit) {
    val tk = t
    val iconNames = stringArrayResource(R.array.ichthys_fp_icon_style_entries)
    Label(stringResource(R.string.ichthys_ed2_fp_icon))
    ChipRow {
        iconNames.forEachIndexed { style, name ->
            val on = d.fpIcon == style
            Column(
                Modifier.width(72.dp).shoalCard(on, RoundedCornerShape(12.dp))
                    .clickable { onDraft(d.copy(fpIcon = style, look = "custom")) }
                    .padding(top = 10.dp, bottom = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(44.dp)) {
                    fingerprintGlyph(style, center, size.width / 2f * 0.72f, if (on) tk.accent else tk.text, density)
                }
                Text(name, fontFamily = tk.mono, fontSize = 10.sp, letterSpacing = 0.4.sp, lineHeight = 12.sp,
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    color = if (on) tk.accent else tk.muted, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
            }
        }
    }
    Label(stringResource(R.string.ichthys_ed2_fp_effect))
    val effectNames = stringArrayResource(R.array.ichthys_fp_anim_effect_entries)
    val effectValues = stringArrayResource(R.array.ichthys_fp_anim_effect_values)
    ChipRow {
        effectNames.forEachIndexed { i, name ->
            val value = effectValues.getOrNull(i)?.toIntOrNull() ?: return@forEachIndexed
            MonoChip(name, d.fpEffect == value, { onDraft(d.copy(fpEffect = value, look = "custom")) })
        }
    }
    Note(stringResource(R.string.ichthys_ed2_fp_note))
    Spacer(Modifier.size(6.dp))
}

// ---- wallpaper and looks --------------------------------------------------------------------

@Composable
private fun WallpaperSheet(
    state: EditorState,
    onWallpaper: (WallpaperDraft) -> Unit,
    onDraft: (LockDesign) -> Unit,
    onCommit: () -> Unit,
    onSkyMinute: (Float?) -> Unit,
) {
    var picked by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked = it }
    picked?.let { uri ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(stringResource(R.string.ichthys_wallpaper_use_for)) },
            confirmButton = {
                TextButton(onClick = {
                    picked = null
                    onWallpaper(WallpaperDraft.Photo(uri, home = false))
                }) { Text(stringResource(R.string.ichthys_wallpaper_lock)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    picked = null
                    onWallpaper(WallpaperDraft.Photo(uri, home = true))
                }) { Text(stringResource(R.string.ichthys_wallpaper_both)) }
            },
        )
    }
    Label(stringResource(R.string.ichthys_ed2_wallpaper))
    ChipRow {
        PictureChip(stringResource(R.string.ichthys_ed2_wallpaper_current),
            state.wallpaper is WallpaperDraft.Current, { onWallpaper(WallpaperDraft.Current) }) {
            Box(Modifier.fillMaxSize().background(t.card), contentAlignment = Alignment.Center) {
                Text("↺", fontSize = 26.sp, color = t.accent)
            }
        }
        PictureChip(stringResource(R.string.ichthys_wallpaper_photo), state.wallpaper is WallpaperDraft.Photo, {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            Box(Modifier.fillMaxSize().background(t.card), contentAlignment = Alignment.Center) {
                Text("+", fontSize = 32.sp, color = t.accent)
            }
        }
        BUILTIN_WALLPAPERS.forEach { (id, res, label) ->
            val w = state.wallpaper
            PictureChip(stringResource(label), w is WallpaperDraft.Builtin && w.id == id,
                { onWallpaper(WallpaperDraft.Builtin(id)) }) {
                WallpaperThumb(res)
            }
        }
    }
    Note(stringResource(R.string.ichthys_ed2_wallpaper_note))
    var credits by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) {
        OutlineAction(stringResource(R.string.ichthys_ed2_wallpaper_credits), { credits = true })
    }
    if (credits) {
        AlertDialog(
            onDismissRequest = { credits = false },
            title = { Text(stringResource(R.string.ichthys_ed2_wallpaper_credits)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    WALLPAPER_CREDITS.forEach {
                        Text(it, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { credits = false }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
    LivingSkySection(state, onDraft, onCommit, onSkyMinute)
}

/**
 * IchthysOS Living Sky: the photo's sky follows the time and the weather. Only for a photo with a
 * sky (else the switch is off, with why); the scrub shows any time of today in the preview (not
 * saved).
 */
@Composable
private fun LivingSkySection(
    state: EditorState,
    onDraft: (LockDesign) -> Unit,
    onCommit: () -> Unit,
    onSkyMinute: (Float?) -> Unit,
) {
    val context = LocalContext.current
    val d = state.draft
    val pic = state.picture
    val usable = pic.livingSky
    fun edit(n: LockDesign) = onDraft(n.copy(look = "custom"))
    Label(stringResource(R.string.ichthys_ed2_sky))
    SwitchRow(stringResource(R.string.ichthys_ed2_sky_follow), d.livingSky && usable, usable) {
        edit(d.copy(livingSky = it))
        if (!it) onSkyMinute(null)
    }
    if (!usable) {
        val reason =
            when {
                pic.backdrop == null -> R.string.ichthys_ed2_sky_no_photo
                pic.skyInfo == null -> R.string.ichthys_ed2_sky_looking
                else -> R.string.ichthys_ed2_sky_none
            }
        Note(stringResource(reason))
        Spacer(Modifier.size(8.dp))
        return
    }
    // IchthysOS: a night picture is graded the other way round: by day its sky is relit to a
    // clean day sky and the land lifted (LivingSky's day path); scrub the day below to see it.
    if (pic.skyInfo?.darkSky == true) Note(stringResource(R.string.ichthys_ed2_sky_dark))
    if (!d.livingSky) {
        Note(stringResource(R.string.ichthys_ed2_sky_note))
        Spacer(Modifier.size(8.dp))
        return
    }
    SwitchRow(stringResource(R.string.ichthys_ed2_sky_weather), d.skyWeather) {
        edit(d.copy(skyWeather = it))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_sky_intensity), d.skyIntensity.toFloat(), 10f..100f,
        "${d.skyIntensity}%", onCommit) { edit(d.copy(skyIntensity = it.toInt())) }

    // Preview the day: any minute of today, with the sky's colour across the day under it.
    // IchthysOS: "now", today and the sun times follow the clock while the sheet stays open (taken
    // once, "Now · 14:02" and its thumb fell behind the preview's own now, which moves each minute).
    var clockTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L)
            clockTick++
        }
    }
    val times = remember(clockTick) { LivingSky.sunTimes(context) }
    val today = remember(clockTick) { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }
    val nowMinute = remember(clockTick) {
        val c = java.util.Calendar.getInstance()
        c.get(java.util.Calendar.HOUR_OF_DAY) * 60f + c.get(java.util.Calendar.MINUTE)
    }
    // IchthysOS: the thumb and the label follow the finger here ([scrub]: only this section
    // recomposes); the preview (the whole editor recomposes and the sky is re-graded) follows at
    // most every SKY_SCRUB_MS with the latest minute, and exactly where the finger lifts.
    var scrub by remember { mutableStateOf<Float?>(null) }
    val pushedAt = remember { longArrayOf(0L) }
    val push by rememberUpdatedState(onSkyMinute)
    LaunchedEffect(scrub) {
        val v = scrub ?: return@LaunchedEffect
        val wait = pushedAt[0] + SKY_SCRUB_MS - android.os.SystemClock.uptimeMillis()
        if (wait > 0L) kotlinx.coroutines.delay(wait)
        pushedAt[0] = android.os.SystemClock.uptimeMillis()
        push(v)
    }
    val minute = scrub ?: state.skyMinute
    val shown = minute ?: nowMinute
    val sun = LivingSky.sun(times, shown, today)
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ichthys_ed2_sky_day).uppercase(), fontFamily = t.mono, fontSize = 10.sp,
            letterSpacing = 1.2.sp, color = t.muted, modifier = Modifier.weight(1f))
        Text(
            (if (minute == null) stringResource(R.string.ichthys_ed2_sky_now) + " · " else "") +
                hhmm(shown) + " · " +
                stringResource(
                    if (sun.elevation >= 0f) R.string.ichthys_ed2_sky_sun_up
                    else R.string.ichthys_ed2_sky_sun_down,
                    kotlin.math.abs(sun.elevation).toInt()),
            fontFamily = t.mono, fontSize = 11.sp, color = t.text)
    }
    val bar = remember(times) {
        List(48) { i ->
            val g = LivingSky.grade(LivingSky.sun(times, i * 30f + 15f, today), null, 0.6f, 0.5f)
            Color(
                kotlin.math.sqrt((g.top[0] + g.hor[0]) / 2f),
                kotlin.math.sqrt((g.top[1] + g.hor[1]) / 2f),
                kotlin.math.sqrt((g.top[2] + g.hor[2]) / 2f),
            )
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp).height(8.dp)
        .clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(bar)))
    ShoalSlider(shown, { scrub = it }, 0f..1439f, Modifier.padding(horizontal = 18.dp),
        onFinished = {
            scrub?.let { onSkyMinute(it) }
            scrub = null
        })
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.ichthys_ed2_sky_times, hhmm(times.sunrise), hhmm(times.sunset)),
            fontSize = 11.sp, color = t.muted, modifier = Modifier.weight(1f))
        if (minute != null) {
            OutlineAction(stringResource(R.string.ichthys_ed2_sky_now), { onSkyMinute(null) }, accent = true)
        }
    }
    Spacer(Modifier.size(6.dp))
}

/** IchthysOS: how often a dragged "preview the day" slider re-grades the preview (~30 Hz). */
private const val SKY_SCRUB_MS = 33L

private fun hhmm(minutes: Float): String {
    val m = minutes.toInt().coerceIn(0, 1439)
    return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60)
}

/**
 * Whole looks, as a gallery of live lock screens: the built-in ones, then the owner's own (with
 * Delete), then "save this one". A look keeps the clock, its placement and style, the notification
 * mode and the fingerprint icon; not the shortcuts or the surface's points (the wallpaper's).
 */
@Composable
private fun LooksSheet(state: EditorState, onDraft: (LockDesign) -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(LockStore.readLooks(context)) }
    val d = state.draft
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ichthys_ed2_looks_hint), fontSize = 11.sp, color = t.muted,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        OutlineAction(stringResource(R.string.ichthys_editor_save_look), {
            var n = saved.size + 1
            while (saved.any { it.id == "mine$n" }) n++
            val id = "mine$n"
            val label = context.getString(R.string.ichthys_editor_my_look, n)
            saved = saved + SavedLook(id, label, d.toJson(id, label))
            LockStore.writeLooks(context, saved)
            onDraft(d.copy(look = id))
        }, accent = true)
    }
    ChipRow {
        builtinLooks(d).forEach { (label, look) ->
            PictureChip(stringResource(label), d.look == look.look, { onDraft(look) }, width = 76.dp) {
                MiniLockChrome(look, state.picture, Modifier.fillMaxSize())
            }
        }
        saved.forEach { s ->
            val look = d.withLook(s.json)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PictureChip(s.label, d.look == s.id, { onDraft(look) }, width = 76.dp) {
                    MiniLockChrome(look, state.picture, Modifier.fillMaxSize())
                }
                Text(stringResource(R.string.ichthys_ed2_delete).uppercase(), fontFamily = t.mono,
                    fontSize = 10.sp, letterSpacing = 0.8.sp, color = t.muted,
                    modifier = Modifier.clickable {
                        saved = saved.filter { it.id != s.id }
                        LockStore.writeLooks(context, saved)
                    }.padding(4.dp))
            }
        }
    }
    Spacer(Modifier.size(10.dp))
}

/** The built-in looks, over [d] (its shortcuts and notification options stay). */
private fun builtinLooks(d: LockDesign): List<Pair<Int, LockDesign>> {
    // IchthysOS: a look changes how the clock looks, not where the owner put it or the font they
    // chose (it used to move the clock back and swap the font to the old Tall face).
    val plain = d.copy(style = DEPTH_STYLE, fill = DepthClockView.FILL_SOLID,
        colorMode = LockKeys.MODE_DEFAULT, opacity = 100,
        showDate = true, details = false, datePos = DepthClockView.DATE_ABOVE,
        dateAlign = DepthClockView.ALIGN_CENTRE, shape = DepthClockView.SHAPE_STRETCH,
        placement = DepthClockView.PLACEMENT_FLAT)
    return listOf(
        R.string.ichthys_ed2_look_pond to plain.copy(depth = DepthClockView.DEPTH_OVERLAP, look = "pond"),
        R.string.ichthys_ed2_look_heat to
            plain.copy(depth = DepthClockView.DEPTH_XRAY, fill = DepthClockView.FILL_HEAT, look = "depth_heat"),
        R.string.ichthys_ed2_look_glass to
            plain.copy(depth = DepthClockView.DEPTH_OVERLAP, fill = DepthClockView.FILL_GLASS,
                look = "glass"),
        // IchthysOS projected clock: the wall looks (the surface stays the wallpaper's).
        R.string.ichthys_ed2_look_wall to
            plain.copy(depth = DepthClockView.DEPTH_OVERLAP, placement = DepthClockView.PLACEMENT_PROJECTED,
                projectedStyle = ProjectedClock.STYLE_PAINTED, look = "wall"),
        R.string.ichthys_ed2_look_neon to
            plain.copy(depth = DepthClockView.DEPTH_OVERLAP, placement = DepthClockView.PLACEMENT_PROJECTED,
                projectedStyle = ProjectedClock.STYLE_LIGHT, look = "neon"),
        R.string.ichthys_ed2_look_shoal to plain.copy(style = 68, depth = DepthClockView.DEPTH_OVERLAP, look = "shoal"),
        R.string.ichthys_ed2_look_oneui to plain.copy(style = 79, depth = DepthClockView.DEPTH_OVERLAP, look = "oneui"),
        R.string.ichthys_ed2_look_typo to plain.copy(style = 78, depth = DepthClockView.DEPTH_OVERLAP, look = "typo"),
        R.string.ichthys_ed2_look_matrix to plain.copy(style = 69, depth = DepthClockView.DEPTH_OFF, look = "matrix"),
    )
}

/**
 * A built-in wallpaper's picture, decoded small and off the main thread: the full 1080 x 2400
 * pictures, a row of them at once, would take some 10 MB of memory each.
 */
@Composable
private fun WallpaperThumb(res: Int) {
    val context = LocalContext.current
    val tk = t
    val thumb by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        null, res) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                android.graphics.BitmapFactory.decodeResource(context.resources, res,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })
                    ?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val shown = thumb
    if (shown != null) {
        Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxSize().background(tk.card))
    }
}
