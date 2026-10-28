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
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.android.settings.ichthys.guard.ui.theme.Guard
import com.android.settings.ichthys.guard.ui.theme.GuardTokens
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settings.R
import com.android.settingslib.ichthys.clock.DepthClockView
import com.android.settingslib.ichthys.clock.LockWeather
import com.android.settingslib.ichthys.clock.SquareDigits
import com.android.settingslib.ichthys.sky.LivingSky
import com.crdroid.settings.fragments.lockscreen.ClockUtils

/**
 * IchthysOS Shoal: the editor's colours, fonts and corners, all from the theme (GuardTheme around
 * the editor reads the framework's ichthys_* tokens). Nothing in the editor's own chrome is a hex.
 */
private val t: GuardTokens
    @Composable get() = Guard.tokens

/** The editor's parts, as the tabs under the preview. */
enum class EditorTab(val label: Int) {
    CLOCK(R.string.ichthys_ed2_clock),
    LAYOUT(R.string.ichthys_ed2_layout),
    WALL(R.string.ichthys_ed2_wallpaper),
    EXTRAS(R.string.ichthys_ed2_extras),
    LOOKS(R.string.ichthys_ed2_looks),
}

fun tabFor(s: EditorSheet): EditorTab? =
    when (s) {
        EditorSheet.CLOCK -> EditorTab.CLOCK
        EditorSheet.LAYOUT, EditorSheet.DATE -> EditorTab.LAYOUT
        EditorSheet.WALLPAPER -> EditorTab.WALL
        EditorSheet.WEATHER, EditorSheet.NOTIFICATIONS, EditorSheet.SHORTCUT_START,
        EditorSheet.SHORTCUT_END -> EditorTab.EXTRAS
        EditorSheet.LOOKS -> EditorTab.LOOKS
        EditorSheet.NONE -> null
    }

private fun EditorTab.sheet(): EditorSheet =
    when (this) {
        EditorTab.CLOCK -> EditorSheet.CLOCK
        EditorTab.LAYOUT -> EditorSheet.LAYOUT
        EditorTab.WALL -> EditorSheet.WALLPAPER
        EditorTab.EXTRAS -> EditorSheet.WEATHER
        EditorTab.LOOKS -> EditorSheet.LOOKS
    }

/** Mono caps tabs with the accent under the open one; the open one again folds the sheet away. */
@Composable
fun EditorTabs(sheet: EditorSheet, onSheet: (EditorSheet) -> Unit) {
    val tk = t
    val open = tabFor(sheet)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EditorTab.entries.forEach { tab ->
                val on = tab == open
                Text(
                    stringResource(tab.label).uppercase(),
                    fontFamily = tk.mono, fontSize = 11.sp, letterSpacing = 1.1.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) tk.text else tk.muted,
                    modifier = Modifier
                        .clickable { onSheet(if (on) EditorSheet.NONE else tab.sheet()) }
                        .padding(horizontal = 6.dp)
                        .padding(top = 14.dp, bottom = 12.dp)
                        .drawBehind {
                            if (on) {
                                val h = 2.dp.toPx()
                                drawRect(tk.accent, Offset(0f, size.height + 12.dp.toPx() - h),
                                    Size(size.width, h))
                            }
                        },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(tk.hairlineWidth).background(tk.hairline))
    }
}

