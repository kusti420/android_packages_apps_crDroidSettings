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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.settings.R
import com.android.settingslib.ichthys.clock.DepthClockView
import com.android.settingslib.ichthys.clock.IchthysClockFace
import com.android.settingslib.ichthys.clock.LockWeather
import com.android.settingslib.ichthys.clock.LockWeatherView
import com.android.settingslib.ichthys.sky.LivingSky
import com.crdroid.settings.fragments.lockscreen.ClockUtils
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What can be selected on the preview. */
enum class LockElement { CLOCK, DATE, WEATHER, NOTIFICATIONS, SHORTCUT_START, SHORTCUT_END }

/**
 * Where everything ended up on the previewed lock screen, in screen pixels, and [k], the preview's
 * scale (preview px = screen px * k). For hit tests and the selection frame.
 * IchthysOS polish: the clock's rectangle hugs what its face shows (not its whole box) and is
 * turned by [clockRot] degrees about [clockPivot], as the clock itself is.
 */
class LockLayout(
    val k: Float,
    val rects: Map<LockElement, Rect>,
    val clockCentreX: Float,
    val clockRot: Float = 0f,
    val clockPivot: Offset = Offset.Zero,
) {
    /** [p] in the clock's own, unturned frame (where [rects]' clock rectangle is). */
    fun unturn(p: Offset): Offset = turn(p, clockPivot, -clockRot)

    /** The selection frame's corners around the clock (its handles), turned like the clock. */
    fun clockCorners(pad: Float): List<Offset> {
        val r = rects[LockElement.CLOCK] ?: return emptyList()
        val f = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
        return listOf(f.topLeft, f.topRight, f.bottomRight, f.bottomLeft)
            .map { turn(it, clockPivot, clockRot) }
    }

    /** The clock's middle on the screen (turned like the clock). */
    val clockCentre: Offset
        get() = rects[LockElement.CLOCK]?.let { turn(it.center, clockPivot, clockRot) } ?: Offset.Zero
}

/** [p] turned by [degrees] (clockwise on the screen, like rotationZ) about [pivot]. */
fun turn(p: Offset, pivot: Offset, degrees: Float): Offset {
    if (degrees == 0f) return p
    val a = Math.toRadians(degrees.toDouble())
    val c = cos(a).toFloat()
    val s = sin(a).toFloat()
    val dx = p.x - pivot.x
    val dy = p.y - pivot.y
    return Offset(pivot.x + dx * c - dy * s, pivot.y + dx * s + dy * c)
}

/** The selection frame's distance from what it frames. */
const val FRAME_PAD_DP = 10f

/** The screen in its natural (portrait) orientation, in pixels. */
@Composable
fun rememberScreen(): IntSize {
    val dm = LocalContext.current.resources.displayMetrics
    return remember { IntSize(minOf(dm.widthPixels, dm.heightPixels), maxOf(dm.widthPixels, dm.heightPixels)) }
}

/**
 * IchthysOS lock screen editor v2: the lock screen of [design] on [picture], drawn at the screen's
 * real size and scaled into the space it has. The face is the picker's own layout (what
 * KeyguardCustomClockView inflates), placed like KeyguardCustomClockView places it; the Depth face
 * gets the design's options and the picture's outline (its Shape mode works in the preview).
 * - [chrome]: shortcuts, fingerprint, notification card (off for small thumbnails).
 * - [input]: a modifier for the phone-sized box, given the scale (the editor's gestures).
 * - [overlay]: drawn last, in screen pixels (guides).
 * - [selected]: framed (IchthysOS polish: the frame glides from one selection to the next, hugs
 *   what the element shows and turns with the clock).
 * - [zoom]: 0 in its place .. 1 filling the window (the editor's open / close, read while drawing).
 * - IchthysOS Living Sky: the picture graded as the lock screen grades it, at [skyMinute] (minutes
 *   after midnight; null: now). Its weather moves only in the editor's own preview (with
 *   [onLayout]), not in thumbnails.
 */
