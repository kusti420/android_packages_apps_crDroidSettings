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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.settings.R
import com.android.settings.ichthys.guard.ui.theme.Guard
import com.android.settings.ichthys.guard.ui.theme.GuardTheme
import com.android.settingslib.ichthys.clock.DepthClockView
import com.android.settingslib.ichthys.clock.LockNotifications
import com.android.settingslib.ichthys.clock.LockWeather
import com.android.settingslib.ichthys.clock.ProjectedClock
import com.android.settingslib.ichthys.clock.SurfaceFit
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * IchthysOS lock screen editor v2 (kusti420/ichthysos-tracker#58), the Shoal studio: the lock
 * screen itself fills the window, live, and everything on it is touched directly: drag the clock
 * (pinch to size, twist to turn), drag the notification card to its anchor, pull the projected
 * clock's surface by its handles (corners, the fold's edge, the curve's bows, the mesh's nine
 * points; they snap to the picture's own lines and to a rectangle). A tool rail at the bottom
 * (Wallpaper · Clock · Notifications · Shortcuts · Fingerprint · Looks) opens a sheet over the
 * lower third; the lock screen shrinks above it so what the sheet changes stays in sight. Every
 * change is undoable; Reset returns to the lock screen as it was; Looks keeps whole designs with
 * live thumbnails. Nothing is written before Apply; Cancel (or back) leaves the lock screen as
 * it was.
 *
 * Opened by org.ichthys.action.EDIT_LOCKSCREEN (SystemUI's lock screen long-press "customize"),
 * the Settings row and the clock picker. "wallpaper=koi" (from the system only) preselects the
 * painted depth wallpaper as the draft's wallpaper; "sheet=weather" opens on the weather.
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
        // IchthysOS: "sheet=weather" (the Weather page's row) opens on the Clock sheet's weather.
        val startWeather = savedInstanceState == null && intent.getStringExtra(EXTRA_SHEET) == "weather"
        // IchthysOS polish: no window animation; the editor zooms out of (and back into) the lock
        // screen itself (LockEditor2's zoom), like a shared element.
        val fresh = savedInstanceState == null
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        setContent {
            // IchthysOS Shoal: the editor's chrome follows the theme's tokens (accent, surfaces,
            // fonts), as Guard's screens do.
            GuardTheme {
                LockEditor2(preset, startWeather = startWeather, animateIn = fresh, onDone = { finish() })
            }
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

/** The tool rail's sheets. */
enum class EditorSheet { NONE, WALLPAPER, CLOCK, NOTIFICATIONS, SHORTCUTS, FINGERPRINT, LOOKS }

/** The Clock sheet's parts. */
enum class ClockTab { FACE, COLOUR, LAYOUT, DATE, WEATHER }

/** The editor's whole state that sheets change. */
class EditorState(
    val original: LockDesign,
    val draft: LockDesign,
    val wallpaper: WallpaperDraft,
    val picture: LockPicture,
    /** IchthysOS Living Sky: the minute the preview shows (null: now); never saved. */
    val skyMinute: Float? = null,
    /** IchthysOS projected clock: the picture's lines found by the auto-fit (handles snap to them). */
    val fitLines: List<SurfaceFit.Line> = emptyList(),
    val fitting: Boolean = false,
)

/**
 * Undo and redo of the draft. A change coalesces with the one before it when it comes within
 * [COALESCE_MS] (a slider's or a drag's many steps are one step back); the gestures end their own
 * step. New changes clear the redo.
 */
class EditHistory {
    private val undo = ArrayDeque<LockDesign>()
    private val redo = ArrayDeque<LockDesign>()
    private var lastPush = 0L

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** [before] was the draft before this change. */
    fun record(before: LockDesign, coalesce: Boolean = true) {
        val now = System.currentTimeMillis()
        redo.clear()
        if (coalesce && undo.isNotEmpty() && now - lastPush < COALESCE_MS) return
        undo.addLast(before)
        if (undo.size > MAX) undo.removeFirst()
        lastPush = now
    }

    /** Closes the running step: the next change starts a new one. */
    fun close() {
        lastPush = 0L
    }

    fun undo(current: LockDesign): LockDesign? {
        val d = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        lastPush = 0L
        return d
    }

    fun redo(current: LockDesign): LockDesign? {
        val d = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        lastPush = 0L
        return d
    }

    companion object {
        private const val COALESCE_MS = 700L
        private const val MAX = 60
    }
}

@Composable
fun LockEditor2(
    presetWallpaper: String?,
    startWeather: Boolean = false,
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
    val history = remember { EditHistory() }
    var historyTick by remember { mutableIntStateOf(0) }
    var wallpaper by remember {
        mutableStateOf<WallpaperDraft>(
            presetWallpaper?.let { WallpaperDraft.Builtin(it) } ?: WallpaperDraft.Current)
    }
    var picture by remember { mutableStateOf(LockPicture.EMPTY) }
    // IchthysOS Living Sky: the preview's "preview the day" minute (null: now).
    var skyMinute by remember { mutableStateOf<Float?>(null) }
    var catalog by remember { mutableStateOf<LockShortcuts.Catalog?>(null) }
    var sheet by remember { mutableStateOf(if (startWeather) EditorSheet.CLOCK else EditorSheet.NONE) }
    var clockTab by remember { mutableStateOf(if (startWeather) ClockTab.WEATHER else ClockTab.FACE) }
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
    // IchthysOS Quiet dock: the card's top while a finger drags its anchor (screen px).
    var cardDrag by remember { mutableStateOf<Float?>(null) }
    // IchthysOS projected clock: the handle a finger holds (see surfaceHit), the fit's lines.
    var surfaceHandle by remember { mutableStateOf(-1) }
    var fitLines by remember { mutableStateOf<List<SurfaceFit.Line>>(emptyList()) }
    var fitting by remember { mutableStateOf(false) }

    /** The one way the draft changes: every change is a step back. */
    fun edit(n: LockDesign, coalesce: Boolean = true) {
        if (n == draft) return
        history.record(draft, coalesce)
        draft = n
        historyTick++
    }

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
        fitLines = emptyList()
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

    /** IchthysOS projected clock: the picture's lines for the surface, proposed and kept for snapping. */
    fun autoFit() {
        val src = picture.source ?: return
        if (fitting) return
        fitting = true
        scope.launch {
            val proposal = withContext(Dispatchers.IO) {
                try {
                    SurfaceFit.fit(src, picture.sky, picture.cutoutMask, screen.width, screen.height)
                } catch (e: Exception) {
                    null
                }
            }
            fitting = false
            if (proposal != null) {
                fitLines = proposal.lines
                edit(draft.copy(surface = proposal.surface, surfaceKind = proposal.surface.kind,
                    look = "custom"), coalesce = false)
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
        }
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

    // Any edit names the look "custom"; an edit undone by hand leaves nothing to discard.
    val changed = draft.copy(look = original.look) != original || wallpaper != WallpaperDraft.Current
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
                    edit(idea.design, coalesce = false)
                    picture = pic
                    if (wp != wallpaper) wallpaper = wp
                    ideaTitle = idea.title
                    ideas = false
                    sheet = EditorSheet.CLOCK
                    clockTab = ClockTab.FACE
                },
            )
        } else {
            val tk = Guard.tokens
            val draftNow by rememberUpdatedState(draft)
            val sheetNow by rememberUpdatedState(sheet)
            // What is framed: the element being moved, else the open sheet's.
            val selected = dragging ?: elementFor(sheet, clockTab, draft)
            // IchthysOS polish: the sheet that is shown, kept while the sheet slides away (it used
            // to go blank as it left).
            val shownSheet = remember { arrayOf(EditorSheet.NONE) }
            if (sheet != EditorSheet.NONE) shownSheet[0] = sheet
            // A spring that puts the clock back inside its limits after a rubber-banded drag.
            val settle = remember { arrayOfNulls<Job>(1) }

            fun tick(constant: Int) {
                view.performHapticFeedback(constant)
            }

            /**
             * IchthysOS: how far the clock may go sideways and still be all on the screen (8 dp
             * in), from its width as drawn (turned and sized); a fixed share of the screen let a
             * wide clock hang off the edge.
             */
            fun dxLimit(): Float {
                val c = layoutRef[0]?.clockCorners(0f).orEmpty()
                if (c.isEmpty()) return screen.width * MAX_DX
                val w = c.maxOf { it.x } - c.minOf { it.x }
                return maxOf(0f, (screen.width - w) / 2f - 8 * geo.density)
            }

            /** Back inside the limits, on a spring, from wherever the fingers left it. */
            fun settleClock() {
                val from = draftNow
                val goal = from.copy(
                    dx = from.dx.coerceIn(-dxLimit(), dxLimit()),
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

            BoxWithConstraints(Modifier.fillMaxSize().background(tk.ink)) {
                val density = geo.density
                val winH = constraints.maxHeight.toFloat()
                val winW = constraints.maxWidth.toFloat()
                // The sheet takes the lower third; the lock screen shrinks into the room above it
                // (a layer, not a relayout: the gestures' scale stays as it is).
                val sheetH = winH * SHEET_SHARE
                val railH = RAIL_DP * density
                val open by animateFloatAsState(if (sheet != EditorSheet.NONE) 1f else 0f,
                    spring(dampingRatio = 0.9f, stiffness = 380f), label = "sheetOpen")
                val fitScale = ((winH - sheetH - railH - 12 * density) / winH).coerceIn(0.4f, 1f)
                val previewScale = 1f + (fitScale - 1f) * open
                // The preview, full window; its own zoom opens it out of the lock screen.
                val pictureIn by animateFloatAsState(
                    if (picture !== LockPicture.EMPTY) 1f else 0f, tween(140), label = "pictureIn")
                LockScreenPreview(
                    draft,
                    picture,
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            alpha = pictureIn
                            scaleX = previewScale
                            scaleY = previewScale
                            // Shrunk about the top, a touch down for the floating bar.
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationY = (TOP_BAR_DP + 8) * density * open
                        },
                    shortcuts = catalog,
                    onLayout = { layoutRef[0] = it },
                    selected = selected,
                    zoom = { zoom.value },
                    skyMinute = skyMinute,
                    draggedCardTop = cardDrag,
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
                                // IchthysOS projected clock: a finger on a surface handle or inside
                                // the surface moves the surface, not the face (surfaceGesture).
                                val surf = lay.surface
                                val handle = if (surf != null) surfaceHit(lay, p0, dens) else null
                                if (surf != null && handle != null) {
                                    surfaceHandle = handle
                                    val moved =
                                        surfaceGesture(down, k, lay, screen, dens, fitLines, { draftNow },
                                            { draft = it }, { dragging = LockElement.CLOCK }, ::tick)
                                    if (moved) {
                                        // One step back for the whole drag.
                                        history.record(d0, coalesce = false)
                                        historyTick++
                                    } else {
                                        sheet = EditorSheet.CLOCK
                                        clockTab = ClockTab.LAYOUT
                                    }
                                    surfaceHandle = -1
                                    dragging = null
                                    return@awaitEachGesture
                                }
                                // A corner handle of the selected clock resizes it (the corners of
                                // the turned frame).
                                val onHandle =
                                    sheetNow == EditorSheet.CLOCK &&
                                        lay.clockCorners(FRAME_PAD_DP * dens)
                                            .any { (it - p0).getDistance() < 28 * dens }
                                var target = if (onHandle) LockElement.CLOCK else hit(lay, p0)
                                // IchthysOS Quiet dock: the dock is anchored to the sensor; only the
                                // cards can be dragged to an anchor.
                                val cardDraggable = target == LockElement.NOTIFICATIONS && !d0.isDock
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
                                var cardTop0 = lay.rects[LockElement.NOTIFICATIONS]?.top ?: 0f
                                var cardAuto = false
                                do {
                                    val ev = awaitPointerEvent()
                                    val pan = ev.calculatePan() / k
                                    val zoomBy = ev.calculateZoom()
                                    val turnBy = ev.calculateRotation()
                                    total += pan
                                    finger += pan
                                    if (!moved && (total.getDistance() > viewConfiguration.touchSlop / k ||
                                            ev.changes.size > 1)) {
                                        // IchthysOS: two fingers anywhere pinch and turn the clock
                                        // (they no longer have to start on it).
                                        if (ev.changes.size > 1 && target != LockElement.DATE &&
                                            target != LockElement.WEATHER && !cardDraggable) {
                                            target = LockElement.CLOCK
                                        }
                                        moved = target == LockElement.CLOCK || target == LockElement.DATE ||
                                            target == LockElement.WEATHER || cardDraggable
                                        if (moved) {
                                            // IchthysOS: the sheet stays as it is while a finger
                                            // moves something (switching it rescaled the preview
                                            // under the finger).
                                            dragging = target
                                            tick(HapticFeedbackConstants.DRAG_START)
                                        }
                                    }
                                    if (moved) {
                                        val cur = draftNow
                                        if (cardDraggable) {
                                            // The card's anchor: between under the clock and the
                                            // keep-out; it magnets back to Auto near its own place.
                                            val lo = minOf(lay.cardsMinTop, lay.cardsMaxTop)
                                            val hi = maxOf(lay.cardsMinTop, lay.cardsMaxTop)
                                            val autoTop = lay.cardsMinTop + LockNotifications.CARDS_GAP_DP * dens / 2f
                                            var t = (cardTop0 + total.y).coerceIn(lo, hi)
                                            val nowAuto = abs(t - autoTop) < SNAP_DP * dens
                                            if (nowAuto) t = autoTop
                                            if (nowAuto != cardAuto) tick(HapticFeedbackConstants.SEGMENT_TICK)
                                            cardAuto = nowAuto
                                            cardDrag = t
                                        } else if (target == LockElement.DATE) {
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
                                            val dxMax = dxLimit()
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
                                    // A tap on something opens its sheet; on empty space it
                                    // leaves the sheet as it is.
                                    target?.let { e ->
                                        sheet = sheetFor(e)
                                        clockTabFor(e)?.let { clockTab = it }
                                    }
                                } else {
                                    // One step back for the whole gesture.
                                    history.record(d0, coalesce = false)
                                    historyTick++
                                    if (cardDraggable) {
                                        val t = cardDrag
                                        if (t != null) {
                                            val autoTop = lay.cardsMinTop + LockNotifications.CARDS_GAP_DP * dens / 2f
                                            val anchor =
                                                if (abs(t - autoTop) < SNAP_DP * dens) LockNotifications.ANCHOR_AUTO
                                                else LockNotifications.clampAnchor((t / screen.height * 100f).roundToInt())
                                            draft = draftNow.copy(notifAnchor = anchor, look = "custom")
                                            tick(HapticFeedbackConstants.CONTEXT_CLICK)
                                        }
                                        cardDrag = null
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
                        val drag = dragging
                        if (drag != null) {
                            // The rails light up when the moved thing reaches a zone.
                            val r = lay.rects[drag]
                            val z = lay.zone
                            val hot = r != null && z != null &&
                                (r.bottom > z.upperBottom || r.top < LockKeys.STATUS_BAR_DP * dens)
                            guardRails(lay, dens, hot)
                        }
                        // IchthysOS projected clock: the surface, its handles and the picture's
                        // lines (snapping) while the clock is selected.
                        if (lay.surface != null && (selected == LockElement.CLOCK || drag == LockElement.CLOCK)) {
                            if (sheetNow == EditorSheet.CLOCK && clockTab == ClockTab.LAYOUT) {
                                fitLines.forEach { l ->
                                    drawLine(SNAP_LINE, Offset(l.x0 * size.width, l.y0 * size.height),
                                        Offset(l.x1 * size.width, l.y1 * size.height), 1.5f * dens,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                                }
                            }
                            surfaceHandles(lay, dens, tk.accent, drag == LockElement.CLOCK, surfaceHandle)
                        }
                        if (snapped) {
                            drawLine(tk.accent, Offset(lay.clockCentreX, 0f),
                                Offset(lay.clockCentreX, size.height), strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)))
                        }
                        // IchthysOS Quiet dock: the card's anchor while it is dragged, as a line
                        // across with its percent.
                        val ct = cardDrag
                        if (ct != null) {
                            drawLine(tk.accent, Offset(0f, ct), Offset(size.width, ct), 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)))
                            val autoTop = lay.cardsMinTop + LockNotifications.CARDS_GAP_DP * dens / 2f
                            drawLine(Color(0x88F6F1EA), Offset(0f, autoTop), Offset(size.width, autoTop), 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                        }
                        if (drag == LockElement.DATE) {
                            lay.rects[LockElement.CLOCK]?.let { c ->
                                for (spot in dateSpots(c)) {
                                    drawCircle(tk.accent.copy(alpha = 0.6f), 9 * dens, spot)
                                }
                            }
                            dateFinger?.let { drawCircle(tk.accent, 12 * dens, it) }
                        }
                        if (drag == LockElement.WEATHER) {
                            weatherSpots(draftNow, lay, geo).forEachIndexed { i, (_, spot) ->
                                val near = i == weatherSpot
                                drawCircle(if (near) tk.accent else tk.accent.copy(alpha = 0.6f),
                                    (if (near) 13f else 9f) * dens, spot)
                            }
                            dateFinger?.let { drawCircle(tk.accent.copy(alpha = 0.4f), 18 * dens, it) }
                        }
                    },
                )

                // Legibility scrims for the floating bars, over the lock screen's edges.
                Box(
                    Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
                        .graphicsLayer { alpha = 1f - zoom.value }
                        .background(Brush.verticalGradient(listOf(tk.ink.copy(alpha = 0.55f), Color.Transparent)))
                )
                Box(
                    Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = (1f - zoom.value) * (1f - open) }
                        .background(Brush.verticalGradient(listOf(Color.Transparent, tk.ink.copy(alpha = 0.6f))))
                )

                // The floating top bar: back · undo · redo · reset · ideas · apply.
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().align(Alignment.TopCenter)
                        .graphicsLayer {
                            alpha = 1f - zoom.value
                            translationY = -24 * density * zoom.value
                        }
                        .height(TOP_BAR_DP.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back (cancel) with the screen's name, in the theme's display face.
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(tk.ink.copy(alpha = 0.55f))
                            .clickable { cancel() }.heightIn(min = 40.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(Modifier.size(16.dp)) {
                            val u = size.width / 24f
                            val chevron = Path().apply {
                                moveTo(15 * u, 5 * u)
                                lineTo(8 * u, 12 * u)
                                lineTo(15 * u, 19 * u)
                            }
                            drawPath(chevron, tk.text, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round,
                                join = StrokeJoin.Round))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ichthys_editor_title), fontFamily = tk.display,
                            fontWeight = FontWeight.Bold, fontSize = 17.sp, color = tk.text, maxLines = 1)
                    }
                    Spacer(Modifier.weight(1f))
                    historyTick
                    IconPill(stringResource(R.string.ichthys_ed2_undo), history.canUndo, tk,
                        onClick = { history.undo(draft)?.let { draft = it; historyTick++ } }) { ink ->
                        arrowGlyph(ink, left = true)
                    }
                    Spacer(Modifier.width(6.dp))
                    IconPill(stringResource(R.string.ichthys_ed2_redo), history.canRedo, tk,
                        onClick = { history.redo(draft)?.let { draft = it; historyTick++ } }) { ink ->
                        arrowGlyph(ink, left = false)
                    }
                    Spacer(Modifier.width(6.dp))
                    if (ideaTitle != null && sheet != EditorSheet.NONE) {
                        Text(stringResource(R.string.ichthys_ed2_idea_open, ideaTitle!!), fontFamily = tk.mono,
                            fontSize = 11.sp, color = tk.muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
                    } else {
                        BarAction("✦ " + stringResource(R.string.ichthys_ed2_ideas), tk.outline, tk.text,
                            tk.ink.copy(alpha = 0.55f)) { ideas = true }
                    }
                    Spacer(Modifier.width(6.dp))
                    BarAction(stringResource(R.string.apply), tk.accent, tk.onAccent, tk.accent) {
                        if (!leaving) {
                            val d = draft
                            val o = original
                            val w = wallpaper
                            leave { LockStore.apply(context, d, o, w) }
                        }
                    }
                }

                // The sheet over the lower third and the tool rail under it.
                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding()
                        .graphicsLayer { alpha = 1f - zoom.value }
                ) {
                    AnimatedVisibility(
                        visible = sheet != EditorSheet.NONE,
                        enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it } +
                            fadeIn(tween(160)),
                        exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 520f)) { it } +
                            fadeOut(tween(120)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(with(androidx.compose.ui.platform.LocalDensity.current) {
                                sheetH.toDp()
                            }).padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(tk.ink.copy(alpha = 0.94f))
                                .border(tk.hairlineWidth, tk.hairline, RoundedCornerShape(22.dp))
                        ) {
                            EditorSheetHost(
                                sheet = shownSheet[0],
                                clockTab = clockTab,
                                state = EditorState(original, draft, wallpaper, picture, skyMinute, fitLines, fitting),
                                catalog = catalog,
                                geo = geo,
                                onDraft = { edit(it) },
                                onCommit = { history.close() },
                                onWallpaper = { wallpaper = it },
                                onClockTab = { clockTab = it },
                                onSkyMinute = { skyMinute = it },
                                onAutoFit = { autoFit() },
                                onReset = {
                                    edit(original.copy(shortcutStart = draft.shortcutStart,
                                        shortcutEnd = draft.shortcutEnd), coalesce = false)
                                    tick(HapticFeedbackConstants.CONFIRM)
                                },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    ToolRail(sheet) { s ->
                        sheet = if (s == sheet) EditorSheet.NONE else s
                    }
                }
            }
        }
    }
}

