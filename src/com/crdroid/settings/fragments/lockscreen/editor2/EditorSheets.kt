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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settings.R
import com.android.settingslib.ichthys.clock.DepthClockView
import com.android.settingslib.ichthys.clock.LockWeather
import com.android.settingslib.ichthys.clock.SquareDigits
import com.android.settingslib.ichthys.sky.LivingSky
import com.crdroid.settings.fragments.lockscreen.ClockUtils

private val SHEET_BG = Color(0xFF18161A)
private val CHIP_BG = Color(0xFF252228)
private val LINE = Color(0xFF3A363D)
private val INK = Color(0xFFF4EDE6)
private val DIM = Color(0xFFA59EA0)
private val POND = Color(0xFF9FE7DD)

/** The sheet docked under the preview for [sheet]. Every change goes into the draft only. */
@Composable
fun EditorSheetHost(
    sheet: EditorSheet,
    state: EditorState,
    catalog: LockShortcuts.Catalog?,
    geo: LockGeometry,
    onDraft: (LockDesign) -> Unit,
    onWallpaper: (WallpaperDraft) -> Unit,
    onClose: () -> Unit,
    onSkyMinute: (Float?) -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(SHEET_BG)
            .heightIn(max = 380.dp)
            .padding(top = 8.dp, bottom = 10.dp)
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape)
                .background(LINE).clickable(onClick = onClose)
        )
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
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                when (shown) {
                    EditorSheet.CLOCK -> ClockSheet(state, geo, onDraft)
                    EditorSheet.DATE -> DateSheet(state.draft, onDraft)
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

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.4.sp,
        color = DIM,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        content()
    }
}