@Composable
fun LockScreenPreview(
    design: LockDesign,
    picture: LockPicture,
    modifier: Modifier = Modifier,
    shortcuts: LockShortcuts.Catalog? = null,
    chrome: Boolean = true,
    rounded: Boolean = true,
    input: (Float) -> Modifier = { Modifier },
    onLayout: ((LockLayout) -> Unit)? = null,
    overlay: (DrawScope.(LockLayout) -> Unit)? = null,
    selected: LockElement? = null,
    zoom: () -> Float = { 0f },
    skyMinute: Float? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screen = rememberScreen()
    val geo = remember(screen) { LockGeometry(context, screen.width, screen.height) }
    var faceSize by remember { mutableStateOf(IntSize.Zero) }
    val accent = remember { context.getColor(android.R.color.system_accent1_100) }
    // IchthysOS lock screen weather: where it was drawn (screen px), in the face's date line or
    // by the weather view (under the clock, top corner). Reported after drawing.
    var faceWeather by remember { mutableStateOf<Rect?>(null) }
    // The weather view's own content bounds (its px); placed on the screen where the view is now,
    // so the selection frame follows the line when only its position changes.
    var viewWeatherLocal by remember { mutableStateOf<Rect?>(null) }
    // IchthysOS polish: what the face shows (screen px, unturned) and, on the Depth face, where
    // it wants the weather line under the clock (screen px), for the face style they came from.
    var faceContent by remember { mutableStateOf<FaceReport?>(null) }
    // IchthysOS: whether the Depth face's fit lets the subject stand in front (DepthClockView).
    var faceDepth by remember { mutableStateOf(true) }
    // IchthysOS: the glass clock's frost and contrast come from the previewed picture, as on the
    // lock screen (ClockBackdrop).
    val frostSource = picture.source
    LaunchedEffect(frostSource) {
        if (frostSource != null) {
            com.android.settingslib.ichthys.clock.ClockBackdrop.publishPhoto(
                com.android.settingslib.ichthys.clock.ClockBackdrop.measure(frostSource))
            com.android.settingslib.ichthys.clock.ClockBackdrop.publishFrost(
                frostSource, screen.width, screen.height)
        }
    }
    val motion = remember { FrameMotion() }
    val placedAt = remember { FloatArray(4) }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val k = minOf(constraints.maxWidth.toFloat() / screen.width,
            constraints.maxHeight.toFloat() / screen.height)
        val radius = with(density) { (if (rounded) 26.dp else 10.dp).toPx() }
        val w = screen.width.toFloat()
        val h = screen.height.toFloat()
        val d = geo.density
        val top = geo.top0 + design.dy
        // Where things are, in screen px.
        val faceW = faceSize.width.takeIf { it > 0 }?.toFloat() ?: geo.faceWidth.toFloat()
        val faceH = faceSize.height.toFloat()
        val cx = w / 2f + design.dx
        // The face's whole box, scaled about its top centre (as KeyguardCustomClockView scales).
        val whole = Rect(cx - faceW * design.scale / 2f, top, cx + faceW * design.scale / 2f,
            top + faceH * design.scale)
        val report = faceContent?.takeIf { it.style == design.style }
        val clock = report?.content ?: whole
        // Turned about the scaled box's middle (KeyguardCustomClockView: pivot in the middle, the
        // scaled top put back).
        val pivot = Offset(cx, top + faceH * design.scale / 2f)
        val dateH = 30 * d * design.scale
        val date =
            if (design.datePos == DepthClockView.DATE_BELOW)
                Rect(clock.left, clock.bottom - dateH - 8 * d, clock.right, clock.bottom)
            else Rect(clock.left, clock.top, clock.right, clock.top + dateH)
        val weatherPlace = design.weatherPlace
        // The weather line under the clock: where the Depth face wants it (under the digits you
        // see), else under the face's scaled box, as SystemUI's LockWeatherView places it.
        // IchthysOS: and under the turned clock's lowest point (a turn takes one side lower).
        val turn = Math.toRadians(design.rot.toDouble())
        val turnDrop = (clock.width / 2f * kotlin.math.abs(kotlin.math.sin(turn)) -
            clock.height / 2f * (1 - kotlin.math.abs(kotlin.math.cos(turn)))).toFloat()
        val underY = (report?.underTop ?: whole.bottom) + maxOf(0f, turnDrop)
        val weatherAtTop = weatherPlace == LockWeather.POS_TOP_START ||
            weatherPlace == LockWeather.POS_TOP_END
        val weatherY = if (weatherAtTop) (LockKeys.STATUS_BAR_DP + WEATHER_TOP_DP) * d else underY
        val viewWeather = viewWeatherLocal?.translate(0f, weatherY)
        val weather =
            if (!design.weather) null
            else if (weatherPlace == LockWeather.POS_DATE) faceWeather
            else viewWeather
        // IchthysOS polish: the notifications start under the clock as you see it (and its
        // weather line), and with a subject in front never higher than under its face: the same
        // SubjectOutline.faceSafe line SystemUI's upper region keeps to.
        val visibleBottom =
            if (weather != null && weatherPlace == LockWeather.POS_UNDER) weather.bottom + d
            else underY
        val outline = picture.outline
        val floor =
            if (design.depth != DepthClockView.DEPTH_OFF && outline != null && outline.faceSafe > 0f)
                outline.faceSafe * h
            else 0f
        val notifTop = (maxOf(visibleBottom, floor) + 24 * d).coerceAtMost(h * 0.74f)
        val notif = Rect(16 * d, notifTop, w - 16 * d, notifTop + 72 * d)
        val r = 34 * d
        val scStart = Rect(Offset(r * 1.6f, h - r * 2.4f), r)
        val scEnd = Rect(Offset(w - r * 1.6f, h - r * 2.4f), r)
        val rects = HashMap<LockElement, Rect>()
        rects[LockElement.CLOCK] = clock
        if (design.isDepthFace && design.showDate) rects[LockElement.DATE] = date
        if (weather != null) rects[LockElement.WEATHER] = weather
        rects[LockElement.NOTIFICATIONS] = notif
        rects[LockElement.SHORTCUT_START] = scStart
        rects[LockElement.SHORTCUT_END] = scEnd
        val layout = LockLayout(k, rects, cx, design.rot, pivot)
        if (onLayout != null) SideEffect { onLayout(layout) }

        // The selection frame's glide (see FrameMotion).
        if (onLayout != null) {
            LaunchedEffect(selected) { motion.retarget(selected, d) }
        }

        Box(
            Modifier.size(with(density) { (w * k).toDp() }, with(density) { (h * k).toDp() })
                .onPlaced { c ->
                    val at = c.positionInWindow()
                    var root = c
                    while (true) root = root.parentLayoutCoordinates ?: break
                    placedAt[0] = at.x
                    placedAt[1] = at.y
                    placedAt[2] = root.size.width.toFloat()
                    placedAt[3] = root.size.height.toFloat()
                }
                .graphicsLayer {
                    // IchthysOS polish: the editor opens out of (and closes into) the lock screen
                    // itself: at 1 this box covers the window, square-cornered.
                    val z = zoom().coerceIn(0f, 1f)
                    if (z > 0f && size.width > 0f && placedAt[2] > 0f) {
                        val cover = maxOf(placedAt[2] / size.width, placedAt[3] / size.height)
                        val sc = 1f + (cover - 1f) * z
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = sc
                        scaleY = sc
                        translationX = -placedAt[0] * z
                        translationY = -placedAt[1] * z
                    }
                    shape = RoundedCornerShape(radius * (1f - z))
                    clip = true
                }
                .then(input(k))
        ) {
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .graphicsLayer {
                        scaleX = k
                        scaleY = k
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                // IchthysOS Living Sky: the same grade (and weather) the lock screen draws.
                val sky =
                    if (design.livingSky && picture.livingSky) {
                        rememberPreviewSky(design, picture, skyMinute, motion = onLayout != null,
                            screen.width, screen.height)
                    } else null
                picture.backdrop?.let {
                    if (sky != null) LivingSkyImage(sky, picture, null, Modifier.fillMaxSize())
                    else Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } ?: if (chrome) Box(Modifier.fillMaxSize().background(Color(0xFF0B1418))) else Unit

                // The face, as KeyguardCustomClockView lays it out: 40 dp in from the screen's
                // sides, its top at 72 dp + margin under the status bar, scaled about its top
                // centre and turned about the scaled box's middle.
                AndroidView(
                    factory = { FaceFrame(it) },
                    update = { frame ->
                        frame.onDepthApplies = { a -> if (a != faceDepth) faceDepth = a }
                        frame.onWeather =
                            if (onLayout != null) { b -> if (b != faceWeather) faceWeather = b } else null
                        frame.onContent =
                            if (onLayout != null) { rep -> if (rep != faceContent) faceContent = rep }
                            else null
                        frame.bind(design, picture, geo, accent)
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = LockKeys.SIDE_DP.dp)
                            .offset { IntOffset(design.dx.roundToInt(), top.roundToInt()) }
                            .onSizeChanged { faceSize = it }
                            .graphicsLayer {
                                scaleX = design.scale
                                scaleY = design.scale
                                rotationZ = design.rot
                                alpha = design.opacity.coerceIn(0, 100) / 100f
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                translationY = -(1f - design.scale) * size.height / 2f
                            },
                )

                // The subject in front (every depth mode), unless the face's fit stepped back
                // (no room above the subject: the clock stands whole in front, as on the lock screen).
                if (design.depth != DepthClockView.DEPTH_OFF && faceDepth) {
                    picture.cutout?.let {
                        val mask = picture.cutoutMask
                        if (sky != null && mask != null) {
                            LivingSkyImage(sky, picture, mask, Modifier.fillMaxSize())
                        } else {
                            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }

                // IchthysOS: the weather under the clock or in a top corner, in front of the
                // subject, as SystemUI's LockWeatherView draws it there (the same view).
                if (design.weather && weatherPlace != LockWeather.POS_DATE) {
                    val atTop = weatherAtTop
                    val y = weatherY
                    // Inside the screen's side margins, however far the clock was moved or grown.
                    val spanL = if (atTop) LockKeys.SIDE_DP * d else maxOf(LockKeys.SIDE_DP * d, whole.left)
                    val spanR =
                        if (atTop) w - LockKeys.SIDE_DP * d else minOf(w - LockKeys.SIDE_DP * d, whole.right)
                    key(atTop) {
                        AndroidView(
                            factory = {
                                LockWeatherView(it).apply {
                                    slot = if (atTop) LockWeatherView.SLOT_TOP else LockWeather.POS_UNDER
                                }
                            },
                            update = { v ->
                                v.spanLeft = spanL
                                v.spanRight = spanR
                                v.previewDepthFace = design.isDepthFace
                                v.previewDateShown = design.showDate
                                v.previewAlign = design.dateAlign
                                v.previewOptions = design.weatherOptions
                                v.onContentBounds =
                                    if (onLayout != null) { b ->
                                        val s = b?.let { Rect(it.left, it.top, it.right, it.bottom) }
                                        if (s != viewWeatherLocal) viewWeatherLocal = s
                                    } else null
                                v.invalidate()
                            },
                            modifier = Modifier.fillMaxWidth().offset { IntOffset(0, y.roundToInt()) },
                        )
                    }
                } else if (viewWeatherLocal != null) {
                    // Gone from the view: no stale rectangle to tap.
                    SideEffect { viewWeatherLocal = null }
                }

                if (chrome) {
                    if (design.showNotifications) {
                        NotificationCard(notif, design.privateNotifications)
                    }
                    // IchthysOS: a shortcut the lock screen won't show (none, or one that needs
                    // setting up, like Device controls without a controls app) is a faint ghost
                    // here: still there to tap and change, not passed off as shown.
                    val start = shortcuts?.byId(design.shortcutStart)
                    val end = shortcuts?.byId(design.shortcutEnd)
                    Shortcut(scStart, start?.icon, shown = start?.enabled == true)
                    Shortcut(scEnd, end?.icon, shown = end?.enabled == true)
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color(0x80F6F1EA), 26 * d, Offset(w / 2f, h * 0.865f),
                            style = Stroke(2 * d))
                    }
                }
                if (overlay != null || onLayout != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        overlay?.invoke(this, layout)
                        val target = selected?.let { rects[it] }
                        motion.draw(this, target, selected == LockElement.CLOCK,
                            if (selected == LockElement.CLOCK) design.rot else 0f,
                            if (selected == LockElement.CLOCK) pivot else target?.center ?: Offset.Zero, d)
                    }
                }
            }
        }
    }
}

