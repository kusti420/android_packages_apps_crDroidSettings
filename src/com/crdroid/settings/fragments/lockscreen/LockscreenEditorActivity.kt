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

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.internal.util.crdroid.ThemeUtils
import com.android.settings.R
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.crdroid.settings.utils.SystemUtils
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * IchthysOS lock screen editor (kusti420/ichthysos-tracker#58), the concept's "Looks + free edit":
 * a full preview of the lock screen (the wallpaper, the real clock face, the depth cut-out over it)
 * where the clock is moved with one finger, resized with a pinch and turned with two, snapping to
 * the centre and to 0° / 90°, with the zones it must keep clear shown as guard rails. Below it,
 * rows of picture choices: whole looks (built in, and the user's own), the face, the Depth face's
 * fill, depth on or off. Apply writes it all; "Save as my look" keeps it as a look.
 *
 * Opened from the lock screen's long-press "customize" button and from Settings.
 */
class LockscreenEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // "wallpaper=koi": set a depth wallpaper on the lock screen straight away.
        intent.getStringExtra(EXTRA_WALLPAPER)?.let { setDepthWallpaper(this, it) }
        setContent { SettingsTheme { LockscreenEditor(onDone = { finish() }) } }
    }
}

/** One whole lock screen. Offsets are in screen pixels, like the settings that hold them. */
data class Look(
    val id: String,
    val label: String,
    val style: Int,
    val fill: Int = 2,
    val depth: Int = 1,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val rot: Float = 0f,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("label", label)
            .put("style", style)
            .put("fill", fill)
            .put("depth", depth)
            .put("dx", dx.toDouble())
            .put("dy", dy.toDouble())
            .put("scale", scale.toDouble())
            .put("rot", rot.toDouble())

    companion object {
        fun fromJson(o: JSONObject) =
            Look(
                o.getString("id"),
                o.getString("label"),
                o.getInt("style"),
                o.optInt("fill", 2),
                o.optInt("depth", 1),
                o.optDouble("dx", 0.0).toFloat(),
                o.optDouble("dy", 0.0).toFloat(),
                o.optDouble("scale", 1.0).toFloat(),
                o.optDouble("rot", 0.0).toFloat(),
            )
    }
}

private val BUILT_IN =
    listOf(
        Look("pond", "Pond", 80, fill = 0),
        Look("depth_heat", "Pond · touch heat", 80, fill = 2),
        Look("depth_tilt", "Tilt", 80, fill = 1, dx = -40f, dy = 90f, scale = 0.9f, rot = -12f),
        Look("shoal", "Shoal Lattice", 68),
        Look("oneui", "One UI Stacked", 79),
        Look("typo", "Typographic", 78),
        Look("matrix", "Night Matrix", 69, depth = 0),
    )

private const val KEY_STYLE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE
private const val KEY_DEPTH = "ichthys_lockscreen_depth"
private const val KEY_FILL = "ichthys_clock_fill"
private const val KEY_SCALE = "lock_screen_custom_clock_size_scale"
private const val KEY_MARGIN_TOP = "lock_screen_custom_clock_margin_top"
private const val KEY_OFFSET_X = "ichthys_clock_offset_x"
private const val KEY_ROTATION = "ichthys_clock_rotation"
private const val KEY_LOOK = "ichthys_lockscreen_look"
private const val KEY_DETAILS = "ichthys_clock_details"
private const val KEY_LOOKS = "ichthys_lockscreen_looks"

const val EXTRA_WALLPAPER = "wallpaper"

/** Wallpapers painted for the depth lock screen: a clear subject to stand in front of the clock. */
private val DEPTH_WALLPAPERS =
    listOf(Triple("koi", R.drawable.ichthys_wallpaper_koi, R.string.ichthys_wallpaper_koi))

/**
 * Sets the depth wallpaper [id] on the lock screen only (the home screen keeps its own). IchthysDepth
 * then finds its subject, and SystemUI draws it over the clock.
 */
