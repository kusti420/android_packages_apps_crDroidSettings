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

package com.crdroid.settings.fragments.lockscreen

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settings.R
import java.io.File

/** Settings.Secure keys read by SystemUI's IchthysDepthRepository and SettingsLib's DepthClockView. */
private const val KEY_DEPTH = "ichthys_lockscreen_depth"
private const val KEY_FILL = "ichthys_clock_fill"

/** Picker index of the Depth face (ClockUtils.CLOCK_LAYOUTS). */
const val DEPTH_CLOCK_STYLE = 80

/**
 * IchthysOS depth lock screen (kusti420/ichthysos-tracker#58), on top of the clock picker: each
 * choice is a small picture of itself, one scrolling row per setting. Both apply at once (the lock
 * screen watches them); the clock face itself still waits for Apply.
 * - Depth: the wallpaper's subject in front of the clock, or not.
 * - Fill (with the Depth face selected): what fills its digits.
 */
@Composable
fun DepthOptions(selectedClock: Int, wallpaper: ImageBitmap?) {
    val context = LocalContext.current
    var depth by remember { mutableIntStateOf(secureInt(context, KEY_DEPTH, 0)) }
    var fill by remember { mutableIntStateOf(secureInt(context, KEY_FILL, 2)) }
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        OptionRow(stringResource(R.string.ichthys_depth_title)) {
            Choice(stringResource(R.string.ichthys_depth_off), depth == 0, {
                depth = 0
                putSecureInt(context, KEY_DEPTH, 0)
            }) { DepthThumb(wallpaper, subject = false, accent) }
            Choice(stringResource(R.string.ichthys_depth_on), depth != 0, {
                depth = 1
                putSecureInt(context, KEY_DEPTH, 1)
            }) { DepthThumb(wallpaper, subject = true, accent) }
        }
        if (selectedClock == DEPTH_CLOCK_STYLE) {
            OptionRow(stringResource(R.string.ichthys_depth_fill_title)) {
                val labels =
                    listOf(
                        R.string.ichthys_depth_fill_solid,
                        R.string.ichthys_depth_fill_gradient,
                        R.string.ichthys_depth_fill_live,
                    )
                labels.forEachIndexed { mode, label ->
                    Choice(stringResource(label), fill == mode, {
                        fill = mode
                        putSecureInt(context, KEY_FILL, mode)
                    }) { FillThumb(mode, accent) }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, content: @Composable () -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    thumb: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(92.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(92.dp, 120.dp)
                .clip(shape)
                .border(
                    if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape,
                )
        ) {
            thumb()
        }
        Text(
            label,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color =
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** The wallpaper, "13" over it, and with [subject] a figure standing in front of the digits. */
@Composable
private fun DepthThumb(wallpaper: ImageBitmap?, subject: Boolean, accent: Color) {
    Box(Modifier.fillMaxSize()) {
        if (wallpaper != null) {
            Image(
                wallpaper,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0x55000000))
            drawDigits("13", Color.White.copy(alpha = 0.92f))
            if (subject) {
                // A stand-in subject: a head and shoulders cutting through the digits.
                val c = Offset(size.width * 0.62f, size.height * 0.58f)
                drawCircle(accent, radius = size.width * 0.15f, center = c)
                drawOval(
                    accent,
                    topLeft = Offset(c.x - size.width * 0.3f, c.y + size.width * 0.12f),
                    size = Size(size.width * 0.6f, size.height * 0.5f),
                )
            }
        }
    }
}

/** "13" filled the way [mode] fills the Depth face. */
@Composable
private fun FillThumb(mode: Int, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF0B1418))
        when (mode) {
            0 -> drawDigits("13", Color.White)
            1 -> drawDigits("13", Color.White, gradientTop = Color(0xFF9FE7DD))
            else -> {
                drawDigits("13", Color(0xFF04090B), outline = Color.White.copy(alpha = 0.3f))
                // Glowing touch heat seen through the digits (the thumb's arc, low on the screen).
                val digits = android.graphics.Path()
                digitPaint(this).getTextPath("13", 0, 2, size.width / 2f, size.height * 0.8f, digits)
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.clipPath(digits)
                listOf(
                        Triple(0.62f, 0.78f, 1f),
                        Triple(0.42f, 0.88f, 0.8f),
                        Triple(0.72f, 0.5f, 0.55f),
                        Triple(0.32f, 0.36f, 0.4f),
                    )
                    .forEach { (fx, fy, w) ->
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color(0xFFFFECD6).copy(alpha = w), accent.copy(alpha = w * 0.7f), Color.Transparent),
                                center = Offset(size.width * fx, size.height * fy),
                                radius = size.width * 0.42f,
                            ),
                            radius = size.width * 0.42f,
                            center = Offset(size.width * fx, size.height * fy),
                        )
                    }
                canvas.restore()
            }
        }
    }
}

private val thumbFace: Typeface by lazy {
    val f = File("/system/fonts/RobotoFlex-Regular.ttf")
    runCatching {
            if (f.isFile)
                Typeface.Builder(f).setFontVariationSettings("'wdth' 25, 'wght' 1000").build()
            else null
        }
        .getOrNull() ?: Typeface.create("sans-serif-condensed", Typeface.BOLD)
}

private fun digitPaint(scope: DrawScope) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = thumbFace
        textAlign = Paint.Align.CENTER
        textSize = scope.size.height * 0.78f
    }

/** Two big digits filling most of the tile, like the Depth face's hours. */
private fun DrawScope.drawDigits(
    text: String,
    color: Color,
    gradientTop: Color? = null,
    outline: Color? = null,
) {
    val p = digitPaint(this).apply { this.color = color.toArgb() }
    val base = size.height * 0.8f
    val canvas = drawContext.canvas.nativeCanvas
    if (gradientTop != null) {
        // A vertical accent-to-clock-colour fade, like FILL_GRADIENT.
        p.shader =
            LinearGradient(0f, size.height * 0.15f, 0f, base, gradientTop.toArgb(),
                color.toArgb(), Shader.TileMode.CLAMP)
    }
    canvas.drawText(text, size.width / 2f, base, p)
    if (outline != null) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = outline.toArgb()
        canvas.drawText(text, size.width / 2f, base, p)
    }
}

private fun secureInt(context: Context, key: String, def: Int): Int =
    Settings.Secure.getIntForUser(context.contentResolver, key, def, UserHandle.USER_CURRENT)

private fun putSecureInt(context: Context, key: String, value: Int) {
    Settings.Secure.putIntForUser(context.contentResolver, key, value, UserHandle.USER_CURRENT)
}