/**
 * IchthysOS Living Sky in the preview: the grade of the moment, the weather the grade paints into
 * the scene, and (in the editor's own preview) its frames, as SystemUI's LivingSkyMotion; shared by
 * the backdrop and the subject drawn over the clock.
 */
private class PreviewSky(val width: Int, val height: Int) {
    var grade: LivingSky.Grade? = null
    var info: LivingSky.SkyInfo? = null
    var intensity = 1f
    var weather: LivingSky.Weather? = null
    var time = 0f
    var flash = 0f
    val frame = mutableLongStateOf(0L)
}

@Composable
private fun rememberPreviewSky(
    design: LockDesign,
    picture: LockPicture,
    minute: Float?,
    motion: Boolean,
    w: Int,
    h: Int,
): PreviewSky {
    val context = LocalContext.current
    val sky = remember(w, h) { PreviewSky(w, h) }
    // The lock screen's weather (LockWeather is filled while someone listens).
    var now by remember { mutableStateOf(LockWeather.current) }
    DisposableEffect(Unit) {
        val listener: () -> Unit = { now = LockWeather.current }
        LockWeather.addListener(context, listener)
        now = LockWeather.current
        onDispose { LockWeather.removeListener(listener) }
    }
    // Showing now: a new grade every minute, as on the lock screen.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(minute) {
        if (minute != null) return@LaunchedEffect
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            tick++
        }
    }
    val info = picture.skyInfo ?: LivingSky.SkyInfo.UNKNOWN
    val weather = if (design.skyWeather) now?.let { LivingSky.weatherFor(it.code) } else null
    val grade =
        remember(tick, minute, weather, info, now?.sunrise, now?.sunset) {
            val times = LivingSky.sunTimes(context)
            val sun =
                if (minute != null) {
                    LivingSky.sun(times, minute,
                        java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR))
                } else LivingSky.sunNow(times)
            LivingSky.grade(sun, weather, info.horizon, LivingSky.moonPhase())
        }
    sky.grade = grade
    sky.info = info
    sky.intensity = design.skyIntensity / 100f
    // IchthysOS: the Depth face's contrast follows the preview's graded sky (as on the lock screen).
    SideEffect {
        com.android.settingslib.ichthys.clock.ClockBackdrop.publish(
            LivingSky.brightnessBehindClock(grade, sky.intensity))
    }

    val saver = remember {
        context.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
    }
    val go = motion && !saver
    val wx = weather?.takeIf { it.type != LivingSky.TYPE_NONE }
    sky.weather = weather
    DisposableEffect(Unit) {
        onDispose { sky.flash = 0f }
    }
    val stars = grade.stars > 0f
    val storm = wx?.storm == true && android.animation.ValueAnimator.areAnimatorsEnabled()
    val falling = wx != null && (wx.type == LivingSky.TYPE_RAIN || wx.type == LivingSky.TYPE_SNOW)
    LaunchedEffect(wx, stars, storm, go) {
        if (!go || (wx == null && !stars)) return@LaunchedEffect
        val step =
            when {
                falling || storm -> 15_500_000L
                wx != null -> 41_000_000L
                else -> 40_000_000L
            }
        var last = 0L
        var bolt = 0f
        var nextBolt = 2f + 6f * kotlin.random.Random.nextFloat()
        while (true) {
            withFrameNanos { t ->
                if (last != 0L && t - last < step) return@withFrameNanos
                val dt = if (last == 0L) 0f else ((t - last) / 1e9f).coerceAtMost(0.1f)
                last = t
                sky.time += dt
                if (storm) {
                    nextBolt -= dt
                    if (nextBolt <= 0f) {
                        bolt = 0f
                        nextBolt = 4f + 10f * kotlin.random.Random.nextFloat()
                    }
                    bolt += dt
                    sky.flash =
                        if (bolt < 0.6f) {
                            0.55f * kotlin.math.exp(-bolt / 0.07f) +
                                0.3f * kotlin.math.exp(-kotlin.math.abs(bolt - 0.22f) / 0.05f)
                        } else 0f
                }
                sky.frame.longValue = t
            }
        }
    }
    return sky
}