fun setDepthWallpaper(context: Context, id: String) {
    val res = DEPTH_WALLPAPERS.firstOrNull { it.first == id }?.second ?: return
    Thread {
            try {
                context.resources.openRawResource(res).use {
                    WallpaperManager.getInstance(context)
                        .setStream(it, null, true, WallpaperManager.FLAG_LOCK)
                }
                // The lock screen's depth is what these are for.
                putSecure(context, KEY_DEPTH, 1)
            } catch (e: Exception) {
                android.util.Log.w("LockscreenEditor", "wallpaper $id not set", e)
            }
        }
        .start()
}

/**
 * Sets a photo the user picked as the lock screen wallpaper ([home]: the home screen's too) and
 * turns depth on; IchthysDepth then finds its subject. [done] runs on the main thread after.
 */
fun setPhotoWallpaper(context: Context, uri: Uri, home: Boolean, done: () -> Unit) {
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    Thread {
            try {
                val which =
                    if (home) WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
                    else WallpaperManager.FLAG_LOCK
                context.contentResolver.openInputStream(uri)?.use {
                    WallpaperManager.getInstance(context).setStream(it, null, true, which)
                }
                putSecure(context, KEY_DEPTH, 1)
            } catch (e: Exception) {
                android.util.Log.w("LockscreenEditor", "photo wallpaper not set", e)
            }
            main.post(done)
        }
        .start()
}

/** KeyguardCustomClockView's defaults: the clock's top padding and its margin setting. */
private const val BASE_TOP_DP = 72
private const val DEFAULT_MARGIN_TOP = 15