/** The sheet docked under the tabs for [sheet]. Every change goes into the draft only. */
@Composable
fun EditorSheetHost(
    sheet: EditorSheet,
    state: EditorState,
    catalog: LockShortcuts.Catalog?,
    geo: LockGeometry,
    onDraft: (LockDesign) -> Unit,
    onWallpaper: (WallpaperDraft) -> Unit,
    onSheet: (EditorSheet) -> Unit,
    onSkyMinute: (Float?) -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth()
            .background(t.ink)
            // IchthysOS: one height for every tab. Each sheet sized to its content made the
            // preview grow and shrink as the tabs changed; now it stays put.
            .height(EDITOR_SHEET_H)
            .padding(bottom = 10.dp)
    ) {
        // Extras: which of them, as chips above the sheet (they stay while its content changes).
        if (tabFor(sheet) == EditorTab.EXTRAS) {
            val extras =
                listOf(EditorSheet.WEATHER to R.string.ichthys_ed2_weather,
                    EditorSheet.NOTIFICATIONS to R.string.ichthys_ed2_notifications,
                    EditorSheet.SHORTCUT_START to R.string.ichthys_ed2_shortcut_left,
                    EditorSheet.SHORTCUT_END to R.string.ichthys_ed2_shortcut_right)
            Spacer(Modifier.size(12.dp))
            ChipRow {
                extras.forEach { (s, label) ->
                    MonoChip(stringResource(label), sheet == s, { onSheet(s) })
                }
            }
        }
        // IchthysOS polish: another sheet's content cross-fades in while the sheet's height
        // springs to fit it (no jump in the preview above).
        AnimatedContent(
            targetState = sheet,
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 70)) togetherWith fadeOut(tween(90)))
                    .using(SizeTransform(clip = false) { _, _ ->
                        spring(dampingRatio = 0.9f, stiffness = 420f)
                    })
            },
            label = "sheet",
        ) { shown ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 2.dp)) {
                when (shown) {
                    EditorSheet.CLOCK -> ClockSheet(state, onDraft)
                    EditorSheet.LAYOUT, EditorSheet.DATE -> LayoutSheet(state, geo, onDraft)
                    EditorSheet.WEATHER -> WeatherSheet(state.draft, onDraft)
                    EditorSheet.NOTIFICATIONS -> NotificationSheet(state.draft, onDraft)
                    EditorSheet.SHORTCUT_START ->
                        ShortcutSheet(true, state.draft, catalog, onDraft)
                    EditorSheet.SHORTCUT_END ->
                        ShortcutSheet(false, state.draft, catalog, onDraft)
                    EditorSheet.WALLPAPER -> WallpaperSheet(state, onWallpaper, onDraft, onSkyMinute)
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
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
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
private fun PictureChip(label: String, on: Boolean, onClick: () -> Unit, thumb: @Composable () -> Unit) {
    Column(
        Modifier.width(64.dp).shoalCard(on, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(9.dp))) { thumb() }
        Text(label, fontFamily = t.mono, fontSize = 10.sp, letterSpacing = 0.4.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (on) t.accent else t.muted, modifier = Modifier.padding(top = 5.dp, bottom = 3.dp))
    }
}

/**
 * [d] with the clock in its home place (no move, turn or size): what the sheets' thumbnails
 * show. The same object while only the place changes, so dragging the clock doesn't rebuild
 * every thumbnail on every frame (each is a whole lock screen with its clock view).
 */
@Composable
private fun rememberAtHome(d: LockDesign): LockDesign {
    val home = d.copy(dx = 0f, dy = 0f, scale = 1f, rot = 0f, look = "")
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
 * [design]'s clock alone, upright and in its home place, on nothing: the lock screen drawn whole
 * at the card's width and cut to the band the faces sit in (the top of the screen).
 */
@Composable
private fun ClockOnly(design: LockDesign) {
    val screen = rememberScreen()
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullH = maxWidth * (screen.height.toFloat() / screen.width)
        Box(
            Modifier.fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .requiredHeight(fullH)
                .offset(y = -fullH * CLOCK_BAND_TOP)
        ) {
            // A little smaller than at home, so the tall two-row faces fit the card whole.
            val shown = remember(design) { design.copy(depth = DepthClockView.DEPTH_OFF, scale = 0.82f) }
            LockScreenPreview(shown, LockPicture.EMPTY, Modifier.fillMaxSize(), chrome = false,
                rounded = false)
        }
    }
}

/**
 * The faces' band: from just under the status bar, tall enough for the two-row faces (Depth,
 * One UI Stacked) with their date: about the top half of the screen at the card's width.
 */
private const val CLOCK_BAND_TOP = 0.06f

/** The docked sheet's height (all tabs): its content scrolls inside. */
private val EDITOR_SHEET_H = 380.dp
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
    height: Dp = 36.dp, glyph: (DrawScope.(Color) -> Unit)? = null) {
    val tk = t
    val shape = RoundedCornerShape(9.dp)
    val ink = if (on) tk.onAccent else tk.text
    Row(
        modifier.height(height).clip(shape)
            .background(if (on) tk.accent else Color.Transparent)
            .border(1.dp, if (on) tk.accent else tk.hairline, shape)
            .clickable(onClick = onClick)
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
    modifier: Modifier = Modifier) {
    val tk = t
    Slider(
        value = value, onValueChange = onChange, valueRange = range, modifier = modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(thumbColor = tk.accent, activeTrackColor = tk.accent,
            inactiveTrackColor = tk.track, activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent),
    )
}

/** A mono caps name with its value at the end, and the slider under them. */
@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    shown: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), fontFamily = t.mono, fontSize = 10.sp, letterSpacing = 1.2.sp,
                color = t.muted, modifier = Modifier.weight(1f))
            Text(shown, fontFamily = t.mono, fontSize = 11.sp, color = t.text)
        }
        ShoalSlider(value, onChange, range)
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
private fun OutlineAction(label: String, onClick: () -> Unit, accent: Boolean = false) {
    val tk = t
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier.height(34.dp).clip(shape).border(1.dp, if (accent) tk.accent else tk.outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
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

// ---- the clock ------------------------------------------------------------------------------

/** The clock: its face, type and finish, the glass, its colour. */
@Composable
private fun ClockSheet(state: EditorState, onDraft: (LockDesign) -> Unit) {
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
        // or the heat a touch leaves.
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

        // IchthysOS: the glass: clear or frosted (each as a picture of itself), and how milky it
        // is, from smoky to milky, or Auto, which picks it from the picture.
        if (d.fill == DepthClockView.FILL_GLASS) {
            Label(stringResource(R.string.ichthys_ed2_finish_glass))
            ChipRow {
                val home = rememberAtHome(d)
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
    ColourGroup(state, onDraft)
    Spacer(Modifier.size(8.dp))
}

private fun digitFamily(font: Int): FontFamily =
    FontFamily(androidx.compose.ui.text.font.Typeface(DepthClockView.digitTypeface(font)))

@Composable
private fun ColourGroup(state: EditorState, onDraft: (LockDesign) -> Unit) {
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
        "${hue.floatValue.toInt()}°") { h ->
        val c = AColor.HSVToColor(floatArrayOf(h, hsv[1].coerceAtLeast(0.3f), hsv[2].coerceAtLeast(0.9f)))
        hue.floatValue = h
        ownColor[0] = c
        onDraft(d.copy(colorMode = LockKeys.MODE_CUSTOM, color = c, look = "custom"))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_saturation), hsv[1], 0f..1f, "${(hsv[1] * 100).toInt()} %") { sat ->
        val c = AColor.HSVToColor(floatArrayOf(hue.floatValue, sat, hsv[2].coerceAtLeast(0.5f)))
        ownColor[0] = c
        onDraft(d.copy(colorMode = LockKeys.MODE_CUSTOM, color = c, look = "custom"))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_opacity), d.opacity.toFloat(), 20f..100f, "${d.opacity} %") {
        onDraft(d.copy(opacity = it.toInt(), look = "custom"))
    }
}

// ---- layout: size, depth, the date line ------------------------------------------------------

@Composable
private fun LayoutSheet(state: EditorState, geo: LockGeometry, onDraft: (LockDesign) -> Unit) {
    val d = state.draft
    SliderRow(stringResource(R.string.ichthys_ed2_size), d.scale, 0.4f..1.8f, "${(d.scale * 100).toInt()} %") {
        onDraft(d.copy(scale = it, look = "custom"))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_rotation), d.rot, -90f..90f, "${d.rot.toInt()}°") {
        onDraft(d.copy(rot = if (kotlin.math.abs(it) < 3f) 0f else it, look = "custom"))
    }

    Label(stringResource(R.string.ichthys_ed2_tab_depth))
    // IchthysOS: the digits are never distorted any more (DepthClockView fits the whole clock
    // evenly in every mode), so "Shape" is what Overlap does now; a saved Shape shows as Overlap.
    val modes =
        listOf(DepthClockView.DEPTH_OFF to R.string.ichthys_ed2_depth_off,
            DepthClockView.DEPTH_OVERLAP to R.string.ichthys_ed2_depth_overlap,
            DepthClockView.DEPTH_XRAY to R.string.ichthys_ed2_depth_xray)
    val home = rememberAtHome(d)
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

    val line = stringResource(R.string.ichthys_ed2_date_line)
    Label(line + " · " + stringResource(R.string.ichthys_ed2_date_shows))
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
        Label(line + " · " + stringResource(R.string.ichthys_ed2_position))
        Segmented(listOf(stringResource(R.string.ichthys_ed2_above), stringResource(R.string.ichthys_ed2_below)),
            d.datePos) { onDraft(d.copy(datePos = it, look = "custom")) }
        Label(line + " · " + stringResource(R.string.ichthys_ed2_align))
        AlignChoice(d) { onDraft(d.copy(dateAlign = it, look = "custom")) }
    }

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
private fun WeatherSheet(d: LockDesign, onDraft: (LockDesign) -> Unit) {
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

    Label(stringResource(R.string.ichthys_ed2_tab_size))
    SliderRow(stringResource(R.string.ichthys_ed2_size), d.weatherSize.toFloat(), 70f..150f,
        "${d.weatherSize} %") { edit(d.copy(weatherSize = it.toInt())) }
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
        Text(label, fontFamily = tk.mono, fontSize = 10.sp, letterSpacing = 0.4.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (on) tk.accent else tk.muted, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
    }
}