/** The tool rail: six chips, the open one filled with the accent; tapping it again closes the sheet. */
@Composable
private fun ToolRail(open: EditorSheet, onSheet: (EditorSheet) -> Unit) {
    val tk = Guard.tokens
    val tools = listOf(
        EditorSheet.WALLPAPER to R.string.ichthys_ed2_wallpaper,
        EditorSheet.CLOCK to R.string.ichthys_ed2_clock,
        EditorSheet.NOTIFICATIONS to R.string.ichthys_ed2_notifications,
        EditorSheet.SHORTCUTS to R.string.ichthys_ed2_shortcuts,
        EditorSheet.FINGERPRINT to R.string.ichthys_ed2_fingerprint,
        EditorSheet.LOOKS to R.string.ichthys_ed2_looks,
    )
    Row(
        Modifier.fillMaxWidth().height(RAIL_DP.dp).horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tools.forEach { (s, label) ->
            val on = s == open
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.height(42.dp).clip(shape)
                    .background(if (on) tk.accent else tk.ink.copy(alpha = 0.62f))
                    .border(1.dp, if (on) tk.accent else tk.hairline, shape)
                    .clickable { onSheet(s) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                val ink = if (on) tk.onAccent else tk.text
                Canvas(Modifier.size(16.dp)) { toolGlyph(s, ink) }
                Text(stringResource(label).uppercase(), fontFamily = tk.mono, fontSize = 10.sp,
                    letterSpacing = 0.9.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
            }
        }
    }
}

/** Small line glyphs for the rail: a picture, a clock, a dock row, two discs, a print, a star. */
private fun DrawScope.toolGlyph(s: EditorSheet, ink: Color) {
    val u = size.width / 24f
    val st = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (s) {
        EditorSheet.WALLPAPER -> {
            drawRoundRect(ink, Offset(3 * u, 4 * u), androidx.compose.ui.geometry.Size(18 * u, 16 * u),
                androidx.compose.ui.geometry.CornerRadius(3 * u), style = st)
            drawPath(Path().apply { moveTo(5 * u, 17 * u); lineTo(10 * u, 11 * u); lineTo(14 * u, 15 * u); lineTo(19 * u, 10 * u) },
                ink, style = st)
        }
        EditorSheet.CLOCK -> {
            drawCircle(ink, 8.5f * u, center, style = st)
            drawPath(Path().apply { moveTo(12 * u, 7 * u); lineTo(12 * u, 12.5f * u); lineTo(15.5f * u, 14.5f * u) }, ink, style = st)
        }
        EditorSheet.NOTIFICATIONS -> {
            for (i in 0..2) drawCircle(ink, 2.2f * u, Offset((6 + 6 * i) * u, 9 * u))
            drawLine(ink, Offset(5 * u, 15 * u), Offset(19 * u, 15 * u), 1.4f * u, StrokeCap.Round)
            drawLine(ink, Offset(7 * u, 19 * u), Offset(17 * u, 19 * u), 1.4f * u, StrokeCap.Round)
        }
        EditorSheet.SHORTCUTS -> {
            drawCircle(ink, 4.2f * u, Offset(7 * u, 15 * u), style = st)
            drawCircle(ink, 4.2f * u, Offset(17 * u, 15 * u), style = st)
        }
        EditorSheet.FINGERPRINT -> {
            fingerprintGlyph(1, center, 9 * u, ink, u * 1.1f)
        }
        EditorSheet.LOOKS -> {
            val p = Path()
            for (i in 0 until 10) {
                val a = Math.toRadians(-90.0 + 36.0 * i)
                val r = if (i % 2 == 0) 9 * u else 4 * u
                val x = 12 * u + r * Math.cos(a).toFloat()
                val y = 12.5f * u + r * Math.sin(a).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, ink, style = st)
        }
        EditorSheet.NONE -> Unit
    }
}

/** A round pill with one glyph (undo, redo). */
@Composable
private fun IconPill(
    description: String,
    enabled: Boolean,
    tk: com.android.settings.ichthys.guard.ui.theme.GuardTokens,
    onClick: () -> Unit,
    glyph: DrawScope.(Color) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(40.dp).clip(shape).background(tk.ink.copy(alpha = 0.55f))
            .border(1.dp, tk.hairline, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (enabled) tk.text else tk.muted.copy(alpha = 0.5f)
        Canvas(Modifier.size(18.dp)) { glyph(ink) }
    }
}

/** A curved arrow, back ([left]) or forward. */
private fun DrawScope.arrowGlyph(ink: Color, left: Boolean) {
    val u = size.width / 24f
    val st = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val dir = if (left) 1f else -1f
    val x0 = if (left) 5f else 19f
    drawPath(Path().apply {
        moveTo(x0 * u, 10 * u)
        cubicTo((x0 + dir * 4) * u, 6 * u, (x0 + dir * 12) * u, 6 * u, (x0 + dir * 14) * u, 13 * u)
        cubicTo((x0 + dir * 15) * u, 17 * u, (x0 + dir * 10) * u, 19 * u, (x0 + dir * 7) * u, 18 * u)
    }, ink, style = st)
    drawPath(Path().apply {
        moveTo(x0 * u, 5 * u)
        lineTo(x0 * u, 10.5f * u)
        lineTo((x0 + dir * 5.5f) * u, 10.5f * u)
    }, ink, style = st)
}

/** A top bar action: an outlined mono caps pill ([line] its outline, [ink] its text, [fill] behind). */
@Composable
private fun BarAction(label: String, line: Color, ink: Color, fill: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.height(40.dp).clip(shape).background(fill).border(1.dp, line, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), fontFamily = Guard.tokens.mono, fontSize = 12.sp, letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

/** The share of the window the sheet takes (the lower third), the rail and the top bar. */
private const val SHEET_SHARE = 0.36f
private const val RAIL_DP = 58
private const val TOP_BAR_DP = 56
/** The picture's lines the surface's handles snap to, as drawn. */
private val SNAP_LINE = Color(0xCCFFD66B)

private fun sheetFor(e: LockElement): EditorSheet =
    when (e) {
        LockElement.CLOCK, LockElement.DATE, LockElement.WEATHER -> EditorSheet.CLOCK
        LockElement.NOTIFICATIONS -> EditorSheet.NOTIFICATIONS
        LockElement.SHORTCUT_START, LockElement.SHORTCUT_END -> EditorSheet.SHORTCUTS
        LockElement.FINGERPRINT -> EditorSheet.FINGERPRINT
    }

private fun clockTabFor(e: LockElement): ClockTab? =
    when (e) {
        LockElement.CLOCK -> ClockTab.LAYOUT
        LockElement.DATE -> ClockTab.DATE
        LockElement.WEATHER -> ClockTab.WEATHER
        else -> null
    }

private fun elementFor(s: EditorSheet, tab: ClockTab, d: LockDesign): LockElement? =
    when (s) {
        EditorSheet.CLOCK ->
            when (tab) {
                ClockTab.DATE -> if (d.isDepthFace && d.showDate) LockElement.DATE else LockElement.CLOCK
                ClockTab.WEATHER -> if (d.weather) LockElement.WEATHER else LockElement.CLOCK
                else -> LockElement.CLOCK
            }
        EditorSheet.NOTIFICATIONS -> if (d.showNotifications) LockElement.NOTIFICATIONS else null
        EditorSheet.FINGERPRINT -> LockElement.FINGERPRINT
        else -> null
    }

// ---- the projected clock's surface --------------------------------------------------------------

/** Surface handles: a point's index, [BOW_TOP] / [BOW_BOTTOM] (curve), [INSIDE] (move), or null. */
private const val BOW_TOP = 100
private const val BOW_BOTTOM = 101
private const val INSIDE = 200

/**
 * IchthysOS projected clock: what a finger at [p] (screen px) takes hold of on the surface: one of
 * its points (within 26 dp), a curve's bow handle, the inside of the surface (the whole thing
 * moves), or nothing.
 */
private fun surfaceHit(lay: LockLayout, p: Offset, dens: Float): Int? {
    val pts = lay.surfacePoints()
    var best: Int? = null
    var bestD = 26 * dens
    pts.forEachIndexed { i, c ->
        val d = (c - p).getDistance()
        if (d < bestD) { bestD = d; best = i }
    }
    lay.bowHandles().forEachIndexed { i, c ->
        val d = (c - p).getDistance()
        if (d < bestD) { bestD = d; best = if (i == 0) BOW_TOP else BOW_BOTTOM }
    }
    if (best != null) return best
    val m = lay.mapper ?: return null
    val outline = m.outline()
    val bounds = android.graphics.RectF()
    outline.computeBounds(bounds, true)
    if (!bounds.contains(p.x, p.y)) return null
    val region = android.graphics.Region()
    region.setPath(outline, android.graphics.Region(bounds.left.toInt() - 1, bounds.top.toInt() - 1,
        bounds.right.toInt() + 1, bounds.bottom.toInt() + 1))
    return if (region.contains(p.x.toInt(), p.y.toInt())) INSIDE else null
}

/**
 * IchthysOS projected clock: the gesture on the surface. A point handle drags that point (snapping
 * to the picture's lines found by the auto-fit, and a plane to an upright rectangle, with a tick);
 * a bow handle bows the curve's edge; a finger inside moves the whole surface; two fingers scale it
 * about its middle. Points are kept in ProjectedClock's range and the surface valid (an invalid
 * shape is simply not taken). Returns whether anything moved (a tap doesn't).
 */
private suspend fun AwaitPointerEventScope.surfaceGesture(
    down: PointerInputChange,
    k: Float,
    lay: LockLayout,
    screen: IntSize,
    dens: Float,
    lines: List<SurfaceFit.Line>,
    current: () -> LockDesign,
    update: (LockDesign) -> Unit,
    onDrag: () -> Unit,
    tick: (Int) -> Unit,
): Boolean {
    val p0 = down.position / k
    val handle = surfaceHit(lay, p0, dens) ?: return false
    val start = lay.surface ?: return false
    var raw = start
    val snapTol = 10 * dens / screen.width
    var moved = false
    var total = Offset.Zero
    var snapped = false
    val w = screen.width.toFloat()
    val h = screen.height.toFloat()
    // Raw point (unsnapped) for a point handle.
    var rx = if (handle < 100) start.points[handle * 2] else 0f
    var ry = if (handle < 100) start.points[handle * 2 + 1] else 0f
    do {
        val ev = awaitPointerEvent()
        val pan = ev.calculatePan() / k
        val zoom = ev.calculateZoom()
        total += pan
        if (!moved && (total.getDistance() > viewConfiguration.touchSlop / k || ev.changes.size > 1)) {
            moved = true
            onDrag()
            tick(HapticFeedbackConstants.DRAG_START)
        }
        if (moved) {
            val fx = pan.x / w
            val fy = pan.y / h
            var next: ProjectedClock.Surface
            var nowSnapped = false
            if (handle < 100 && ev.changes.size == 1) {
                rx += fx
                ry += fy
                var x = rx
                var y = ry
                // The picture's lines first, then (a plane) the rectangle.
                SurfaceFit.snap(x, y, lines, snapTol * 1.4f)?.let { s ->
                    x = s[0]; y = s[1]; nowSnapped = true
                }
                next = raw.withPoint(handle, x, y)
                if (next.kind == ProjectedClock.KIND_PLANE) {
                    ProjectedClock.snapRectangle(next.points, snapTol)?.let { q ->
                        next = ProjectedClock.Surface(ProjectedClock.KIND_PLANE, q)
                        nowSnapped = true
                    }
                }
            } else if (handle == BOW_TOP || handle == BOW_BOTTOM) {
                // The bow: the finger's travel along the edge's outward normal, in shares of the
                // mean height (the curve's apex moves by bow x mean height).
                val pts = raw.points
                val top = handle == BOW_TOP
                val ax = if (top) pts[0] else pts[6]
                val ay = if (top) pts[1] else pts[7]
                val bx = if (top) pts[2] else pts[4]
                val by = if (top) pts[3] else pts[5]
                val ox = if (top) (pts[6] + pts[4]) / 2f else (pts[0] + pts[2]) / 2f
                val oy = if (top) (pts[7] + pts[5]) / 2f else (pts[1] + pts[3]) / 2f
                val ex = (bx - ax) * w
                val ey = (by - ay) * h
                val len = maxOf(1f, hypot(ex, ey))
                var nx = ey / len
                var ny = -ex / len
                val mx = (ax + bx) / 2f * w
                val my = (ay + by) / 2f * h
                if (nx * (mx - ox * w) + ny * (my - oy * h) < 0f) { nx = -nx; ny = -ny }
                val meanH = raw.meanHeightPx(screen.width, screen.height)
                val delta = (pan.x * nx + pan.y * ny) / meanH
                val i = if (top) 0 else 1
                var bow = raw.bend[i] + delta
                if (abs(bow) < 0.03f) { bow = 0f; nowSnapped = true }
                next = raw.withBend(i, bow)
            } else {
                next = raw.translated(fx, fy)
                if (zoom != 1f) next = next.scaled(zoom)
                // A point handle held with a second finger scales too.
                if (handle < 100) { rx += fx; ry += fy }
            }
            if ((nowSnapped) != snapped) tick(HapticFeedbackConstants.SEGMENT_TICK)
            snapped = nowSnapped
            if (next.isValid()) {
                raw = next
                update(current().copy(surface = next, look = "custom"))
            }
            ev.changes.forEach { it.consume() }
        }
    } while (ev.changes.any { it.pressed })
    return moved
}

/**
 * IchthysOS projected clock: the surface's outline, a fold's edge, a mesh's grid, the curve's bow
 * handles (diamonds) and the point handles (discs; the held one in the accent), screen px.
 */
private fun DrawScope.surfaceHandles(lay: LockLayout, dens: Float, accent: Color, active: Boolean, held: Int) {
    val m = lay.mapper ?: return
    val s = lay.surface ?: return
    val outline = m.outline().asComposePath()
    drawPath(outline, Color.Black.copy(alpha = 0.22f), style = Stroke(4.5f * dens))
    drawPath(outline, if (active) accent else Color.White, style = Stroke(2f * dens))
    m.foldEdge()?.let { f ->
        drawPath(f.asComposePath(), Color.White.copy(alpha = 0.9f), style = Stroke(1.5f * dens,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * dens, 6f * dens))))
    }
    if (s.kind == ProjectedClock.KIND_MESH) {
        // The inner grid lines, through the middle points.
        val pt = FloatArray(2)
        for (line in 0..1) {
            val p = Path()
            for (i in 0..16) {
                val t = i / 16f
                if (line == 0) m.map(0.5f, t, pt) else m.map(t, 0.5f, pt)
                if (i == 0) p.moveTo(pt[0], pt[1]) else p.lineTo(pt[0], pt[1])
            }
            drawPath(p, Color.White.copy(alpha = 0.55f), style = Stroke(1f * dens,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * dens, 6f * dens))))
        }
    }
    lay.bowHandles().forEachIndexed { i, c ->
        val on = held == (if (i == 0) BOW_TOP else BOW_BOTTOM)
        val r = 9f * dens
        val diamond = Path().apply {
            moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
        }
        drawPath(diamond, Color.Black.copy(alpha = 0.25f), style = Stroke(3f * dens))
        drawPath(diamond, if (on) accent else Color.White)
    }
    lay.surfacePoints().forEachIndexed { i, c ->
        val on = held == i
        drawCircle(Color.Black.copy(alpha = 0.25f), 10.5f * dens, c)
        drawCircle(Color.White, 8.5f * dens, c)
        drawCircle(accent, if (on) 5.5f * dens else 3f * dens, c)
    }
}