@Composable
fun LockscreenEditor(onDone: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dm = context.resources.displayMetrics
    val screenW = minOf(dm.widthPixels, dm.heightPixels)
    val screenH = maxOf(dm.widthPixels, dm.heightPixels)

    var saved by remember { mutableStateOf(readLooks(context)) }
    var look by remember { mutableStateOf(currentLook(context, saved)) }
    var guide by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(secureInt(context, KEY_DETAILS, 0)) }
    var pictures by remember { mutableStateOf(LockPictures.load(context, screenW, screenH)) }
    // Your own photo as the wallpaper: Android's photo picker, then lock screen or both.
    var pickedPhoto by remember { mutableStateOf<Uri?>(null) }
    val pickPhoto =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri -> pickedPhoto = uri }
    fun choosePhoto() {
        pickPhoto.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }
    pickedPhoto?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickedPhoto = null },
            title = { Text(stringResource(R.string.ichthys_wallpaper_use_for)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pickedPhoto = null
                    setPhotoWallpaper(context, uri, home = false) {
                        pictures = LockPictures.load(context, screenW, screenH)
                    }
                }) { Text(stringResource(R.string.ichthys_wallpaper_lock)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pickedPhoto = null
                    setPhotoWallpaper(context, uri, home = true) {
                        pictures = LockPictures.load(context, screenW, screenH)
                    }
                }) { Text(stringResource(R.string.ichthys_wallpaper_both)) }
            },
        )
    }
    val accent = MaterialTheme.colorScheme.primary
    val all = BUILT_IN + saved
    val base = all.firstOrNull { it.id == look.id }
    val edited =
        base == null ||
            abs(base.dx - look.dx) > 1 ||
            abs(base.dy - look.dy) > 1 ||
            abs(base.scale - look.scale) > 0.005f ||
            abs(base.rot - look.rot) > 0.5f ||
            base.style != look.style ||
            base.fill != look.fill ||
            base.depth != look.depth

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFF07090B))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // The preview: the real lock screen at screen size, scaled into the space it has.
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val k =
                minOf(
                    constraints.maxWidth.toFloat() / screenW,
                    constraints.maxHeight.toFloat() / screenH,
                )
            val shape = RoundedCornerShape(28.dp)
            Box(
                Modifier.size(
                        with(density) { (screenW * k).toDp() },
                        with(density) { (screenH * k).toDp() },
                    )
                    .clip(shape)
                    .border(BorderStroke(1.dp, Color(0x33F6F1EA)), shape)
                    .pointerInput(k) {
                        detectTransformGestures(panZoomLock = false) { _, pan, zoom, rotation ->
                            var dx = look.dx + pan.x / k
                            val dy = (look.dy + pan.y / k).coerceIn(-screenH * 0.1f, screenH * 0.45f)
                            dx = dx.coerceIn(-screenW * 0.4f, screenW * 0.4f)
                            val snap = abs(dx) < 8 * dm.density
                            if (snap) dx = 0f
                            guide = snap
                            var rot = look.rot + rotation
                            while (rot > 180) rot -= 360
                            while (rot < -180) rot += 360
                            for (t in listOf(-90f, 0f, 90f)) if (abs(rot - t) < 4) rot = t
                            look =
                                look.copy(
                                    dx = dx,
                                    dy = dy,
                                    scale = (look.scale * zoom).coerceIn(0.5f, 1.5f),
                                    rot = rot,
                                )
                        }
                    }
            ) {
                Box(
                    Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(
                            with(density) { screenW.toDp() },
                            with(density) { screenH.toDp() },
                        )
                        .graphicsLayer {
                            scaleX = k
                            scaleY = k
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    LockPreview(look, pictures, guide, accent, details = details != 0)
                }
            }
        }

        // What the clock is doing, and the look's actions.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                    "X" to "%+d px".format((look.dx).roundToInt()),
                    "Y" to "%+d px".format((look.dy).roundToInt()),
                    "SIZE" to "${(look.scale * 100).roundToInt()}%",
                    "ROT" to "%+d°".format(look.rot.roundToInt()),
                )
                .forEach { (k, v) ->
                    Column {
                        Text(k, fontSize = 9.sp, color = Color(0xFF9AA3A7), letterSpacing = 1.sp)
                        Text(v, fontSize = 12.sp, color = Color(0xFFF2ECE4),
                            fontFamily = FontFamily.Monospace)
                    }
                }
            Box(
                Modifier.size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (edited) accent else Color(0xFF5C6569))
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { base?.let { look = it } }, enabled = edited && base != null) {
                Text(stringResource(R.string.ichthys_editor_reset), fontSize = 13.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    val n = saved.size + 1
                    val mine =
                        look.copy(
                            id = "mine$n",
                            label = context.getString(R.string.ichthys_editor_my_look, n),
                        )
                    saved = saved + mine
                    writeLooks(context, saved)
                    look = mine
                }
            ) {
                Text(stringResource(R.string.ichthys_editor_save_look), fontSize = 13.sp, maxLines = 1)
            }
            OutlinedButton(
                // The ROM has no wallpaper picker app: any photo, through the photo picker.
                onClick = { choosePhoto() }
            ) {
                Text(stringResource(R.string.ichthys_editor_wallpaper), fontSize = 13.sp, maxLines = 1)
            }
        }

        // The choices, one scrolling row each.
        Column(Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState())) {
            EditorRow(stringResource(R.string.ichthys_editor_looks)) {
                all.forEach { l ->
                    Chip(l.label, l.id == look.id, { look = l }) {
                        FaceThumb(l.style, l, pictures)
                    }
                }
            }
            // Eighty faces: built only as they scroll into view.
            val names = remember { ClockUtils.getClockNames() }
            val faces = remember { ClockUtils.CLOCK_LAYOUTS.indices.drop(1).reversed() }
            Text(
                stringResource(R.string.ichthys_editor_face).uppercase(),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = Color(0xFF9AA3A7),
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(faces.size, key = { faces[it] }) { i ->
                    val style = faces[i]
                    Chip(names[style], style == look.style, { look = look.copy(style = style) }) {
                        FaceThumb(style, null, pictures)
                    }
                }
            }
            if (look.style == DEPTH_CLOCK_STYLE) {
                EditorRow(stringResource(R.string.ichthys_depth_fill_title)) {
                    listOf(
                            R.string.ichthys_depth_fill_solid,
                            R.string.ichthys_depth_fill_gradient,
                            R.string.ichthys_depth_fill_live,
                        )
                        .forEachIndexed { mode, label ->
                            Chip(stringResource(label), look.fill == mode, {
                                look = look.copy(fill = mode)
                            }) {
                                FaceThumb(look.style, look.copy(fill = mode), pictures)
                            }
                        }
                }
            }
            EditorRow(stringResource(R.string.ichthys_editor_wallpapers)) {
                Chip(stringResource(R.string.ichthys_wallpaper_photo), false, { choosePhoto() }) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFF0E1417)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", fontSize = 34.sp, color = Color(0xFF9FE7DD))
                    }
                }
                DEPTH_WALLPAPERS.forEach { (id, res, label) ->
                    Chip(stringResource(label), false, {
                        setDepthWallpaper(context, id)
                        pictures = LockPictures.load(context, screenW, screenH)
                    }) {
                        Image(
                            androidx.compose.ui.res.painterResource(res),
                            null,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            if (look.style == DEPTH_CLOCK_STYLE) {
                EditorRow(stringResource(R.string.ichthys_editor_status)) {
                    listOf(R.string.ichthys_editor_status_date, R.string.ichthys_editor_status_details)
                        .forEachIndexed { mode, label ->
                            Chip(stringResource(label), details == mode, {
                                details = mode
                                putSecure(context, KEY_DETAILS, mode)
                            }) {
                                FaceThumb(look.style, look, pictures)
                            }
                        }
                }
            }
            EditorRow(stringResource(R.string.ichthys_depth_title)) {
                Chip(stringResource(R.string.ichthys_depth_off), look.depth == 0, {
                    look = look.copy(depth = 0)
                }) {
                    FaceThumb(look.style, look.copy(depth = 0), pictures)
                }
                Chip(stringResource(R.string.ichthys_depth_on), look.depth != 0, {
                    look = look.copy(depth = 1)
                }) {
                    FaceThumb(look.style, look.copy(depth = 1), pictures)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = {
                    apply(context, look)
                    onDone()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.apply))
            }
        }
    }
}