/** The graded [picture] over the previewed screen (screen px); with [mask], only the subject. */
@Composable
private fun LivingSkyImage(sky: PreviewSky, picture: LockPicture, mask: Bitmap?, modifier: Modifier) {
    val src = picture.source ?: return
    val skyMask = picture.sky ?: return
    val painter = remember { LivingSky.Painter() }
    val shader = remember(src, sky.width, sky.height) { LivingSky.fitted(src, sky.width, sky.height) }
    val skyShader =
        remember(skyMask, sky.width, sky.height) { LivingSky.fitted(skyMask, sky.width, sky.height) }
    // The subject is the nearest thing in the scene: rain and snow behind it stay behind.
    val subjectMask = picture.cutoutMask
    val subject =
        remember(subjectMask, sky.width, sky.height) {
            subjectMask?.let { LivingSky.fitted(it, sky.width, sky.height) }
        }
    val grade = sky.grade ?: return
    val info = sky.info ?: return
    val intensity = sky.intensity
    Canvas(modifier) {
        sky.frame.longValue
        painter.bind(shader, skyShader, info, grade, intensity, sky.width,
            sky.height, density, sky.time, sky.flash, sky.weather, subject)
        drawIntoCanvas {
            if (mask != null) painter.drawMasked(it.nativeCanvas, sky.width, sky.height, mask)
            else painter.draw(it.nativeCanvas, sky.width, sky.height)
        }
    }
}

