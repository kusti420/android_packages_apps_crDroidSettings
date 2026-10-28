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

import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.settings.R
import com.android.settingslib.ichthys.clock.DepthClockView
import com.android.settingslib.ichthys.clock.LockWeather
import com.android.settings.ichthys.guard.ui.theme.Guard
import com.android.settings.ichthys.guard.ui.theme.GuardTheme
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow

/**
 * IchthysOS lock screen editor v2 (kusti420/ichthysos-tracker#58), One UI style: a full-screen live
 * preview of the lock screen; tap the clock, the date line, the notification card or a shortcut
 * to select it (a frame with handles and its sheet docked at the bottom), drag to move, pinch or
 * pull a corner to resize, twist to turn. Ideas makes many complete lock screens out of a picture.
 * Nothing is written before Apply; Cancel (or back) leaves the lock screen as it was.
 *
 * Opened by org.ichthys.action.EDIT_LOCKSCREEN (SystemUI's lock screen long-press "customize"),
 * the Settings row and the clock picker. "wallpaper=koi" (from the system only) preselects the
 * painted depth wallpaper as the draft's wallpaper.
 */
class LockscreenEditor2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preset =
            if (savedInstanceState == null && launchedBySystem())
                intent.getStringExtra(EXTRA_WALLPAPER)?.takeIf { id ->
                    BUILTIN_WALLPAPERS.any { it.first == id }
                }
            else null
        // IchthysOS: "sheet=weather" (the Weather page's row) opens on the Weather sheet.
        val startSheet =
            if (savedInstanceState == null && intent.getStringExtra(EXTRA_SHEET) == "weather")
                EditorSheet.WEATHER
            else EditorSheet.NONE
        // IchthysOS polish: no window animation; the editor zooms out of (and back into) the lock
        // screen itself (LockEditor2's zoom), like a shared element.
        val fresh = savedInstanceState == null
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        setContent {
            // IchthysOS Shoal: the editor's chrome follows the theme's tokens (accent, surfaces,
            // fonts), as Guard's screens do.
            GuardTheme { LockEditor2(preset, startSheet, animateIn = fresh, onDone = { finish() }) }
        }
    }

    /** SystemUI, Settings itself, or adb (the activity is exported for SystemUI). */
    private fun launchedBySystem(): Boolean {
        val appId = UserHandle.getAppId(launchedFromUid)
        return appId == Process.SYSTEM_UID || appId == Process.SHELL_UID ||
            launchedFromPackage == "com.android.systemui"
    }

    companion object {
        const val EXTRA_WALLPAPER = "wallpaper"
        const val EXTRA_SHEET = "sheet"
    }
}

/** Which sheet is docked at the bottom. */
enum class EditorSheet {
    NONE, CLOCK, LAYOUT, DATE, WEATHER, NOTIFICATIONS, SHORTCUT_START, SHORTCUT_END, WALLPAPER, LOOKS
}

private fun sheetFor(e: LockElement): EditorSheet =
    when (e) {
        LockElement.CLOCK -> EditorSheet.CLOCK
        LockElement.DATE -> EditorSheet.DATE
        LockElement.WEATHER -> EditorSheet.WEATHER
        LockElement.NOTIFICATIONS -> EditorSheet.NOTIFICATIONS
        LockElement.SHORTCUT_START -> EditorSheet.SHORTCUT_START
        LockElement.SHORTCUT_END -> EditorSheet.SHORTCUT_END
    }

private fun elementFor(s: EditorSheet): LockElement? =
    when (s) {
        EditorSheet.CLOCK, EditorSheet.LAYOUT -> LockElement.CLOCK
        EditorSheet.DATE -> LockElement.DATE
        EditorSheet.WEATHER -> LockElement.WEATHER
        EditorSheet.NOTIFICATIONS -> LockElement.NOTIFICATIONS
        EditorSheet.SHORTCUT_START -> LockElement.SHORTCUT_START
        EditorSheet.SHORTCUT_END -> LockElement.SHORTCUT_END
        else -> null
    }