/** The lock screen at screen size: wallpaper, guard rails, the clock, the subject over it. */
@Composable
private fun LockPreview(
    look: Look,
    pictures: LockPictures,
    guide: Boolean,
    accent: Color,
    rails: Boolean = true,
    details: Boolean = false,
) {
    val density = LocalDensity.current
    val dm = LocalContext.current.resources.displayMetrics
    Box(Modifier.fillMaxSize()) {
        pictures.backdrop?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } ?: Box(Modifier.fillMaxSize().background(Color(0xFF0B1418)))

        // Zones the clock may cross but should never cover: status bar, fingerprint, shortcuts.
        if (rails) ComposeCanvas(Modifier.fillMaxSize()) {
            val rail = Color(0x44F6F1EA)
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            drawRect(Color(0x22F6F1EA), size = androidx.compose.ui.geometry.Size(size.width, 40 * dm.density))
            val fp = Offset(size.width / 2f, size.height * 0.865f)
            drawCircle(rail, 60 * dm.density, fp, style = Stroke(2f, pathEffect = dash))
            val r = 34 * dm.density
            drawCircle(rail, r, Offset(r * 1.6f, size.height - r * 2.4f), style = Stroke(2f, pathEffect = dash))
            drawCircle(rail, r, Offset(size.width - r * 1.6f, size.height - r * 2.4f),
                style = Stroke(2f, pathEffect = dash))
            if (guide) {
                drawLine(accent, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height),
                    strokeWidth = 2f, pathEffect = dash)
            }
        }

        // The clock, placed like KeyguardCustomClockView places it.
        val top = with(density) { (40 + BASE_TOP_DP).dp.toPx() } + DEFAULT_MARGIN_TOP + look.dy
        ClockFace(
            look.style,
            look.fill,
            details,
            Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset { IntOffset(look.dx.roundToInt(), top.roundToInt()) }
                .graphicsLayer {
                    scaleX = look.scale
                    scaleY = look.scale
                    rotationZ = look.rot
                    transformOrigin = TransformOrigin(0.5f, if (look.rot != 0f) 0.5f else 0f)
                },
        )

        if (look.depth != 0) {
            pictures.cutout?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

/** A face, inflated from the picker's own layout, as the lock screen would show it. */
@Composable
private fun ClockFace(style: Int, fill: Int, details: Boolean, modifier: Modifier) {
    AndroidView(
        factory = { ctx -> FrameLayout(ctx) },
        update = { frame ->
            if (frame.tag != style) {
                frame.tag = style
                frame.removeAllViews()
                val layout = ClockUtils.CLOCK_LAYOUTS.getOrNull(style) ?: return@AndroidView
                LayoutInflater.from(frame.context).inflate(layout, frame, true)
            }
            // The look's own fill and status line, not whatever the lock screen has now.
            forEachDepthFace(frame) {
                it.previewFill = fill
                it.previewDetails = details
            }
        },
        modifier = modifier,
    )
}

private fun forEachDepthFace(
    view: android.view.View,
    action: (com.android.settingslib.ichthys.clock.DepthClockView) -> Unit,
) {
    when (view) {
        is com.android.settingslib.ichthys.clock.DepthClockView -> action(view)
        is ViewGroup -> for (i in 0 until view.childCount) forEachDepthFace(view.getChildAt(i), action)
    }
}

/**
 * A small lock screen with [style] on it (and [look]'s placement, depth and fill, when given):
 * the same preview as the big one, scaled down.
 */
@Composable
private fun FaceThumb(style: Int, look: Look?, pictures: LockPictures) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dm = context.resources.displayMetrics
    val screenW = minOf(dm.widthPixels, dm.heightPixels)
    val screenH = maxOf(dm.widthPixels, dm.heightPixels)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val k = constraints.maxWidth.toFloat() / screenW
        Box(
            Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(with(density) { screenW.toDp() }, with(density) { screenH.toDp() })
                .graphicsLayer {
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            LockPreview(
                (look ?: Look("face", "", style)).copy(style = style),
                pictures,
                guide = false,
                accent = Color.Transparent,
                rails = false,
            )
        }
    }
}