/** IchthysOS polish: what a face reported: its style, content (screen px), under-line top. */
data class FaceReport(val style: Int, val content: Rect?, val underTop: Float?)

/**
 * IchthysOS polish: the selection frame's motion. A new selection glides out of the last frame
 * (rectangle, turn and pivot in between, a soft spring); the first one contracts onto its element
 * as it fades in; deselecting fades it out where it was. While the selection stays, the frame
 * follows its element exactly (drags, pinches), every frame.
 */
private class FrameMotion {
    val morph = Animatable(1f)
    val alpha = Animatable(0f)
    private var from: Rect? = null
    private var fromRot = 0f
    private var fromPivot = Offset.Zero
    private var last: Rect? = null
    private var lastRot = 0f
    private var lastPivot = Offset.Zero
    private var lastHandles = false

    suspend fun retarget(selected: LockElement?, density: Float) {
        if (selected == null) {
            alpha.animateTo(0f, tween(140))
            return
        }
        val wasShown = alpha.value > 0.05f && last != null
        from = if (wasShown) last else null
        fromRot = lastRot
        fromPivot = lastPivot
        morph.snapTo(0f)
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(1f, tween(if (wasShown) 90 else 180)) }
            morph.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f))
        }
    }

    fun draw(scope: DrawScope, target: Rect?, handles: Boolean, rot: Float, pivot: Offset, d: Float) {
        val a = alpha.value
        if (a <= 0.01f) return
        val r: Rect
        val turnBy: Float
        val about: Offset
        if (target != null) {
            val t = morph.value
            val start = from ?: Rect(target.left - 14 * d, target.top - 14 * d,
                target.right + 14 * d, target.bottom + 14 * d)
            r = lerp(start, target, t)
            turnBy = if (from != null) fromRot + (rot - fromRot) * t else rot
            about = if (from != null) lerpOffset(fromPivot, pivot, t) else pivot
            last = r
            lastRot = turnBy
            lastPivot = about
            lastHandles = handles
        } else {
            r = last ?: return
            turnBy = lastRot
            about = lastPivot
        }
        with(scope) {
            rotate(turnBy, about) { selectionFrame(r, if (target != null) handles else lastHandles, d, a) }
        }
    }

    private fun lerp(a: Rect, b: Rect, t: Float) =
        Rect(a.left + (b.left - a.left) * t, a.top + (b.top - a.top) * t,
            a.right + (b.right - a.right) * t, a.bottom + (b.bottom - a.bottom) * t)

    private fun lerpOffset(a: Offset, b: Offset, t: Float) =
        Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

/** SystemUI's top-corner weather distance under the status bar (WEATHER_TOP_DP there). */
private const val WEATHER_TOP_DP = 10

/** A small, fixed lock screen for rows and grids (no chrome). */
@Composable
fun MiniLock(design: LockDesign, picture: LockPicture, modifier: Modifier = Modifier) {
    LockScreenPreview(design, picture, modifier, chrome = false, rounded = false)
}