/** The editor's whole state that sheets change. */
class EditorState(
    val original: LockDesign,
    val draft: LockDesign,
    val wallpaper: WallpaperDraft,
    val picture: LockPicture,
    /** IchthysOS Living Sky: the minute the preview shows (null: now); never saved. */
    val skyMinute: Float? = null,
)

@Composable
fun LockEditor2(
    presetWallpaper: String?,
    startSheet: EditorSheet = EditorSheet.NONE,
    animateIn: Boolean = true,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val screen = rememberScreen()
    val geo = remember(screen) { LockGeometry(context, screen.width, screen.height) }
    val scope = rememberCoroutineScope()
    // IchthysOS: what the previews' weather shows until OmniJaws has some.
    remember {
        if (LockWeather.sample == null) {
            LockWeather.sample = LockWeather.Now(
                temp = "18", condition = context.getString(R.string.ichthys_ed2_weather_sample),
                code = 30, city = context.getString(R.string.ichthys_ed2_weather_sample_city),
                high = "21", low = "12", wind = "12 km/h", humidity = "64%",
                iconPack = "org.omnirom.omnijaws.tabler")
        }
        true
    }

    var original by remember { mutableStateOf(LockStore.read(context)) }
    var draft by remember { mutableStateOf(original) }
    var wallpaper by remember {
        mutableStateOf<WallpaperDraft>(
            presetWallpaper?.let { WallpaperDraft.Builtin(it) } ?: WallpaperDraft.Current)
    }
    var picture by remember { mutableStateOf(LockPicture.EMPTY) }
    // IchthysOS Living Sky: the preview's "preview the day" minute (null: now).
    var skyMinute by remember { mutableStateOf<Float?>(null) }
    var catalog by remember { mutableStateOf<LockShortcuts.Catalog?>(null) }
    var sheet by remember { mutableStateOf(startSheet) }
    var ideas by remember { mutableStateOf(false) }
    var ideaTitle by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    // Not Compose state: the preview reports it on every composition (hit tests read it).
    val layoutRef = remember { arrayOfNulls<LockLayout>(1) }
    // While a finger moves something: snap line, rails, the date's drop spots.
    var dragging by remember { mutableStateOf<LockElement?>(null) }
    var snapped by remember { mutableStateOf(false) }
    var dateFinger by remember { mutableStateOf<Offset?>(null) }
    // The weather's drop spot nearest the finger while it is dragged (a tick on each change).
    var weatherSpot by remember { mutableStateOf(-1) }

    // "wallpaper=koi": the painted wallpaper and depth, as a draft.
    LaunchedEffect(Unit) {
        if (presetWallpaper != null && draft.depth == DepthClockView.DEPTH_OFF) {
            draft = draft.copy(depth = DepthClockView.DEPTH_OVERLAP,
                style = if (draft.style == 0) DEPTH_STYLE else draft.style)
        }
        val cat = withContext(Dispatchers.IO) { LockShortcuts.load(context) }
        catalog = cat
        if (cat != null) {
            original = original.copy(shortcutStart = cat.start, shortcutEnd = cat.end)
            draft = draft.copy(shortcutStart = cat.start, shortcutEnd = cat.end)
        }
    }

    // IchthysOS Living Sky: a scrubbed time is only for while the Wallpaper sheet is open.
    LaunchedEffect(sheet) { if (sheet != EditorSheet.WALLPAPER) skyMinute = null }

    // The draft's wallpaper, off the main thread; again when IchthysDepth has made a mask.
    LaunchedEffect(wallpaper, reload) {
        val w = wallpaper
        // IchthysOS Living Sky: the Wallpaper sheet says whether the photo has a sky.
        LockPictures.wantSky = true
        picture =
            withContext(Dispatchers.IO) {
                when (w) {
                    is WallpaperDraft.Current -> LockPictures.current(context, screen.width, screen.height)
                    is WallpaperDraft.Photo ->
                        LockPictures.withSubject(context,
                            LockPictures.photo(context, w.uri, screen.width, screen.height),
                            screen.width, screen.height)
                    is WallpaperDraft.Builtin -> {
                        val res = BUILTIN_WALLPAPERS.first { it.first == w.id }.second
                        LockPictures.withSubject(context,
                            LockPictures.builtin(context, res, screen.width, screen.height),
                            screen.width, screen.height)
                    }
                }
            }
    }
    DisposableEffect(Unit) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    if (wallpaper is WallpaperDraft.Current) reload++
                }
            }
        try {
            context.contentResolver.registerContentObserver(
                Uri.parse("content://org.ichthys.depth/mask"), true, observer, UserHandle.USER_ALL)
            // IchthysOS Living Sky: and its sky.
            context.contentResolver.registerContentObserver(
                Uri.parse(com.android.settingslib.ichthys.sky.LivingSky.SKY_URI), true, observer,
                UserHandle.USER_ALL)
        } catch (e: Exception) {
            // IchthysDepth is missing: no cut-outs.
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    // IchthysOS polish: the editor opens out of the lock screen: the preview starts covering the
    // window (as the lock screen did) and settles into its place on a spring, the bars fading in
    // around it; leaving runs it back. 1 = covering the window, 0 = in its place.
    val zoom = remember { Animatable(if (animateIn) 1f else 0f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!animateIn) return@LaunchedEffect
        // The wallpaper first (it loads off the main thread), so it is there as it zooms.
        withTimeoutOrNull(600L) { snapshotFlow { picture !== LockPicture.EMPTY }.first { it } }
        zoom.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 230f))
    }
    /** Back into the lock screen, then [onDone]; [work] runs meanwhile (Apply's writes). */
    fun leave(work: (suspend () -> Unit)? = null) {
        if (leaving) return
        leaving = true
        scope.launch {
            val writes = work?.let { async(Dispatchers.IO) { it() } }
            sheet = EditorSheet.NONE
            zoom.animateTo(1f, spring(dampingRatio = 1f, stiffness = 320f))
            writes?.await()
            onDone()
        }
    }

    val changed = draft != original || wallpaper != WallpaperDraft.Current
    fun cancel() {
        if (changed) confirmDiscard = true else leave()
    }
    BackHandler {
        when {
            leaving -> Unit
            ideas -> ideas = false
            sheet != EditorSheet.NONE -> sheet = EditorSheet.NONE
            else -> cancel()
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.ichthys_ed2_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    leave()
                }) { Text(stringResource(R.string.ichthys_ed2_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.ichthys_ed2_keep_editing))
                }
            },
        )
    }

    // IchthysOS polish: Ideas comes up out of the editor and goes back into it (a short fade and
    // a slight scale), instead of a cut.
    AnimatedContent(
        targetState = ideas,
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 60)) +
                scaleIn(spring(dampingRatio = 0.9f, stiffness = 380f), initialScale = 0.96f)) togetherWith
                (fadeOut(tween(140)) + scaleOut(tween(200), targetScale = 0.98f))
        },
        label = "ideas",
    ) { showIdeas ->
        if (showIdeas) {
            IdeasScreen(
                base = draft,
                current = picture,
                currentIsWallpaper = wallpaper,
                onBack = { ideas = false },
                onChoose = { idea, pic, wp ->
                    draft = idea.design
                    picture = pic
                    if (wp != wallpaper) wallpaper = wp
                    ideaTitle = idea.title
                    ideas = false
                    sheet = EditorSheet.CLOCK
                },
            )
        } else {
            val tk = Guard.tokens
            val draftNow by rememberUpdatedState(draft)
            val sheetNow by rememberUpdatedState(sheet)
            val selected = elementFor(sheet)
            // IchthysOS polish: the sheet that is shown, kept while the sheet slides away (it used to go
            // blank as it left).
            val shownSheet = remember { arrayOf(EditorSheet.NONE) }
            if (sheet != EditorSheet.NONE) shownSheet[0] = sheet
            // A spring that puts the clock back inside its limits after a rubber-banded drag.
            val settle = remember { arrayOfNulls<Job>(1) }

            fun tick(constant: Int) {
                view.performHapticFeedback(constant)
            }

            /** Back inside the limits, on a spring, from wherever the fingers left it. */
            fun settleClock() {
                val from = draftNow
                val goal = from.copy(
                    dx = from.dx.coerceIn(-screen.width * MAX_DX, screen.width * MAX_DX),
                    dy = from.dy.coerceIn(-screen.height * MIN_DY, screen.height * MAX_DY),
                    scale = from.scale.coerceIn(MIN_SCALE, MAX_SCALE),
                )
                if (goal == from) return
                settle[0] = scope.launch {
                    val t = Animatable(0f)
                    t.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f)) {
                        val v = value
                        draft = draftNow.copy(
                            dx = from.dx + (goal.dx - from.dx) * v,
                            dy = from.dy + (goal.dy - from.dy) * v,
                            scale = from.scale + (goal.scale - from.scale) * v,
                        )
                    }
                    draft = draftNow.copy(dx = goal.dx, dy = goal.dy, scale = goal.scale)
                }
            }

            Column(
                Modifier.fillMaxSize().background(tk.ink).statusBarsPadding().navigationBarsPadding()
            ) {
                // Cancel · Ideas · Apply
                Row(
                    Modifier.fillMaxWidth()
                        .graphicsLayer {
                            alpha = 1f - zoom.value
                            translationY = -24 * density * zoom.value
                        }
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back (cancel) with the screen's name, in the theme's display face.
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { cancel() }
                            .heightIn(min = 44.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(Modifier.size(18.dp)) {
                            val u = size.width / 24f
                            val chevron = Path().apply {
                                moveTo(15 * u, 5 * u)
                                lineTo(8 * u, 12 * u)
                                lineTo(15 * u, 19 * u)
                            }
                            drawPath(chevron, tk.text, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round,
                                join = StrokeJoin.Round))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ichthys_editor_title), fontFamily = tk.display,
                            fontWeight = FontWeight.Bold, fontSize = 19.sp, color = tk.text, maxLines = 1)
                    }
                    Spacer(Modifier.weight(1f))
                    if (ideaTitle != null && sheet != EditorSheet.NONE) {
                        Text(stringResource(R.string.ichthys_ed2_idea_open, ideaTitle!!), fontFamily = tk.mono,
                            fontSize = 11.sp, color = tk.muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                    } else {
                        BarAction("✦ " + stringResource(R.string.ichthys_ed2_ideas), tk.outline, tk.text) {
                            ideas = true
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    BarAction(stringResource(R.string.apply), tk.accent, tk.accent) {
                        if (!leaving) {
                            val d = draft
                            val o = original
                            val w = wallpaper
                            leave { LockStore.apply(context, d, o, w) }
                        }
                    }
                }

                // The preview: everything on it can be tapped; the selected element can be moved.
                Box(
                    Modifier.fillMaxWidth().weight(1f).zIndex(1f)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    // IchthysOS polish: nothing of the preview shows before its wallpaper (the
                    // face alone, grey and unmasked, flashed for a frame on a cold start).
                    val pictureIn by animateFloatAsState(
                        if (picture !== LockPicture.EMPTY) 1f else 0f, tween(140),
                        label = "pictureIn")
                    LockScreenPreview(
                        draft,
                        picture,
                        Modifier.fillMaxSize().graphicsLayer { alpha = pictureIn },
                        shortcuts = catalog,
                        onLayout = { layoutRef[0] = it },
                        selected = selected,
                        zoom = { zoom.value },
                        skyMinute = skyMinute,
                        input = { k ->
                            Modifier.pointerInput(k) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (leaving || zoom.value > 0.02f) return@awaitEachGesture
                                    val lay = layoutRef[0] ?: return@awaitEachGesture
                                    settle[0]?.cancel()
                                    val p0 = down.position / k
                                    val d0 = draftNow
                                    val dens = geo.density
                                    // A corner handle of the selected clock resizes it (the corners of
                                    // the turned frame).
                                    val onHandle =
                                        (sheetNow == EditorSheet.CLOCK || sheetNow == EditorSheet.LAYOUT) &&
                                            lay.clockCorners(FRAME_PAD_DP * dens)
                                                .any { (it - p0).getDistance() < 28 * dens }
                                    val target = if (onHandle) LockElement.CLOCK else hit(lay, p0)
                                    val centre = lay.clockCentre
                                    val startDist = maxOf((p0 - centre).getDistance(), 1f)
                                    var moved = false
                                    var total = Offset.Zero
                                    var finger = p0
                                    // IchthysOS polish: what the fingers ask for, unclamped; what shows
                                    // rubber-bands past the limits and springs back on release.
                                    var rawDx = d0.dx
                                    var rawDy = d0.dy
                                    var rawScale = d0.scale
                                    var rawRot = d0.rot
                                    var centred = abs(d0.dx) < SNAP_DP * dens
                                    var rotSnap: Float? = ROT_SNAPS.firstOrNull { abs(d0.rot - it) < 0.5f }
                                    var beyond = false
                                    do {
                                        val ev = awaitPointerEvent()
                                        val pan = ev.calculatePan() / k
                                        val zoomBy = ev.calculateZoom()
                                        val turnBy = ev.calculateRotation()
                                        total += pan
                                        finger += pan
                                        if (!moved && (total.getDistance() > viewConfiguration.touchSlop / k ||
                                                ev.changes.size > 1)) {
                                            moved = target == LockElement.CLOCK || target == LockElement.DATE ||
                                                target == LockElement.WEATHER
                                            if (moved) {
                                                sheet = sheetFor(target!!)
                                                dragging = target
                                                tick(HapticFeedbackConstants.DRAG_START)
                                            }
                                        }
                                        if (moved) {
                                            val cur = draftNow
                                            if (target == LockElement.DATE) {
                                                dateFinger = finger
                                            } else if (target == LockElement.WEATHER) {
                                                dateFinger = finger
                                                // It jumps to the nearest place as the finger goes, with a
                                                // tick each time (the spots are the ones it started with).
                                                val spots = weatherSpots(d0, lay, geo)
                                                val spot = nearestWeatherSpot(spots, finger)
                                                if (spot != weatherSpot) {
                                                    if (weatherSpot != -1) {
                                                        tick(HapticFeedbackConstants.CLOCK_TICK)
                                                    }
                                                    weatherSpot = spot
                                                    if (spot >= 0 && spots[spot].first != cur.weatherPos) {
                                                        draft = cur.copy(weatherPos = spots[spot].first, look = "custom")
                                                    }
                                                }
                                            } else if (onHandle) {
                                                rawScale = d0.scale * (finger - centre).getDistance() / startDist
                                                val out = rawScale !in MIN_SCALE..MAX_SCALE
                                                if (out && !beyond) tick(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                                                beyond = out
                                                draft = cur.copy(
                                                    scale = rubber(rawScale, MIN_SCALE, MAX_SCALE, SCALE_BAND),
                                                    look = "custom")
                                            } else {
                                                rawDx += pan.x
                                                rawDy += pan.y
                                                rawScale *= zoomBy
                                                rawRot += turnBy
                                                val dxMax = screen.width * MAX_DX
                                                val dyMin = -screen.height * MIN_DY
                                                val dyMax = screen.height * MAX_DY
                                                var dx = rubber(rawDx, -dxMax, dxMax, screen.width * 0.08f)
                                                val dy = rubber(rawDy, dyMin, dyMax, screen.height * 0.04f)
                                                // Centre: a magnet with a tick on the way in.
                                                val nowCentred = abs(rawDx) < SNAP_DP * dens
                                                if (nowCentred) dx = 0f
                                                if (nowCentred && !centred) tick(HapticFeedbackConstants.SEGMENT_TICK)
                                                centred = nowCentred
                                                snapped = nowCentred
                                                var rot = rawRot
                                                while (rot > 180) rot -= 360
                                                while (rot < -180) rot += 360
                                                val snap = ROT_SNAPS.firstOrNull { abs(rot - it) < ROT_SNAP_DEG }
                                                if (snap != null) rot = snap
                                                if (snap != null && snap != rotSnap) tick(HapticFeedbackConstants.SEGMENT_TICK)
                                                rotSnap = snap
                                                val out = rawDx !in -dxMax..dxMax || rawDy !in dyMin..dyMax ||
                                                    rawScale !in MIN_SCALE..MAX_SCALE
                                                if (out && !beyond) tick(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                                                beyond = out
                                                draft = cur.copy(dx = dx, dy = dy, rot = rot,
                                                    scale = rubber(rawScale, MIN_SCALE, MAX_SCALE, SCALE_BAND),
                                                    look = "custom")
                                            }
                                            ev.changes.forEach { it.consume() }
                                        }
                                    } while (ev.changes.any { it.pressed })
                                    if (!moved) {
                                        // A tap selects (or, on empty space, deselects).
                                        sheet = target?.let { sheetFor(it) } ?: EditorSheet.NONE
                                    } else if (target == LockElement.DATE) {
                                        val dropped = dropDate(draftNow, lay, finger)
                                        if (dropped != draftNow) tick(HapticFeedbackConstants.CONTEXT_CLICK)
                                        draft = dropped
                                    } else if (target == LockElement.WEATHER) {
                                        val spots = weatherSpots(d0, lay, geo)
                                        val i = nearestWeatherSpot(spots, finger)
                                        if (i >= 0) {
                                            draft = draftNow.copy(weatherPos = spots[i].first, look = "custom")
                                            tick(HapticFeedbackConstants.CONTEXT_CLICK)
                                        }
                                    } else if (target == LockElement.CLOCK) {
                                        settleClock()
                                    }
                                    dragging = null
                                    snapped = false
                                    dateFinger = null
                                    weatherSpot = -1
                                }
                            }
                        },
                        overlay = { lay ->
                            val dens = geo.density
                            if (dragging != null) guardRails(dens)
                            if (snapped) {
                                drawLine(tk.accent, Offset(lay.clockCentreX, 0f),
                                    Offset(lay.clockCentreX, size.height), strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)))
                            }
                            if (dragging == LockElement.DATE) {
                                lay.rects[LockElement.CLOCK]?.let { c ->
                                    for (spot in dateSpots(c)) {
                                        drawCircle(tk.accent.copy(alpha = 0.6f), 9 * dens, spot)
                                    }
                                }
                                dateFinger?.let { drawCircle(tk.accent, 12 * dens, it) }
                            }
                            if (dragging == LockElement.WEATHER) {
                                weatherSpots(draftNow, lay, geo).forEachIndexed { i, (_, spot) ->
                                    val near = i == weatherSpot
                                    drawCircle(if (near) tk.accent else tk.accent.copy(alpha = 0.6f),
                                        (if (near) 13f else 9f) * dens, spot)
                                }
                                dateFinger?.let { drawCircle(tk.accent.copy(alpha = 0.4f), 18 * dens, it) }
                            }
                        },
                    )
                }

                // The tabs, and the docked sheet for the open one. IchthysOS polish: the sheet comes
                // and goes on a spring (the preview resizes along with it, no jump), and its content
                // changes in place with a short cross-fade.
                Column(Modifier.graphicsLayer { alpha = 1f - zoom.value }) {
                    EditorTabs(sheet) { sheet = it }
                    AnimatedVisibility(
                        visible = sheet != EditorSheet.NONE,
                        enter = expandVertically(spring(dampingRatio = 0.86f, stiffness = 420f),
                            expandFrom = Alignment.Top) + fadeIn(tween(160)),
                        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 520f),
                            shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
                    ) {
                        EditorSheetHost(
                            sheet = shownSheet[0],
                            state = EditorState(original, draft, wallpaper, picture, skyMinute),
                            catalog = catalog,
                            geo = geo,
                            onDraft = { draft = it },
                            onWallpaper = { wallpaper = it },
                            onSheet = { sheet = it },
                            onSkyMinute = { skyMinute = it },
                        )
                    }
                }
            }
        }
    }
}