/** A choice shown as a picture of itself (a small lock screen) with its name under it. */
@Composable
private fun PictureChip(label: String, on: Boolean, onClick: () -> Unit, thumb: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(58.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp, 128.dp).clip(shape)
                .border(if (on) BorderStroke(2.dp, POND) else BorderStroke(1.dp, LINE), shape)
        ) {
            thumb()
        }
        Text(label, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center,
            color = if (on) INK else DIM, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TextChip(label: String, on: Boolean, onClick: () -> Unit, family: FontFamily? = null,
    big: Boolean = false, square: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.clip(shape).background(CHIP_BG)
            .border(if (on) BorderStroke(2.dp, POND) else BorderStroke(1.dp, LINE), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (big) 6.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // IchthysOS: the Square font has no typeface; the text keeps the chip its size (unseen)
        // and the digits are drawn over it as the Depth face draws them.
        Text(label, fontSize = if (big) 20.sp else 12.sp, color = if (square) Color.Transparent else INK,
            fontFamily = family)
        if (square) {
            Box(
                Modifier.matchParentSize().drawWithCache {
                    val textSize = (if (big) 20.sp else 12.sp).toPx()
                    val path = android.graphics.Path()
                    val w = SquareDigits.advance(textSize) * label.length
                    SquareDigits.textPath(label, 0, label.length, (size.width - w) / 2f,
                        (size.height + SquareDigits.capHeight(textSize)) / 2f, textSize, path)
                    val glyphs = path.asComposePath()
                    onDrawBehind { drawPath(glyphs, INK) }
                }
            )
        }
    }
}

/** A row of mutually exclusive options. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(12.dp))
            .background(CHIP_BG).padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (i == selected) INK else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.sp, color = if (i == selected) Color(0xFF111111) else DIM)
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, on: Boolean, onClick: () -> Unit, brush: Brush? = null) {
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
            .border(if (on) BorderStroke(2.5.dp, POND) else BorderStroke(1.dp, LINE), CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    shown: String, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = DIM, modifier = Modifier.width(70.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(shown, fontSize = 12.sp, color = INK, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
    }
}

// ---- the clock ------------------------------------------------------------------------------

@Composable
private fun ClockSheet(state: EditorState, geo: LockGeometry, onDraft: (LockDesign) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val d = state.draft
    Segmented(
        listOf(stringResource(R.string.ichthys_ed2_tab_style), stringResource(R.string.ichthys_ed2_tab_colour),
            stringResource(R.string.ichthys_ed2_tab_depth), stringResource(R.string.ichthys_ed2_tab_size)),
        tab,
    ) { tab = it }
    when (tab) {
        0 -> StyleTab(state, onDraft)
        1 -> ColourTab(state, onDraft)
        2 -> DepthTab(state, geo, onDraft)
        else -> SizeTab(d, onDraft)
    }
}

@Composable
private fun StyleTab(state: EditorState, onDraft: (LockDesign) -> Unit) {
    val d = state.draft
    val names = remember { ClockUtils.getClockNames() }
    // Newest faces first (Depth, One UI, …); built only as they scroll into view.
    val faces = remember { ClockUtils.CLOCK_LAYOUTS.indices.reversed().toList() }
    Label(stringResource(R.string.ichthys_ed2_faces))
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(faces.size, key = { faces[it] }) { i ->
            val style = faces[i]
            PictureChip(names[style], style == d.style, { onDraft(d.copy(style = style, look = "custom")) }) {
                MiniLock(d.copy(style = style), state.picture, Modifier.fillMaxSize())
            }
        }
    }
    if (d.isDepthFace) {
        Label(stringResource(R.string.ichthys_ed2_font))
        val fonts =
            listOf(DepthClockView.FONT_TALL to R.string.ichthys_ed2_font_tall,
                DepthClockView.FONT_FLEX to R.string.ichthys_ed2_font_flex,
                DepthClockView.FONT_WIDE to R.string.ichthys_ed2_font_wide,
                DepthClockView.FONT_THIN to R.string.ichthys_ed2_font_thin,
                DepthClockView.FONT_SERIF to R.string.ichthys_ed2_font_serif,
                DepthClockView.FONT_MONO to R.string.ichthys_ed2_font_mono,
                DepthClockView.FONT_SQUARE to R.string.ichthys_ed2_font_square)
        ChipRow {
            fonts.forEach { (font, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val family = remember(font) {
                        FontFamily(androidx.compose.ui.text.font.Typeface(DepthClockView.digitTypeface(font)))
                    }
                    TextChip("10", d.font == font, { onDraft(d.copy(font = font, look = "custom")) },
                        family, big = true, square = font == DepthClockView.FONT_SQUARE)
                    Text(stringResource(label), fontSize = 10.sp, color = DIM,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        Label(stringResource(R.string.ichthys_ed2_digits))
        Segmented(
            listOf(stringResource(R.string.ichthys_depth_fill_solid),
                stringResource(R.string.ichthys_depth_fill_gradient),
                stringResource(R.string.ichthys_depth_fill_live)),
            d.fill,
        ) { onDraft(d.copy(fill = it, look = "custom")) }
    }
    Spacer(Modifier.size(6.dp))
}

@Composable
private fun ColourTab(state: EditorState, onDraft: (LockDesign) -> Unit) {
    val d = state.draft
    val context = LocalContext.current
    val accent = remember { Color(context.getColor(android.R.color.system_accent1_100)) }
    // IchthysOS: the Depth face's finish: solid, a gradient, or glass the picture shows through.
    if (d.isDepthFace && d.fill != DepthClockView.FILL_HEAT) {
        Label(stringResource(R.string.ichthys_ed2_finish))
        val fills = listOf(DepthClockView.FILL_SOLID, DepthClockView.FILL_GRADIENT,
            DepthClockView.FILL_GLASS)
        Segmented(
            listOf(stringResource(R.string.ichthys_ed2_finish_solid),
                stringResource(R.string.ichthys_ed2_finish_gradient),
                stringResource(R.string.ichthys_ed2_finish_glass)),
            fills.indexOf(d.fill).coerceAtLeast(0),
        ) { onDraft(d.copy(fill = fills[it], look = "custom")) }
    }
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
    SliderRow(stringResource(R.string.ichthys_ed2_hue), hsv[0], 0f..360f, "${hsv[0].toInt()}°") { h ->
        onDraft(d.copy(colorMode = LockKeys.MODE_CUSTOM,
            color = AColor.HSVToColor(floatArrayOf(h, hsv[1].coerceAtLeast(0.3f), hsv[2].coerceAtLeast(0.9f))),
            look = "custom"))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_saturation), hsv[1], 0f..1f, "${(hsv[1] * 100).toInt()} %") { sat ->
        onDraft(d.copy(colorMode = LockKeys.MODE_CUSTOM,
            color = AColor.HSVToColor(floatArrayOf(hsv[0], sat, hsv[2].coerceAtLeast(0.5f))), look = "custom"))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_opacity), d.opacity.toFloat(), 20f..100f, "${d.opacity} %") {
        onDraft(d.copy(opacity = it.toInt(), look = "custom"))
    }
}

@Composable
private fun DepthTab(state: EditorState, geo: LockGeometry, onDraft: (LockDesign) -> Unit) {
    val d = state.draft
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
            PictureChip(stringResource(label), on, { onDraft(withDepth(d, mode, state.picture, geo)) }) {
                MiniLock(withDepth(d, mode, state.picture, geo), state.picture, Modifier.fillMaxSize())
            }
        }
    }
    if (state.picture.cutout == null) {
        Text(stringResource(R.string.ichthys_ed2_depth_no_subject), fontSize = 11.sp, color = DIM,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }
    Spacer(Modifier.size(6.dp))
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

@Composable
private fun SizeTab(d: LockDesign, onDraft: (LockDesign) -> Unit) {
    Label(stringResource(R.string.ichthys_ed2_tab_size))
    SliderRow(stringResource(R.string.ichthys_ed2_size), d.scale, 0.5f..1.5f, "${(d.scale * 100).toInt()} %") {
        onDraft(d.copy(scale = it, look = "custom"))
    }
    SliderRow(stringResource(R.string.ichthys_ed2_rotation), d.rot, -90f..90f, "${d.rot.toInt()}°") {
        onDraft(d.copy(rot = if (kotlin.math.abs(it) < 3f) 0f else it, look = "custom"))
    }
    Row(Modifier.padding(horizontal = 10.dp)) {
        TextButton(onClick = { onDraft(d.copy(dx = 0f, dy = 0f, scale = 1f, rot = 0f, look = "custom")) }) {
            Text(stringResource(R.string.ichthys_editor_reset))
        }
    }
}

// ---- date, notifications, shortcuts ---------------------------------------------------------

@Composable
private fun DateSheet(d: LockDesign, onDraft: (LockDesign) -> Unit) {
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
    Label(stringResource(R.string.ichthys_ed2_position))
    Segmented(listOf(stringResource(R.string.ichthys_ed2_above), stringResource(R.string.ichthys_ed2_below)),
        d.datePos) { onDraft(d.copy(datePos = it, look = "custom")) }
    Label(stringResource(R.string.ichthys_ed2_align))
    // Left, centre, right on screen; the settings' order is centre, start, end.
    val shown = when (d.dateAlign) {
        DepthClockView.ALIGN_START -> 0
        DepthClockView.ALIGN_END -> 2
        else -> 1
    }
    Segmented(listOf(stringResource(R.string.ichthys_ed2_left), stringResource(R.string.ichthys_ed2_centre),
        stringResource(R.string.ichthys_ed2_right)), shown) {
        val a = when (it) {
            0 -> DepthClockView.ALIGN_START
            2 -> DepthClockView.ALIGN_END
            else -> DepthClockView.ALIGN_CENTRE
        }
        onDraft(d.copy(dateAlign = a, look = "custom"))
    }
    Spacer(Modifier.size(8.dp))
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
        Text(stringResource(R.string.ichthys_ed2_weather_no_data), fontSize = 11.sp, color = DIM,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.padding(horizontal = 10.dp)) {
            TextButton(onClick = {
                try {
                    context.startActivity(
                        com.android.internal.util.crdroid.OmniJawsClient.get().getSettingsIntent()
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // OmniJaws is not installed.
                }
            }) { Text(stringResource(R.string.ichthys_ed2_weather_settings)) }
        }
    }
    if (!d.weather) {
        Spacer(Modifier.size(6.dp))
        return
    }

    Label(stringResource(R.string.ichthys_ed2_date_shows))
    ChipRow {
        TextChip(stringResource(R.string.ichthys_ed2_weather_temp), d.weatherTemp,
            { edit(d.copy(weatherTemp = !d.weatherTemp)) })
        TextChip(stringResource(R.string.ichthys_ed2_weather_condition), d.weatherCondition,
            { edit(d.copy(weatherCondition = !d.weatherCondition)) })
        TextChip(stringResource(R.string.ichthys_ed2_weather_highlow), d.weatherHighLow,
            { edit(d.copy(weatherHighLow = !d.weatherHighLow)) })
        TextChip(stringResource(R.string.ichthys_ed2_weather_location), d.weatherLocation,
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
        val shown = when (d.dateAlign) {
            DepthClockView.ALIGN_START -> 0
            DepthClockView.ALIGN_END -> 2
            else -> 1
        }
        Segmented(listOf(stringResource(R.string.ichthys_ed2_left), stringResource(R.string.ichthys_ed2_centre),
            stringResource(R.string.ichthys_ed2_right)), shown) {
            val a = when (it) {
                0 -> DepthClockView.ALIGN_START
                2 -> DepthClockView.ALIGN_END
                else -> DepthClockView.ALIGN_CENTRE
            }
            edit(d.copy(dateAlign = a))
        }
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
    val shape = RoundedCornerShape(12.dp)
    val image =
        remember(pack, code) {
            pack?.let { p ->
                LockWeather.icon(context, p, code)?.let { dr ->
                    val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                    val m = dr.mutate()
                    if (LockWeather.isMono(p)) m.setTint(DepthClockView.POND_CREAM)
                    m.setBounds(0, 0, 96, 96)
                    m.draw(android.graphics.Canvas(b))
                    b.asImageBitmap()
                }
            }
        }
    Column(Modifier.width(62.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp).clip(shape).background(Color(0xFF0E1417))
                .border(if (on) BorderStroke(2.dp, POND) else BorderStroke(1.dp, LINE), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) Image(image, null, Modifier.size(30.dp))
            else Text("∅", fontSize = 18.sp, color = INK)
        }
        Text(label, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center,
            color = if (on) INK else DIM, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = INK, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange)
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
    Text(stringResource(R.string.ichthys_ed2_notif_fixed), fontSize = 11.sp, color = DIM,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun ShortcutSheet(start: Boolean, d: LockDesign, catalog: LockShortcuts.Catalog?,
    onDraft: (LockDesign) -> Unit) {
    Label(stringResource(if (start) R.string.ichthys_ed2_shortcut_left else R.string.ichthys_ed2_shortcut_right))
    if (catalog == null) {
        Text(stringResource(R.string.ichthys_ed2_shortcuts_unavailable), fontSize = 12.sp, color = DIM,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        return
    }
    val current = if (start) d.shortcutStart else d.shortcutEnd
    fun pick(id: String) = onDraft(if (start) d.copy(shortcutStart = id) else d.copy(shortcutEnd = id))
    val items = listOf<LockShortcuts.Affordance?>(null) + catalog.all
    // Rows of four.
    items.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { a ->
                val id = a?.id ?: ""
                val on = id == current
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) Color(0x339FE7DD) else CHIP_BG)
                        .clickable { pick(id) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val icon = remember(a) {
                        a?.icon?.let { dr ->
                            val b = android.graphics.Bitmap.createBitmap(72, 72, android.graphics.Bitmap.Config.ARGB_8888)
                            val m = dr.mutate()
                            m.setTint(AColor.rgb(244, 237, 230))
                            m.setBounds(0, 0, 72, 72)
                            m.draw(android.graphics.Canvas(b))
                            b.asImageBitmap()
                        }
                    }
                    if (icon != null) Image(icon, null, Modifier.size(24.dp))
                    else Text("∅", fontSize = 18.sp, color = INK)
                    Text(a?.name ?: stringResource(R.string.ichthys_ed2_none), fontSize = 10.sp,
                        color = if (on) INK else DIM, maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
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
            Box(Modifier.fillMaxSize().background(Color(0xFF0E1417)), contentAlignment = Alignment.Center) {
                Text("↺", fontSize = 26.sp, color = POND)
            }
        }
        PictureChip(stringResource(R.string.ichthys_wallpaper_photo), state.wallpaper is WallpaperDraft.Photo, {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0E1417)), contentAlignment = Alignment.Center) {
                Text("+", fontSize = 32.sp, color = POND)
            }
        }
        BUILTIN_WALLPAPERS.forEach { (id, res, label) ->
            val w = state.wallpaper
            PictureChip(stringResource(label), w is WallpaperDraft.Builtin && w.id == id,
                { onWallpaper(WallpaperDraft.Builtin(id)) }) {
                Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
    Text(stringResource(R.string.ichthys_ed2_wallpaper_note), fontSize = 11.sp, color = DIM,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
    SkySwitchRow(stringResource(R.string.ichthys_ed2_sky_follow), d.livingSky && usable, usable) {
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
        Text(stringResource(reason), fontSize = 11.sp, color = DIM,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Spacer(Modifier.size(8.dp))
        return
    }
    if (!d.livingSky) {
        Text(stringResource(R.string.ichthys_ed2_sky_note), fontSize = 11.sp, color = DIM,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Spacer(Modifier.size(8.dp))
        return
    }
    SkySwitchRow(stringResource(R.string.ichthys_ed2_sky_weather), d.skyWeather, true) {
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ichthys_ed2_sky_day), fontSize = 12.sp, color = DIM,
            modifier = Modifier.weight(1f))
        Text(
            (if (minute == null) stringResource(R.string.ichthys_ed2_sky_now) + " · " else "") +
                hhmm(shown) + " · " +
                stringResource(
                    if (sun.elevation >= 0f) R.string.ichthys_ed2_sky_sun_up
                    else R.string.ichthys_ed2_sky_sun_down,
                    kotlin.math.abs(sun.elevation).toInt()),
            fontSize = 12.sp, color = INK)
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
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(8.dp)
        .clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(bar)))
    Slider(
        value = shown,
        onValueChange = { onSkyMinute(it) },
        valueRange = 0f..1439f,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.ichthys_ed2_sky_times, hhmm(times.sunrise), hhmm(times.sunset)),
            fontSize = 11.sp, color = DIM, modifier = Modifier.weight(1f))
        if (minute != null) {
            TextButton(onClick = { onSkyMinute(null) }) {
                Text(stringResource(R.string.ichthys_ed2_sky_now), color = POND)
            }
        }
    }
    Spacer(Modifier.size(6.dp))
}

private fun hhmm(minutes: Float): String {
    val m = minutes.toInt().coerceIn(0, 1439)
    return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60)
}

@Composable
private fun SkySwitchRow(label: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onChange(!on) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = if (enabled) INK else DIM, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange, enabled = enabled)
    }
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
                Text(stringResource(R.string.ichthys_ed2_delete), fontSize = 10.sp, color = POND,
                    modifier = Modifier.clickable {
                        saved = saved.filter { it.id != s.id }
                        LockStore.writeLooks(context, saved)
                    }.padding(4.dp))
            }
        }
    }
    Row(Modifier.padding(horizontal = 10.dp)) {
        TextButton(onClick = {
            var n = saved.size + 1
            while (saved.any { it.id == "mine$n" }) n++
            val id = "mine$n"
            val label = context.getString(R.string.ichthys_editor_my_look, n)
            saved = saved + SavedLook(id, label, d.toJson(id, label))
            LockStore.writeLooks(context, saved)
            onDraft(d.copy(look = id))
        }) {
            Text(stringResource(R.string.ichthys_editor_save_look))
        }
    }
}

/** The built-in looks, over [d] (its shortcuts and notification options stay). */
private fun builtinLooks(d: LockDesign): List<Pair<Int, LockDesign>> {
    val plain = d.copy(style = DEPTH_STYLE, fill = DepthClockView.FILL_SOLID, font = DepthClockView.FONT_TALL,
        colorMode = LockKeys.MODE_DEFAULT, opacity = 100, dx = 0f, dy = 0f, scale = 1f, rot = 0f,
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