@Composable
private fun NotificationCard(at: Rect, showContent: Boolean) {
    val density = LocalDensity.current
    Column(
        Modifier.offset { IntOffset(at.left.roundToInt(), at.top.roundToInt()) }
            .size(with(density) { at.width.toDp() }, with(density) { at.height.toDp() })
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xC81E1A1E))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(stringResource(R.string.ichthys_ed2_notif_sample_title), fontSize = 13.sp,
            color = Color(0xE6F6F1EA))
        Text(
            stringResource(
                if (showContent) R.string.ichthys_ed2_notif_sample_text
                else R.string.ichthys_ed2_notif_hidden),
            fontSize = 12.sp,
            color = Color(0x99F6F1EA),
        )
    }
}

@Composable
private fun Shortcut(at: Rect, icon: Drawable?, shown: Boolean = true) {
    val density = LocalDensity.current
    val image = remember(icon) { icon?.let { toImage(it) } }
    Box(
        Modifier.offset { IntOffset(at.left.roundToInt(), at.top.roundToInt()) }
            .graphicsLayer { alpha = if (shown) 1f else 0.3f }
            .size(with(density) { at.width.toDp() }, with(density) { at.height.toDp() })
            .clip(RoundedCornerShape(50))
            .background(Color(0xB31E1A1E)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) Image(image, null, Modifier.size(28.dp))
    }
}

private fun toImage(d: Drawable): ImageBitmap {
    val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val copy = d.mutate()
    copy.setTint(AColor.rgb(246, 241, 234))
    copy.setBounds(0, 0, 96, 96)
    copy.draw(c)
    return b.asImageBitmap()
}

/**
 * Hosts one face (the picker's own layout) and gives it the design: colours the way
 * KeyguardCustomClockView applies them, the drawn faces' options, and for the Depth face the
 * preview values (fill, depth mode, shape, font, date line, the picture's outline and where the
 * face is on the previewed screen, so its Shape mode lands where the lock screen's will).
 */