@Composable
private fun NotificationSheet(d: LockDesign, onDraft: (LockDesign) -> Unit) {
    Label(stringResource(R.string.ichthys_ed2_notifications))
    SwitchRow(stringResource(R.string.ichthys_ed2_notif_show), d.showNotifications) {
        onDraft(d.copy(showNotifications = it))
    }
    SwitchRow(stringResource(R.string.ichthys_ed2_notif_content), d.privateNotifications) {
        onDraft(d.copy(privateNotifications = it))
    }
    Note(stringResource(R.string.ichthys_ed2_notif_fixed))
}

@Composable
private fun ShortcutSheet(start: Boolean, d: LockDesign, catalog: LockShortcuts.Catalog?,
    onDraft: (LockDesign) -> Unit) {
    Label(stringResource(if (start) R.string.ichthys_ed2_shortcut_left else R.string.ichthys_ed2_shortcut_right))
    if (catalog == null) {
        Note(stringResource(R.string.ichthys_ed2_shortcuts_unavailable))
        return
    }
    val current = if (start) d.shortcutStart else d.shortcutEnd
    fun pick(id: String) = onDraft(if (start) d.copy(shortcutStart = id) else d.copy(shortcutEnd = id))
    val tk = t
    val tint = tk.text.toArgb()
    val items = listOf<LockShortcuts.Affordance?>(null) + catalog.all
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
                    Text(a?.name ?: stringResource(R.string.ichthys_ed2_none), fontFamily = tk.mono,
                        fontSize = 10.sp, letterSpacing = 0.4.sp, overflow = TextOverflow.Ellipsis,
                        color = if (on) tk.accent else tk.muted, maxLines = 1,
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

// ---- wallpaper and looks --------------------------------------------------------------------

@Composable
private fun WallpaperSheet(
    state: EditorState,
    onWallpaper: (WallpaperDraft) -> Unit,
    onDraft: (LockDesign) -> Unit,
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
    LivingSkySection(state, onDraft, onSkyMinute)
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
                pic.skyInfo.reason == LivingSky.REASON_DARK -> R.string.ichthys_ed2_sky_dark
                else -> R.string.ichthys_ed2_sky_none
            }
        Note(stringResource(reason))
        Spacer(Modifier.size(8.dp))
        return
    }
    if (!d.livingSky) {
        Note(stringResource(R.string.ichthys_ed2_sky_note))
        Spacer(Modifier.size(8.dp))
        return
    }
    SwitchRow(stringResource(R.string.ichthys_ed2_sky_weather), d.skyWeather) {
        edit(d.copy(skyWeather = it))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_sky_intensity), d.skyIntensity.toFloat(), 10f..100f,
        "${d.skyIntensity}%") { edit(d.copy(skyIntensity = it.toInt())) }

    // Preview the day: any minute of today, with the sky's colour across the day under it.
    val times = remember { LivingSky.sunTimes(context) }
    val today = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }
    val minute = state.skyMinute
    val nowMinute = remember {
        val c = java.util.Calendar.getInstance()
        c.get(java.util.Calendar.HOUR_OF_DAY) * 60f + c.get(java.util.Calendar.MINUTE)
    }
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
    ShoalSlider(shown, { onSkyMinute(it) }, 0f..1439f, Modifier.padding(horizontal = 18.dp))
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