/** A top bar action: an outlined mono caps button ([line] its outline, [ink] its text). */
@Composable
private fun BarAction(label: String, line: Color, ink: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(36.dp).clip(shape).border(1.dp, line, shape).clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), fontFamily = Guard.tokens.mono, fontSize = 12.sp, letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

/**
 * [v] inside [lo]..[hi]; past them it gives way less and less, up to [band] beyond (iOS-style
 * rubber band: the further you pull, the harder it gets).
 */
private fun rubber(v: Float, lo: Float, hi: Float, band: Float): Float =
    when {
        v < lo -> lo - band * (1f - 1f / ((lo - v) / band * 0.55f + 1f))
        v > hi -> hi + band * (1f - 1f / ((v - hi) / band * 0.55f + 1f))
        else -> v
    }

/** The clock's limits in the editor (as before; now with a rubber band past them). */
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 1.5f
private const val SCALE_BAND = 0.12f
private const val MAX_DX = 0.4f
private const val MIN_DY = 0.1f
private const val MAX_DY = 0.45f
/** The centre magnet's reach and the angles a turn snaps to. */
private const val SNAP_DP = 8f
private val ROT_SNAPS = listOf(-90f, 0f, 90f)
private const val ROT_SNAP_DEG = 4f

/** The element under [p] (screen px): shortcuts and notifications first, then date, then clock. */
private fun hit(lay: LockLayout, p: Offset): LockElement? {
    for (e in listOf(LockElement.SHORTCUT_START, LockElement.SHORTCUT_END,
            LockElement.NOTIFICATIONS, LockElement.WEATHER, LockElement.DATE, LockElement.CLOCK)) {
        val r = lay.rects[e] ?: continue
        // IchthysOS polish: the clock is hit in its own, turned frame.
        val q = if (e == LockElement.CLOCK) lay.unturn(p) else p
        if (r.inflate(12f).contains(q)) return e
    }
    return null
}