private class FaceFrame(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var design: LockDesign? = null
    private var picture: LockPicture? = null
    private var geo: LockGeometry? = null

    /** IchthysOS: told where the Depth face drew the weather, in screen px (null: nowhere). */
    var onWeather: ((Rect?) -> Unit)? = null
    /** IchthysOS: told whether the Depth face lets the subject stand in front of it. */
    var onDepthApplies: ((Boolean) -> Unit)? = null
    private var weatherLocal: android.graphics.RectF? = null
    private var depthPlace: FloatArray? = null

    /**
     * IchthysOS polish: told what the face shows (screen px, unturned) and, for the Depth face,
     * where it wants the weather line under the clock (screen px).
     */
    var onContent: ((FaceReport) -> Unit)? = null
    private var depthContent: android.graphics.RectF? = null
    private var depthUnder = Float.NaN
    private val leafBounds = android.graphics.RectF()

    private var boundLook: LockDesign? = null
    private var boundPicture: LockPicture? = null
    private var boundAccent = 0

    fun bind(d: LockDesign, p: LockPicture, g: LockGeometry, accent: Int) {
        design = d
        picture = p
        geo = g
        // IchthysOS: moving, sizing or turning the clock changes only where it is drawn (the
        // preview's offset and scale, outside this view). Re-applying every option to the face's
        // views on each frame of a drag made the editor stutter; only a real change does it.
        val look = d.copy(dx = 0f, dy = 0f, scale = 1f, rot = if (d.rot != 0f) 1f else 0f)
        if (look == boundLook && p === boundPicture && accent == boundAccent && tag == d.style) {
            place()
            if (!d.isDepthFace && onContent != null) post { emitContent() }
            return
        }
        boundLook = look
        boundPicture = p
        boundAccent = accent
        if (tag != d.style) {
            tag = d.style
            removeAllViews()
            depthContent = null
            depthUnder = Float.NaN
            val layout = ClockUtils.CLOCK_LAYOUTS.getOrNull(d.style)
            if (layout != null) {
                try {
                    LayoutInflater.from(context).inflate(layout, this, true)
                } catch (e: Exception) {
                    android.util.Log.w("LockEditor2", "face ${d.style} not inflated", e)
                }
            }
        }
        val ink = d.inkOrNull(accent)
        val dotShape =
            Settings.Secure.getInt(context.contentResolver, "ichthys_clock_dot_shape",
                IchthysClockFace.DOT_ROUND)
        walk(this) { v ->
            if (v is IchthysClockFace) {
                when (d.colorMode) {
                    LockKeys.MODE_ACCENT -> v.setClockColors(accent, AColor.WHITE)
                    LockKeys.MODE_CUSTOM -> v.setClockColors(d.color, accent)
                    else -> v.setClockColors(AColor.WHITE, accent)
                }
                v.setClockOptions(d.showDate, dotShape, true)
            }
            if (v is TextView && ink != null) {
                val roles = (v.tag as? String)?.split("|") ?: emptyList()
                if (roles.any { it.startsWith("text") || it.startsWith("accent") }) v.setTextColor(ink)
            }
            if (v is DepthClockView) {
                v.previewFill = d.fill
                v.previewDetails = d.details
                // A turned clock has no shape on the lock screen either (DepthClockView.placement).
                v.previewDepth =
                    if (d.rot != 0f && d.depth == DepthClockView.DEPTH_SHAPE) DepthClockView.DEPTH_OVERLAP
                    else d.depth
                v.previewShapeStyle = d.shape
                v.previewFont = d.font
                v.previewGlassFrost = d.glassFrost
                v.previewGlassOpacity = d.glassOpacity
                v.previewDatePos = d.datePos
                v.previewDateAlign = d.dateAlign
                v.previewOutline = p.outline
                v.previewWeather = d.weatherOptions
                v.onWeatherPlaced = { r ->
                    weatherLocal = r
                    // Out of the face's draw: the preview's state changes on the next frame.
                    post { emitWeather() }
                }
                v.onDepthApplies = { a -> onDepthApplies?.invoke(a) }
                v.onFaceLayout = { r, under ->
                    depthContent = r
                    depthUnder = under
                    post { emitContent() }
                }
            }
        }
        place()
        // Other faces: their pieces moved or scaled with the design.
        if (!d.isDepthFace && onContent != null) post { emitContent() }
    }

    /**
     * IchthysOS polish: what the face shows, on the previewed screen: the Depth face's own report,
     * else the union of its visible pieces (text by its lines, not by its views' boxes).
     */
    private fun emitContent() {
        val cb = onContent ?: return
        val d = design ?: return
        val g = geo ?: return
        val pl = depthPlace
        val dc = depthContent
        if (d.isDepthFace && pl != null && dc != null) {
            cb(FaceReport(d.style,
                Rect(pl[0] + dc.left * pl[2], pl[1] + dc.top * pl[2], pl[0] + dc.right * pl[2],
                    pl[1] + dc.bottom * pl[2]),
                if (depthUnder.isNaN()) null else pl[1] + depthUnder * pl[2]))
            return
        }
        if (d.isDepthFace) return
        leafBounds.setEmpty()
        val root = android.graphics.Matrix()
        for (i in 0 until childCount) collect(getChildAt(i), root, null)
        if (leafBounds.isEmpty) {
            cb(FaceReport(d.style, null, null))
            return
        }
        val fw = width.toFloat()
        fun sx(x: Float) = LockKeys.SIDE_DP * g.density + d.dx + fw / 2f + (x - fw / 2f) * d.scale
        fun sy(y: Float) = g.top0 + d.dy + y * d.scale
        // What runs past the screen's sides isn't seen: the frame stays on the screen.
        val sw = g.width.toFloat()
        val l = sx(leafBounds.left).coerceIn(0f, sw)
        val r = sx(leafBounds.right).coerceIn(0f, sw)
        if (r <= l) {
            cb(FaceReport(d.style, null, null))
            return
        }
        cb(FaceReport(d.style, Rect(l, sy(leafBounds.top), r, sy(leafBounds.bottom)), null))
    }

    private val collectRect = android.graphics.RectF()

    /**
     * Adds what [v] draws to [leafBounds], in this frame's px: [m] maps [v]'s parent to this frame,
     * and each view's own turn and scale are applied (the Block face's turned digits). Text counts
     * by its lines, a background or a picture by its whole box; a bare spacer counts for nothing.
     * Anything a clipping parent cuts off is left out.
     */
    private fun collect(v: View, m: android.graphics.Matrix, clip: android.graphics.RectF?) {
        if (v.visibility != View.VISIBLE || v.alpha == 0f || v.width == 0 || v.height == 0) return
        val vm = android.graphics.Matrix(m)
        vm.preTranslate(v.left.toFloat(), v.top.toFloat())
        if (!v.matrix.isIdentity) vm.preConcat(v.matrix)
        fun add(l: Float, t: Float, r: Float, b: Float) {
            collectRect.set(l, t, r, b)
            vm.mapRect(collectRect)
            if (clip != null && !collectRect.intersect(clip)) return
            leafBounds.union(collectRect)
        }
        val bg = v.background
        val hasBg = bg != null && bg.alpha != 0 &&
            !(bg is android.graphics.drawable.ColorDrawable && AColor.alpha(bg.color) == 0)
        if (hasBg) add(0f, 0f, v.width.toFloat(), v.height.toFloat())
        if (v is ViewGroup) {
            var inner = clip
            if (v.clipChildren) {
                val c = android.graphics.RectF(0f, 0f, v.width.toFloat(), v.height.toFloat())
                vm.mapRect(c)
                if (inner != null && !c.intersect(inner)) return
                inner = c
            }
            for (i in 0 until v.childCount) collect(v.getChildAt(i), vm, inner)
            return
        }
        val tl = (v as? TextView)?.layout
        if (v is TextView) {
            if (tl == null || tl.lineCount == 0 || v.text.isNullOrEmpty()) return
            var l = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            for (i in 0 until tl.lineCount) {
                l = minOf(l, tl.getLineLeft(i))
                r = maxOf(r, tl.getLineRight(i))
            }
            // A single line is laid out very wide and scrolled back into view (scrollX); and the
            // text never shows outside the view's content box.
            val px = v.totalPaddingLeft.toFloat() - v.scrollX
            val py = v.totalPaddingTop.toFloat() - v.scrollY
            val cl = v.totalPaddingLeft.toFloat()
            val cr = (v.width - v.totalPaddingRight).toFloat()
            val lo = (px + l).coerceIn(cl, cr)
            val hi = (px + r).coerceIn(cl, cr)
            if (hi <= lo) return
            add(lo, py + tl.getLineTop(0), hi, py + tl.getLineBottom(tl.lineCount - 1))
        } else if (v is android.widget.ImageView) {
            val dr = v.drawable ?: return
            val b = android.graphics.RectF(0f, 0f, dr.intrinsicWidth.toFloat(), dr.intrinsicHeight.toFloat())
            if (b.isEmpty) {
                add(0f, 0f, v.width.toFloat(), v.height.toFloat())
                return
            }
            v.imageMatrix.mapRect(b)
            b.offset(v.paddingLeft.toFloat(), v.paddingTop.toFloat())
            add(b.left, b.top, b.right, b.bottom)
        } else if (v.javaClass != View::class.java) {
            // A view that draws itself (the analog clock): its whole box.
            add(0f, 0f, v.width.toFloat(), v.height.toFloat())
        }
    }

    /** The face's weather rectangle on the previewed screen (rotation left out). */
    private fun emitWeather() {
        val cb = onWeather ?: return
        val r = weatherLocal
        val pl = depthPlace
        if (r == null || pl == null) {
            cb(null)
            return
        }
        cb(Rect(pl[0] + r.left * pl[2], pl[1] + r.top * pl[2], pl[0] + r.right * pl[2],
            pl[1] + r.bottom * pl[2]))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        place()
        if (design?.isDepthFace == false) post { emitContent() }
    }

    /** The Depth face's top left corner and scale on the previewed screen (screen px). */
    private fun place() {
        val d = design ?: return
        val g = geo ?: return
        walk(this) { v ->
            if (v !is DepthClockView) return@walk
            var x = 0f
            var y = 0f
            var c: View = v
            while (c !== this) {
                x += c.left + c.translationX
                y += c.top + c.translationY
                c = c.parent as? View ?: break
            }
            val fw = width.toFloat()
            val left = LockKeys.SIDE_DP * g.density + d.dx + fw / 2f + (x - fw / 2f) * d.scale
            val top = g.top0 + d.dy + y * d.scale
            // IchthysOS: and the turn (rotationZ about this frame's middle), for the clear glass,
            // which has to sit exactly over the picture.
            val px = LockKeys.SIDE_DP * g.density + d.dx + fw / 2f
            val py = g.top0 + d.dy + height * d.scale / 2f
            val old = v.previewPlacement
            if (old == null || old.size < 6 || old[0] != left || old[1] != top ||
                old[2] != d.scale || old[3] != d.rot || old[4] != px || old[5] != py) {
                v.previewPlacement = floatArrayOf(left, top, d.scale, d.rot, px, py)
            }
            val was = depthPlace
            if (was == null || was[0] != left || was[1] != top || was[2] != d.scale) {
                depthPlace = floatArrayOf(left, top, d.scale)
                if (weatherLocal != null) post { emitWeather() }
                if (depthContent != null) post { emitContent() }
            }
        }
    }

    private fun walk(v: View, action: (View) -> Unit) {
        action(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), action)
    }
}