private fun hhmm(minutes: Float): String {
    val m = minutes.toInt().coerceIn(0, 1439)
    return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60)
}

/** Whole looks: built in, then the user's own (deletable), then "save this one". */
@Composable
private fun LooksSheet(state: EditorState, onDraft: (LockDesign) -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(LockStore.readLooks(context)) }
    val d = state.draft
    Label(stringResource(R.string.ichthys_ed2_looks))
    ChipRow {
        builtinLooks(d).forEach { (label, look) ->
            PictureChip(stringResource(label), d.look == look.look, { onDraft(look) }) {
                MiniLock(look, state.picture, Modifier.fillMaxSize())
            }
        }
        saved.forEach { s ->
            val look = d.withLook(s.json)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PictureChip(s.label, d.look == s.id, { onDraft(look) }) {
                    MiniLock(look, state.picture, Modifier.fillMaxSize())
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
    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
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
}

/** The built-in looks, over [d] (its shortcuts and notification options stay). */
private fun builtinLooks(d: LockDesign): List<Pair<Int, LockDesign>> {
    // IchthysOS: a look changes how the clock looks, not where the owner put it or the font they
    // chose (it used to move the clock back and swap the font to the old Tall face).
    val plain = d.copy(style = DEPTH_STYLE, fill = DepthClockView.FILL_SOLID,
        colorMode = LockKeys.MODE_DEFAULT, opacity = 100,
        showDate = true, details = false, datePos = DepthClockView.DATE_ABOVE,
        dateAlign = DepthClockView.ALIGN_CENTRE, shape = DepthClockView.SHAPE_STRETCH)
    return listOf(
        R.string.ichthys_ed2_look_pond to plain.copy(depth = DepthClockView.DEPTH_OVERLAP, look = "pond"),
        R.string.ichthys_ed2_look_heat to
            plain.copy(depth = DepthClockView.DEPTH_XRAY, fill = DepthClockView.FILL_HEAT, look = "depth_heat"),
        R.string.ichthys_ed2_look_glass to
            plain.copy(depth = DepthClockView.DEPTH_OVERLAP, fill = DepthClockView.FILL_GLASS,
                look = "glass"),
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