@Composable
private fun EditorRow(title: String, content: @Composable () -> Unit) {
    Text(
        title.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        color = Color(0xFF9AA3A7),
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit, thumb: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(64.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(64.dp, 128.dp)
                .clip(shape)
                .border(
                    if (on) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    else BorderStroke(1.dp, Color(0x14F6F1EA)),
                    shape,
                )
        ) {
            thumb()
        }
        Text(
            label,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = if (on) Color(0xFFF2ECE4) else Color(0xFF9AA3A7),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** The lock wallpaper cut to the screen, and its subject (IchthysDepth's mask), for previews. */
class LockPictures(val backdrop: ImageBitmap?, val cutout: ImageBitmap?) {
    companion object {
        fun load(context: Context, w: Int, h: Int): LockPictures =
            try {
                val user = UserHandle.myUserId()
                val wm = context.getSystemService(WallpaperManager::class.java)!!
                val pic =
                    wm.getBitmapAsUser(user, false, WallpaperManager.FLAG_LOCK, false)
                        ?: wm.getBitmapAsUser(user, false, WallpaperManager.FLAG_SYSTEM, true)
                if (pic == null) LockPictures(null, null)
                else {
                    val region = centerCrop(pic.width, pic.height, w, h)
                    val dst = Rect(0, 0, w / 2, h / 2)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    val back = Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
                    Canvas(back).drawBitmap(pic, region, dst, paint)
                    val key =
                        "L" + wm.getWallpaperIdForUser(WallpaperManager.FLAG_LOCK, user) +
                            "S" + wm.getWallpaperIdForUser(WallpaperManager.FLAG_SYSTEM, user)
                    val mask =
                        try {
                            context.contentResolver
                                .openFileDescriptor(
                                    Uri.parse("content://org.ichthys.depth/mask/$user/$key"), "r")
                                ?.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) }
                        } catch (e: Exception) {
                            null
                        }
                    val cut =
                        mask?.let {
                            val out = Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
                            val c = Canvas(out)
                            val sx = it.width.toFloat() / pic.width
                            val sy = it.height.toFloat() / pic.height
                            c.drawBitmap(it, Rect((region.left * sx).toInt(), (region.top * sy).toInt(),
                                (region.right * sx).toInt(), (region.bottom * sy).toInt()), dst, paint)
                            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                            c.drawBitmap(back, 0f, 0f, paint)
                            out.asImageBitmap()
                        }
                    LockPictures(back.asImageBitmap(), cut)
                }
            } catch (e: Exception) {
                LockPictures(null, null)
            }

        private fun centerCrop(pw: Int, ph: Int, w: Int, h: Int): Rect {
            val aspect = w.toFloat() / h
            return if (pw.toFloat() / ph > aspect) {
                val cw = (ph * aspect).toInt()
                Rect((pw - cw) / 2, 0, (pw - cw) / 2 + cw, ph)
            } else {
                val ch = (pw / aspect).toInt()
                Rect(0, (ph - ch) / 2, pw, (ph - ch) / 2 + ch)
            }
        }
    }
}

private fun secureInt(context: Context, key: String, def: Int) =
    Settings.Secure.getIntForUser(context.contentResolver, key, def, UserHandle.USER_CURRENT)

private fun putSecure(context: Context, key: String, value: Int) =
    Settings.Secure.putIntForUser(context.contentResolver, key, value, UserHandle.USER_CURRENT)

private fun readLooks(context: Context): List<Look> =
    try {
        val raw =
            Settings.Secure.getStringForUser(context.contentResolver, KEY_LOOKS, UserHandle.USER_CURRENT)
                ?: return emptyList()
        val a = JSONArray(raw)
        (0 until a.length()).map { Look.fromJson(a.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

private fun writeLooks(context: Context, looks: List<Look>) {
    val a = JSONArray()
    looks.forEach { a.put(it.toJson()) }
    Settings.Secure.putStringForUser(context.contentResolver, KEY_LOOKS, a.toString(),
        UserHandle.USER_CURRENT)
}

/** The lock screen as the settings have it now, as a look (named after a matching one). */
private fun currentLook(context: Context, saved: List<Look>): Look {
    val id =
        Settings.Secure.getStringForUser(context.contentResolver, KEY_LOOK, UserHandle.USER_CURRENT)
    val margin = secureInt(context, KEY_MARGIN_TOP, DEFAULT_MARGIN_TOP)
    val now =
        Look(
            id = id ?: "custom",
            label = "",
            style = secureInt(context, KEY_STYLE, 80).takeIf { it != 0 } ?: 80,
            fill = secureInt(context, KEY_FILL, 0),
            depth = secureInt(context, KEY_DEPTH, 0),
            dx = secureInt(context, KEY_OFFSET_X, 0).toFloat(),
            dy = (margin - DEFAULT_MARGIN_TOP).toFloat(),
            scale = secureInt(context, KEY_SCALE, 100) / 100f,
            rot = secureInt(context, KEY_ROTATION, 0).toFloat(),
        )
    val named = (BUILT_IN + saved).firstOrNull { it.id == now.id }
    return now.copy(label = named?.label ?: "")
}

/** Writes [look] to the lock screen. Switching between the stock clock and a face restarts SystemUI. */
private fun apply(context: Context, look: Look) {
    val oldStyle = secureInt(context, KEY_STYLE, 0)
    putSecure(context, KEY_FILL, look.fill)
    putSecure(context, KEY_DEPTH, look.depth)
    putSecure(context, KEY_SCALE, (look.scale * 100).roundToInt())
    putSecure(context, KEY_MARGIN_TOP, DEFAULT_MARGIN_TOP + look.dy.roundToInt())
    putSecure(context, KEY_OFFSET_X, look.dx.roundToInt())
    putSecure(context, KEY_ROTATION, look.rot.roundToInt())
    Settings.Secure.putStringForUser(context.contentResolver, KEY_LOOK, look.id,
        UserHandle.USER_CURRENT)
    if (look.style != oldStyle) {
        putSecure(context, KEY_STYLE, look.style)
        if (oldStyle == 0) {
            // From the stock clock to a face: the stock clock and smartspace overlays go away,
            // which takes a SystemUI restart (as the clock picker does).
            val theme = ThemeUtils(context)
            theme.setOverlayEnabled("android.theme.customization.hideclock",
                "com.android.systemui.clocks.hideclock", "android")
            theme.setOverlayEnabled("android.theme.customization.smartspace",
                "com.android.systemui.hide.smartspace", "com.android.systemui")
            SystemUtils.restartSystemUI(context)
        }
    }
}