private fun rubber(v: Float, lo: Float, hi: Float, band: Float): Float =
    when {
        v < lo -> lo - band * (1f - 1f / ((lo - v) / band * 0.55f + 1f))
        v > hi -> hi + band * (1f - 1f / ((v - hi) / band * 0.55f + 1f))
        else -> v
    }

/** The clock's limits in the editor (as before; now with a rubber band past them). */
private const val MIN_SCALE = 0.4f
private const val MAX_SCALE = 1.8f
private const val SCALE_BAND = 0.12f
// IchthysOS: room to put the clock where the owner wants it (down to just above the
// notifications, out to the sides); it used to stop short of where people wanted it.
private const val MAX_DX = 0.5f
private const val MIN_DY = 0.12f
private const val MAX_DY = 0.55f
/** The centre magnet's reach and the angles a turn snaps to. */
private const val SNAP_DP = 8f
private val ROT_SNAPS = listOf(-90f, 0f, 90f)
private const val ROT_SNAP_DEG = 4f

/** The element under [p] (screen px): shortcuts, fingerprint and notifications first, then date, then clock. */
private fun hit(lay: LockLayout, p: Offset): LockElement? {
    for (e in listOf(LockElement.SHORTCUT_START, LockElement.SHORTCUT_END, LockElement.FINGERPRINT,
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