/**
 * The selection frame: a white outline with round corner handles (the clock's resize handles).
 * IchthysOS polish: soft rounded corners, a faint shadow line so it reads on white pictures, and
 * handles with a dark rim, at [alpha].
 */
fun DrawScope.selectionFrame(r: Rect, handles: Boolean, density: Float, alpha: Float = 1f) {
    val pad = FRAME_PAD_DP * density
    val rr = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
    val corner = androidx.compose.ui.geometry.CornerRadius(10 * density)
    drawRoundRect(Color.Black.copy(alpha = 0.22f * alpha), rr.topLeft, rr.size, corner,
        style = Stroke(4.5f * density))
    drawRoundRect(Color.White.copy(alpha = alpha), rr.topLeft, rr.size, corner,
        style = Stroke(2f * density))
    if (handles) {
        for (p in listOf(rr.topLeft, rr.topRight, rr.bottomLeft, rr.bottomRight)) {
            drawCircle(Color.Black.copy(alpha = 0.25f * alpha), 10.5f * density, p)
            drawCircle(Color.White.copy(alpha = alpha), 8.5f * density, p)
        }
    }
}

/** Guard rails: the zones a clock should keep clear (status bar, fingerprint, shortcuts). */
fun DrawScope.guardRails(density: Float) {
    val rail = Color(0x55F6F1EA)
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
    drawRect(Color(0x22F6F1EA), size = Size(size.width, 40 * density))
    drawCircle(rail, 60 * density, Offset(size.width / 2f, size.height * 0.865f),
        style = Stroke(2f, pathEffect = dash))
    val r = 34 * density
    drawCircle(rail, r, Offset(r * 1.6f, size.height - r * 2.4f), style = Stroke(2f, pathEffect = dash))
    drawCircle(rail, r, Offset(size.width - r * 1.6f, size.height - r * 2.4f),
        style = Stroke(2f, pathEffect = dash))
}