/** Six places the date line can go: above or below the clock, left, centre or right. */
private fun dateSpots(c: Rect): List<Offset> {
    val xs = listOf(c.left + c.width * 0.12f, c.center.x, c.right - c.width * 0.12f)
    return xs.map { Offset(it, c.top) } + xs.map { Offset(it, c.bottom) }
}

/**
 * IchthysOS: the places the weather can be dropped (its LockWeather.POS_* and the spot, screen
 * px): next to the date (the Depth face with its date shown), under the clock, the top corners.
 */
private fun weatherSpots(d: LockDesign, lay: LockLayout, geo: LockGeometry): List<Pair<Int, Offset>> {
    val c = lay.rects[LockElement.CLOCK] ?: return emptyList()
    val dens = geo.density
    val out = ArrayList<Pair<Int, Offset>>()
    if (d.isDepthFace && d.showDate) {
        lay.rects[LockElement.DATE]?.let { out.add(LockWeather.POS_DATE to Offset(it.right - it.width * 0.2f, it.center.y)) }
    }
    out.add(LockWeather.POS_UNDER to Offset(c.center.x, c.bottom + 16 * dens))
    val y = (LockKeys.STATUS_BAR_DP + 22) * dens
    out.add(LockWeather.POS_TOP_START to Offset(56 * dens, y))
    out.add(LockWeather.POS_TOP_END to Offset(geo.width - 56 * dens, y))
    return out
}

private fun nearestWeatherSpot(spots: List<Pair<Int, Offset>>, f: Offset): Int =
    spots.indices.minByOrNull { (spots[it].second - f).getDistance() } ?: -1

/** Where the date line goes when the finger lets go at [f]. */
private fun dropDate(d: LockDesign, lay: LockLayout, f: Offset): LockDesign {
    val c = lay.rects[LockElement.CLOCK] ?: return d
    val pos = if (f.y < c.center.y) DepthClockView.DATE_ABOVE else DepthClockView.DATE_BELOW
    val align =
        when {
            f.x < c.left + c.width / 3f -> DepthClockView.ALIGN_START
            f.x > c.right - c.width / 3f -> DepthClockView.ALIGN_END
            else -> DepthClockView.ALIGN_CENTRE
        }
    return d.copy(datePos = pos, dateAlign = align, look = "custom")
}
