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

package com.crdroid.settings.preferences

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.content.theming.ThemeStyle
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import com.android.settingslib.ichthys.charging.ChargingAnimationSettings
import com.android.settingslib.ichthys.charging.ChargingStylePainter
import com.android.settingslib.ichthys.clock.LockWeather
import com.android.systemui.monet.ColorScheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import org.json.JSONObject

/**
 * IchthysOS (tracker#59): list settings choose from pictures instead of words. When a list row
 * whose key is known here is tapped, the plain radio-button dialog is replaced by a grid of
 * drawn previews, one per option, each showing what that option looks like (the moving ones,
 * like fingerprint effects and tile animations, play on a loop). Choosing one goes through the
 * row's change listener exactly as the stock dialog does.
 *
 * Hooked from SettingsPreferenceFragment.onDisplayPreferenceDialog, so any page gets it by key.
 */
object OptionPreviews {

    /** Draws option [value] into [b]; [t] runs 0..1 over the loop for animated previews. */
    private fun interface Painter {
        fun paint(c: Canvas, b: RectF, value: String, t: Float, p: Pal)
    }

    private class Spec(
        val columns: Int,
        /** Preview height / width. */
        val aspect: Float,
        val loopMs: Long,
        /**
         * IchthysOS: the dialog stays open after a pick (the selection moves), so options can be
         * tried one after the other; closed with its button.
         */
        val keepOpen: Boolean = false,
        /** IchthysOS: also run when an option is picked (e.g. a live preview of it). */
        val onPick: ((Context, String) -> Unit)? = null,
        val painter: Painter,
    )

    /** Colours the previews draw with, from the current theme. */
    private class Pal(val ctx: Context) {
        val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val accent = attr(ctx, android.R.attr.colorAccent) or 0xFF000000.toInt()
        val fg = attr(ctx, android.R.attr.textColorPrimary) or 0xFF000000.toInt()
        val dim = alpha(fg, 0.55f)
        val faint = alpha(fg, 0.16f)
        /** A phone screen. */
        val screen = ctx.getColor(
            if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50)
        /** A panel or card on that screen. */
        val panel = ctx.getColor(
            if (dark) android.R.color.system_neutral1_700 else android.R.color.system_neutral1_200)
        /** The lock screen / AOD is dark whatever the theme. */
        val night = 0xFF0B1418.toInt()
        val wall1 = ctx.getColor(android.R.color.system_accent1_700)
        val wall2 = ctx.getColor(android.R.color.system_accent3_400)
        val green = 0xFF3DDC84.toInt()
        val red = 0xFFFF4D4D.toInt()
        /**
         * IchthysOS: the seed the system palette really uses when the colour source is "preset"
         * (a custom accent, Abyss, …): the system_palette colour of the theme JSON. Null when the
         * palette follows the wallpaper.
         */
        val presetSeed: Int? = try {
            val o = JSONObject(Settings.Secure.getString(ctx.contentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES) ?: "{}")
            val palette = o.optString("android.theme.customization.system_palette")
            if (o.optString("android.theme.customization.color_source") == "preset" &&
                palette.isNotEmpty()) {
                Color.parseColor(if (palette.startsWith("#")) palette else "#$palette")
            } else null
        } catch (e: Exception) {
            null
        }
        val seedColors: WallpaperColors? = if (presetSeed != null) null else try {
            WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        } catch (e: RuntimeException) {
            null
        }

        private fun attr(ctx: Context, id: Int): Int {
            val tv = TypedValue()
            ctx.theme.resolveAttribute(id, tv, true)
            return if (tv.resourceId != 0) ctx.getColor(tv.resourceId) else tv.data
        }
    }

    private val SPECS: Map<String, Spec> by lazy { buildSpecs() }

    /** Shows the picture dialog for [pref]; false when the row has none (use the stock one). */
    @JvmStatic
    fun show(pref: Preference): Boolean {
        if (pref !is ListPreference) return false
        val spec = SPECS[pref.key] ?: return false
        val values = pref.entryValues ?: return false
        val labels = pref.entries ?: return false
        if (values.isEmpty() || values.size != labels.size) return false
        val ctx = pref.context
        val pal = Pal(ctx)
        val dp = ctx.resources.displayMetrics.density

        val list = RecyclerView(ctx)
        list.layoutManager = GridLayoutManager(ctx, spec.columns)
        list.setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), 0)
        list.clipToPadding = false
        var dialog: AlertDialog? = null
        lateinit var adapter: Adapter
        adapter = Adapter(spec, pal, values.map { it.toString() },
            labels.map { it.toString() }, pref.value) { value ->
            if (pref.callChangeListener(value)) pref.value = value
            spec.onPick?.invoke(ctx, value)
            if (spec.keepOpen) {
                adapter.selected = pref.value
                adapter.notifyDataSetChanged()
            } else {
                dialog?.dismiss()
            }
        }
        list.adapter = adapter
        val frame = FrameLayout(ctx)
        frame.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        val builder = AlertDialog.Builder(ctx)
            .setTitle(pref.dialogTitle ?: pref.title)
            .setView(frame)
        if (spec.keepOpen) {
            builder.setPositiveButton(android.R.string.ok, null)
        } else {
            builder.setNegativeButton(android.R.string.cancel, null)
        }
        dialog = builder.create()
        dialog.show()
        // Keep long lists scrolling inside the dialog instead of pushing the buttons off.
        list.post {
            val maxH = (ctx.resources.displayMetrics.heightPixels * 0.62f).toInt()
            if (list.height > maxH) {
                list.layoutParams = list.layoutParams.apply { height = maxH }
            }
        }
        return true
    }

    private class Adapter(
        val spec: Spec,
        val pal: Pal,
        val values: List<String>,
        val labels: List<String>,
        var selected: String?,
        val onPick: (String) -> Unit,
    ) : RecyclerView.Adapter<Adapter.Holder>() {

        class Holder(val root: LinearLayout, val preview: PreviewView, val label: TextView) :
            RecyclerView.ViewHolder(root)

        override fun getItemCount() = values.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val dp = ctx.resources.displayMetrics.density
            val root = LinearLayout(ctx)
            root.orientation = LinearLayout.VERTICAL
            root.setPadding((5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt(), (8 * dp).toInt())
            val preview = PreviewView(ctx, spec, pal)
            root.addView(preview, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val label = TextView(ctx)
            label.gravity = Gravity.CENTER_HORIZONTAL
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            label.maxLines = 2
            label.setPadding(0, (6 * dp).toInt(), 0, 0)
            root.addView(label, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return Holder(root, preview, label)
        }

        override fun onBindViewHolder(h: Holder, position: Int) {
            val value = values[position]
            val on = value == selected
            h.preview.bind(value, on)
            h.label.text = labels[position]
            h.label.setTextColor(if (on) pal.accent else pal.fg)
            h.label.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            h.root.contentDescription = labels[position]
            h.root.setOnClickListener { onPick(value) }
        }
    }

    /** One option's picture, in a rounded card; outlined in the accent when it is the current one. */
    private class PreviewView(ctx: Context, val spec: Spec, val pal: Pal) : View(ctx) {
        private val dp = ctx.resources.displayMetrics.density
        private var value = ""
        private val bounds = RectF()
        private val clip = Path()
        private val card = GradientDrawable()

        init {
            card.cornerRadius = 14 * dp
            background = card
            clipToOutline = true
        }

        fun bind(v: String, selected: Boolean) {
            value = v
            card.setColor(alpha(pal.fg, if (pal.dark) 0.07f else 0.05f))
            card.setStroke((if (selected) 2.5f else 1f).let { (it * dp).toInt() },
                if (selected) pal.accent else pal.faint)
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(w, (w * spec.aspect).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val inset = 7 * dp
            bounds.set(inset, inset, width - inset, height - inset)
            clip.reset()
            clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 14 * dp, 14 * dp,
                Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            val t = if (spec.loopMs > 0) {
                (SystemClock.uptimeMillis() % spec.loopMs) / spec.loopMs.toFloat()
            } else 0f
            try {
                spec.painter.paint(canvas, bounds, value, t, pal)
            } catch (e: RuntimeException) {
                // A preview must never take the settings page down.
            }
            canvas.restore()
            if (spec.loopMs > 0 && isAttachedToWindow) postInvalidateOnAnimation()
        }
    }

    // ---------------------------------------------------------------- drawing helpers

    private fun alpha(color: Int, a: Float): Int {
        val v = (Color.alpha(color) * a.coerceIn(0f, 1f)).toInt()
        return (color and 0x00FFFFFF) or (v shl 24)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** 0..1 over [from, to] of the loop, clamped. */
    private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

    private fun ease(x: Float) = (1 - cos(x * PI.toFloat())) / 2

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tmp = RectF()
    private val path = Path()
    private val matrix = Matrix()

    private fun fillPaint(color: Int): Paint {
        fill.shader = null
        fill.maskFilter = null
        fill.colorFilter = null
        fill.color = color
        return fill
    }

    private fun strokePaint(color: Int, width: Float): Paint {
        stroke.shader = null
        stroke.pathEffect = null
        stroke.maskFilter = null
        stroke.color = color
        stroke.strokeWidth = width
        return stroke
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, paint: Paint) {
        tmp.set(l, t, r, b)
        c.drawRoundRect(tmp, rad, rad, paint)
    }

    /** A portrait phone centred in [b]; returns its screen rectangle. */
    private fun phone(c: Canvas, b: RectF, p: Pal, screen: Int = p.screen): RectF {
        val h = b.height()
        val w = min(b.width(), h * 0.52f)
        val l = b.centerX() - w / 2
        val r = RectF(l, b.top, l + w, b.bottom)
        rr(c, r.left, r.top, r.right, r.bottom, w * 0.14f, fillPaint(screen))
        rr(c, r.left, r.top, r.right, r.bottom, w * 0.14f, strokePaint(p.faint, w * 0.025f))
        return RectF(r.left + w * 0.04f, r.top + w * 0.04f, r.right - w * 0.04f,
            r.bottom - w * 0.04f)
    }

    /** The top strip of a phone across [b] (status bar previews); returns the bar's rectangle. */
    private fun statusStrip(c: Canvas, b: RectF, p: Pal): RectF {
        val top = b.top + b.height() * 0.18f
        rr(c, b.left - b.width() * 0.1f, top, b.right + b.width() * 0.1f, b.bottom + 40f,
            b.width() * 0.12f, fillPaint(p.screen))
        rr(c, b.left - b.width() * 0.1f, top, b.right + b.width() * 0.1f, b.bottom + 40f,
            b.width() * 0.12f, strokePaint(p.faint, 2f))
        return RectF(b.left + b.width() * 0.05f, top + b.height() * 0.1f,
            b.right - b.width() * 0.05f, top + b.height() * 0.32f)
    }

    private fun clockText(c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int,
            align: Paint.Align = Paint.Align.CENTER) {
        text.textAlign = align
        text.textSize = size
        text.typeface = Typeface.DEFAULT_BOLD
        text.color = color
        c.drawText(s, x, cy + size * 0.36f, text)
        text.textAlign = Paint.Align.CENTER
    }

    /** Wi-Fi + signal glyphs ending at [right]; returns where they start. */
    private fun sysIcons(c: Canvas, right: Float, cy: Float, h: Float, color: Int): Float {
        val u = h / 10f
        // signal bars
        var x = right
        for (i in 3 downTo 0) {
            val bh = u * (3 + i * 2)
            rr(c, x - u * 1.4f, cy + h / 2 - bh, x, cy + h / 2, u * 0.4f, fillPaint(color))
            x -= u * 2.2f
        }
        // wifi wedge
        x -= u * 2
        path.reset()
        path.moveTo(x - u * 4, cy + h / 2 - u * 6.5f)
        path.quadTo(x - u * 8.5f, cy - h / 2 + u * 2f, x - u * 13f, cy + h / 2 - u * 6.5f)
        path.lineTo(x - u * 8.5f, cy + h / 2)
        path.close()
        c.drawPath(path, fillPaint(color))
        return x - u * 13f
    }

    private fun battery(c: Canvas, right: Float, cy: Float, h: Float, color: Int): Float {
        val w = h * 1.9f
        rr(c, right - w, cy - h * 0.38f, right - h * 0.12f, cy + h * 0.38f, h * 0.18f,
            strokePaint(color, h * 0.1f))
        rr(c, right - w + h * 0.14f, cy - h * 0.24f, right - w + (w - h * 0.4f) * 0.64f,
            cy + h * 0.24f, h * 0.08f, fillPaint(color))
        rr(c, right - h * 0.1f, cy - h * 0.14f, right, cy + h * 0.14f, h * 0.05f, fillPaint(color))
        return right - w
    }

    /** A blocky stand-in for a tile glyph. */
    private fun glyph(c: Canvas, cx: Float, cy: Float, s: Float, kind: Int, color: Int) {
        when (kind % 4) {
            0 -> { // wifi
                val pnt = strokePaint(color, s * 0.16f)
                for (i in 1..3) {
                    val r = s * 0.17f * i
                    tmp.set(cx - r, cy + s * 0.3f - r, cx + r, cy + s * 0.3f + r)
                    c.drawArc(tmp, 225f, 90f, false, pnt)
                }
            }
            1 -> { // torch
                rr(c, cx - s * 0.16f, cy - s * 0.1f, cx + s * 0.16f, cy + s * 0.42f, s * 0.06f,
                    fillPaint(color))
                path.reset()
                path.moveTo(cx - s * 0.3f, cy - s * 0.42f)
                path.lineTo(cx + s * 0.3f, cy - s * 0.42f)
                path.lineTo(cx + s * 0.16f, cy - s * 0.12f)
                path.lineTo(cx - s * 0.16f, cy - s * 0.12f)
                path.close()
                c.drawPath(path, fillPaint(color))
            }
            2 -> { // moon
                path.reset()
                path.addCircle(cx, cy, s * 0.36f, Path.Direction.CW)
                val bite = Path()
                bite.addCircle(cx + s * 0.2f, cy - s * 0.14f, s * 0.3f, Path.Direction.CW)
                path.op(bite, Path.Op.DIFFERENCE)
                c.drawPath(path, fillPaint(color))
            }
            else -> { // bluetooth-ish
                val pnt = strokePaint(color, s * 0.13f)
                c.drawLine(cx, cy - s * 0.42f, cx, cy + s * 0.42f, pnt)
                c.drawLine(cx, cy - s * 0.42f, cx + s * 0.24f, cy - s * 0.2f, pnt)
                c.drawLine(cx + s * 0.24f, cy - s * 0.2f, cx - s * 0.24f, cy + s * 0.2f, pnt)
                c.drawLine(cx, cy + s * 0.42f, cx + s * 0.24f, cy + s * 0.2f, pnt)
                c.drawLine(cx + s * 0.24f, cy + s * 0.2f, cx - s * 0.24f, cy - s * 0.2f, pnt)
            }
        }
    }

    /**
     * IchthysOS: the Monet engine page carries ThemeStyle's own names (TONAL_SPOT, …); Theme
     * Studio's theme_style row carries the ichthys.nix ones (tonalSpot, fruitSalad, monochrome,
     * …). Both resolve here, so every palette previews as itself.
     */
    private fun themeStyleOf(v: String): Int = try {
        ThemeStyle.valueOf(v)
    } catch (e: RuntimeException) {
        when (v) {
            "vibrant" -> ThemeStyle.VIBRANT
            "expressive" -> ThemeStyle.EXPRESSIVE
            "spritz" -> ThemeStyle.SPRITZ
            "rainbow" -> ThemeStyle.RAINBOW
            "fruitSalad" -> ThemeStyle.FRUIT_SALAD
            "monochrome" -> ThemeStyle.MONOCHROMATIC
            else -> ThemeStyle.TONAL_SPOT
        }
    }

    /** Superellipse ("squircle") in [r]. */
    private fun squircle(r: RectF, out: Path) {
        out.reset()
        val cx = r.centerX()
        val cy = r.centerY()
        val a = r.width() / 2
        val bb = r.height() / 2
        val n = 64
        for (i in 0..n) {
            val th = i * 2 * PI / n
            val ct = cos(th)
            val st = sin(th)
            val x = cx + a * Math.signum(ct) * Math.pow(abs(ct), 0.5).toFloat()
            val y = cy + bb * Math.signum(st) * Math.pow(abs(st), 0.5).toFloat()
            if (i == 0) out.moveTo(x.toFloat(), y.toFloat()) else out.lineTo(x.toFloat(), y.toFloat())
        }
        out.close()
    }

    private fun textLines(c: Canvas, l: Float, top: Float, w: Float, n: Int, gap: Float,
            color: Int) {
        for (i in 0 until n) {
            val lw = w * (if (i == n - 1) 0.55f else 0.9f)
            rr(c, l, top + i * gap, l + lw, top + i * gap + gap * 0.42f, gap * 0.21f,
                fillPaint(color))
        }
    }

    /** Lock-screen-like wallpaper: a soft gradient from the theme's colours. */
    private fun wallpaper(c: Canvas, r: RectF, p: Pal) {
        fill.shader = LinearGradient(r.left, r.top, r.right, r.bottom, p.wall1, p.wall2,
            Shader.TileMode.CLAMP)
        c.drawRect(r, fill)
        fill.shader = null
    }

    // ---------------------------------------------------------------- the settings

    /**
     * IchthysOS: the charging animation picker. Each card is a small dark phone screen playing
     * its style on a loop, drawn by SettingsLib's ChargingStylePainter (the same code SystemUI
     * plays full screen) at a 360 x 630 dp virtual screen scaled into the card. "Ripple" (the
     * stock surfaceeffects ripple, which lives in SystemUI) gets a drawn stand-in. Tapping a card
     * selects it and has SystemUI play it once for real; the dialog stays open for the next one.
     */
    private fun chargingSpec(): Spec {
        val loopMs = 2900L
        val painters = HashMap<String, ChargingStylePainter>()
        var level = -1
        var forPal: Pal? = null
        return Spec(3, 1.75f, loopMs, keepOpen = true,
            onPick = { ctx, v -> ChargingAnimationSettings.sendPreview(ctx, v) }) { c, b, v, t, p ->
            // A new dialog (new context and theme): set the painters up again.
            if (p !== forPal) {
                forPal = p
                painters.clear()
                level = -1
            }
            val dp = p.ctx.resources.displayMetrics.density
            val r = 10 * dp
            // The screen: the lock screen's night ground with a faint wallpaper wash.
            tmp.set(b)
            c.save()
            clipRound.reset()
            clipRound.addRoundRect(tmp, r, r, Path.Direction.CW)
            c.clipPath(clipRound)
            c.drawColor(p.night)
            val wash = fillPaint(alpha(p.wall1, 0.55f))
            wash.shader = LinearGradient(0f, b.top, 0f, b.bottom, alpha(p.wall1, 0.5f),
                alpha(p.wall2, 0.25f), Shader.TileMode.CLAMP)
            c.drawRect(b, wash)
            clockText(c, "12:30", b.centerX(), b.top + b.height() * 0.2f, b.height() * 0.11f,
                alpha(Color.WHITE, 0.8f))

            val ms = t * loopMs
            val vw = 360 * dp
            val vh = 630 * dp
            val scale = b.width() / vw
            when (v) {
                ChargingAnimationSettings.OFF -> {
                    val s = b.width() * 0.22f
                    val paint = strokePaint(alpha(Color.WHITE, 0.45f), s * 0.08f)
                    c.drawCircle(b.centerX(), b.centerY(), s, paint)
                    c.drawLine(b.centerX() - s * 0.7f, b.centerY() + s * 0.7f,
                        b.centerX() + s * 0.7f, b.centerY() - s * 0.7f, paint)
                }
                ChargingAnimationSettings.RIPPLE -> {
                    // A soft accent ripple from the port, like the stock one.
                    val k = (ms / 1500f).coerceIn(0f, 1f)
                    if (k < 1f) {
                        val rad = b.height() * 1.1f * ease(k)
                        val g = fillPaint(p.accent)
                        g.shader = RadialGradient(b.centerX(), b.bottom, max(rad, 1f),
                            intArrayOf(alpha(p.accent, 0f), alpha(p.accent, 0.55f * (1 - k)),
                                alpha(p.accent, 0f)), floatArrayOf(0f, 0.85f, 1f),
                            Shader.TileMode.CLAMP)
                        c.drawCircle(b.centerX(), b.bottom, max(rad, 1f), g)
                    }
                }
                else -> {
                    if (level < 0) {
                        level = try {
                            p.ctx.getSystemService(android.os.BatteryManager::class.java)
                                ?.getIntProperty(
                                    android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 64
                        } catch (e: Exception) {
                            64
                        }.let { if (it in 0..100) it else 64 }
                    }
                    val painter = painters.getOrPut(v) {
                        ChargingStylePainter(p.ctx).also {
                            it.configure(v, level, vw / 2f, vh, vw.toInt(), vh.toInt(),
                                IntArray(4) { (28 * dp).toInt() })
                        }
                    }
                    if (ms < painter.durationMs) {
                        c.translate(b.left, b.top)
                        c.scale(scale, scale)
                        painter.draw(c, ms)
                    }
                }
            }
            c.restore()
        }
    }

    private val clipRound = Path()

    private fun buildSpecs(): Map<String, Spec> {
        val m = HashMap<String, Spec>()

        // ---- Theme

        m["theme_studio_surface"] = Spec(2, 0.85f, 0) { c, b, v, _, p ->
            val gap = b.height() / 4.2f
            for (i in 0 until 3) {
                val top = b.top + gap * 0.4f + i * gap * 1.25f
                tmp.set(b.left + b.width() * 0.06f, top, b.right - b.width() * 0.06f, top + gap)
                val rad = gap * 0.32f
                if (v == "tonal") {
                    c.drawRoundRect(tmp, rad, rad, fillPaint(alpha(p.accent, 0.2f)))
                } else if (v == "stock") {
                    // IchthysOS: plain Monet surfaces: quiet neutral fills, no outline.
                    c.drawRoundRect(tmp, rad, rad, fillPaint(alpha(p.fg, 0.1f)))
                } else {
                    c.drawRoundRect(tmp, rad, rad, strokePaint(alpha(p.fg, 0.4f), gap * 0.035f))
                }
                c.drawCircle(tmp.left + gap * 0.5f, tmp.centerY(), gap * 0.2f,
                    fillPaint(if (v == "tonal") p.accent else p.dim))
                textLines(c, tmp.left + gap, tmp.centerY() - gap * 0.2f, tmp.width() * 0.6f, 2,
                    gap * 0.24f, p.dim)
            }
        }

        m["theme_studio_radius"] = Spec(3, 0.85f, 0) { c, b, v, _, p ->
            val dp = (v.toFloatOrNull() ?: 22f)
            val w = b.width() * 0.78f
            val h = b.height() * 0.72f
            val l = b.centerX() - w / 2
            val top = b.centerY() - h / 2
            // a card about 140 dp tall drawn at this size
            val rad = min(dp * h / 140f * 1.6f, h / 2)
            rr(c, l, top, l + w, top + h, rad, fillPaint(alpha(p.accent, 0.18f)))
            rr(c, l, top, l + w, top + h, rad, strokePaint(p.accent, h * 0.025f))
            textLines(c, l + w * 0.18f, top + h * 0.3f, w * 0.64f, 3, h * 0.15f, p.dim)
        }

        m["theme_studio_shape"] = Spec(3, 0.85f, 0) { c, b, v, _, p ->
            val s = min(b.width(), b.height())
            fun shape(r: RectF, paint: Paint) {
                when (v) {
                    "pill" -> c.drawRoundRect(r, r.height() / 2, r.height() / 2, paint)
                    "squircle" -> { squircle(r, path); c.drawPath(path, paint) }
                    else -> c.drawRoundRect(r, r.height() * 0.24f, r.height() * 0.24f, paint)
                }
            }
            val tile = RectF(b.centerX() - s * 0.32f, b.top + s * 0.06f, b.centerX() + s * 0.32f,
                b.top + s * 0.46f)
            shape(tile, fillPaint(alpha(p.accent, 0.85f)))
            glyph(c, tile.left + tile.height() * 0.5f, tile.centerY(), tile.height() * 0.5f, 0,
                p.screen)
            val btn = RectF(b.centerX() - s * 0.4f, b.top + s * 0.58f, b.centerX() + s * 0.4f,
                b.top + s * 0.84f)
            shape(btn, strokePaint(p.accent, s * 0.025f))
            rr(c, btn.centerX() - btn.width() * 0.25f, btn.centerY() - s * 0.02f,
                btn.centerX() + btn.width() * 0.25f, btn.centerY() + s * 0.02f, s * 0.02f,
                fillPaint(p.accent))
        }

        m["theme_studio_icon_style"] = Spec(2, 0.85f, 0) { c, b, v, _, p ->
            val badges = intArrayOf(0xFF4C8DF6.toInt(), 0xFFF2994A.toInt(), 0xFF8E6CEF.toInt(),
                0xFF3DBE8B.toInt())
            val cw = b.width() / 2
            val ch = b.height() / 2
            for (i in 0 until 4) {
                val l = b.left + (i % 2) * cw + cw * 0.08f
                val top = b.top + (i / 2) * ch + ch * 0.12f
                rr(c, l, top, l + cw * 0.84f, top + ch * 0.76f, ch * 0.3f,
                    fillPaint(alpha(p.fg, 0.08f)))
                val gx = l + ch * 0.38f
                val gy = top + ch * 0.38f
                if (v == "colour") {
                    c.drawCircle(gx, gy, ch * 0.24f, fillPaint(badges[i]))
                    glyph(c, gx, gy, ch * 0.3f, i, Color.WHITE)
                } else {
                    glyph(c, gx, gy, ch * 0.32f, i, p.fg)
                }
                textLines(c, gx + ch * 0.34f, gy - ch * 0.1f, cw * 0.32f, 2, ch * 0.13f, p.dim)
            }
        }

        m["ichthys_screen_reveal_style"] = Spec(3, 1.25f, 2000) { c, b, v, t, p ->
            val s = phone(c, b, p, 0xFF000000.toInt())
            val k = ease(seg(t, 0.1f, 0.6f))
            c.save()
            path.reset()
            val w = s.width()
            val h = s.height()
            when (v) {
                "2" -> path.addOval(s.centerX() - w * 0.75f * k, s.top - h * 0.2f,
                    s.centerX() + w * 0.75f * k, s.bottom + h * 0.2f, Path.Direction.CW)
                "3" -> path.addOval(s.left - w * 0.2f, s.centerY() - h * 0.75f * k,
                    s.right + w * 0.2f, s.centerY() + h * 0.75f * k, Path.Direction.CW)
                "4" -> path.addCircle(s.centerX(), s.centerY(), hypot(w, h) * 0.55f * k,
                    Path.Direction.CW)
                "5" -> path.addOval(s.centerX() - w * 1.1f * k, s.top - h * 1.15f * k,
                    s.centerX() + w * 1.1f * k, s.top + h * 1.15f * k, Path.Direction.CW)
                // IchthysOS: "default" keeps the stock per-trigger effect; the usual one is the
                // power button's (PowerButtonReveal), an oval growing from the button's edge.
                "0" -> path.addOval(s.right + w * 0.05f - w * 1.25f * k, s.top + h * 0.3f - h * k,
                    s.right + w * 0.05f + w * 1.25f * k, s.top + h * 0.3f + h * k,
                    Path.Direction.CW)
                else -> path.addOval(s.centerX() - w * 1.2f * k, s.bottom - h * 1.6f * k,
                    s.centerX() + w * 1.2f * k, s.bottom + h * 0.6f * k, Path.Direction.CW)
            }
            c.clipPath(path)
            wallpaper(c, s, p)
            clockText(c, "12:30", s.centerX(), s.top + h * 0.28f, w * 0.3f, Color.WHITE)
            rr(c, s.left + w * 0.1f, s.top + h * 0.55f, s.right - w * 0.1f, s.top + h * 0.68f,
                w * 0.06f, fillPaint(alpha(Color.WHITE, 0.25f)))
            c.restore()
        }

        val monet = Spec(3, 0.8f, 0) { c, b, v, _, p ->
            val style = themeStyleOf(v)
            // IchthysOS: the preset seed first, so a custom accent previews its own palettes.
            val scheme = p.presetSeed?.let { ColorScheme(it, p.dark, style) }
                ?: p.seedColors?.let { ColorScheme(it, p.dark, style) }
                ?: ColorScheme(p.accent, p.dark, style)
            val r = min(b.width(), b.height()) * 0.36f
            val cx = b.centerX()
            val cy = b.top + r + b.height() * 0.06f
            tmp.set(cx - r, cy - r, cx + r, cy + r)
            c.drawArc(tmp, 180f, 180f, true, fillPaint(scheme.accent1.getAtTone(if (p.dark) 80f else 40f)))
            c.drawArc(tmp, 90f, 90f, true, fillPaint(scheme.accent2.getAtTone(if (p.dark) 80f else 50f)))
            c.drawArc(tmp, 0f, 90f, true, fillPaint(scheme.accent3.getAtTone(if (p.dark) 80f else 50f)))
            // the surfaces the scheme gives
            val sw = b.width() / 5
            val tones = floatArrayOf(10f, 30f, 50f, 70f, 90f)
            val y = b.bottom - b.height() * 0.16f
            for (i in tones.indices) {
                rr(c, b.left + i * sw + sw * 0.1f, y, b.left + (i + 1) * sw - sw * 0.1f,
                    y + b.height() * 0.13f, sw * 0.2f, fillPaint(scheme.neutral1.getAtTone(tones[i])))
            }
        }
        m["theme_style"] = monet

        // ---- Status bar

        m["theme_studio_statusbar"] = Spec(2, 0.6f, 0) { c, b, v, _, p ->
            val bar = statusStrip(c, b, p)
            val h = bar.height()
            clockText(c, "12:30", bar.left, bar.centerY(), h * 0.72f, p.fg, Paint.Align.LEFT)
            var x = battery(c, bar.right, bar.centerY(), h * 0.62f, p.fg) - h * 0.3f
            val zig = v == "zigzag"
            for (i in 0 until 6) {
                val dy = if (zig) (if (i % 2 == 0) -h * 0.24f else h * 0.24f) else 0f
                c.drawCircle(x - h * 0.22f, bar.centerY() + dy, h * 0.2f, fillPaint(p.fg))
                x -= if (zig) h * 0.32f else h * 0.55f
            }
        }

        // IchthysOS: one picture per value SystemUI draws (UnifiedBattery / IchthysBatteryStyles,
        // tracker#37), shared by the status bar list and the QS header override. Geometry follows
        // the SystemUI canvases at the stock frame thickness (step 3); the level is 64 %.
        val batteryStyle = Spec(3, 0.8f, 0) { c, b, v, _, p ->
            val cx = b.centerX()
            val cy = b.centerY() - b.height() * 0.04f
            val s = min(b.width(), b.height()) * 0.5f
            val level = 0.64f
            val track = alpha(p.fg, 0.3f)
            when (v) {
                "-1" -> { // QS header follows the status bar: whatever the bar shows
                    val bar = statusStrip(c, b, p)
                    val h = bar.height()
                    clockText(c, "12:30", bar.left, bar.centerY(), h * 0.72f, p.fg, Paint.Align.LEFT)
                    battery(c, bar.right, bar.centerY(), h * 0.62f, p.fg)
                }
                "4" -> battery(c, cx + s * 0.6f, cy, s * 0.6f, p.fg) // landscape pill
                "1", "3" -> { // circle / dotted circle: ring track, level arc from 12 o'clock
                    val pnt = strokePaint(track, s * 0.11f)
                    if (v == "3") pnt.pathEffect = DashPathEffect(floatArrayOf(s * 0.06f, s * 0.06f), 0f)
                    tmp.set(cx - s * 0.4f, cy - s * 0.4f, cx + s * 0.4f, cy + s * 0.4f)
                    c.drawArc(tmp, 0f, 360f, false, pnt)
                    pnt.color = p.fg
                    c.drawArc(tmp, -90f, 360f * level, false, pnt)
                    clockText(c, "64", cx, cy, s * 0.26f, p.fg)
                }
                "5" -> { // filled circle: faint disc, level pie, outline
                    c.drawCircle(cx, cy, s * 0.42f, fillPaint(track))
                    tmp.set(cx - s * 0.42f, cy - s * 0.42f, cx + s * 0.42f, cy + s * 0.42f)
                    c.drawArc(tmp, -90f, 360f * level, true, fillPaint(p.fg))
                    c.drawCircle(cx, cy, s * 0.4f, strokePaint(p.fg, s * 0.05f))
                }
                "2" -> clockText(c, "64%", cx, cy, s * 0.42f, p.fg)
                "6" -> { // hidden: the room the icon would take
                    val w = s * 1.1f
                    rr(c, cx - w / 2, cy - s * 0.3f, cx + w / 2, cy + s * 0.3f, s * 0.1f,
                        strokePaint(p.faint, s * 0.05f).apply {
                            pathEffect = DashPathEffect(floatArrayOf(s * 0.08f, s * 0.08f), 0f)
                        })
                }
                "7" -> { // segments: 22x12 landscape cell, four bars lit from the left
                    val u = s * 0.075f
                    val l = cx - 11 * u
                    val top = cy - 6 * u
                    val t = 1.6f * u
                    rr(c, l, top, l + 20 * u, top + 12 * u, 2 * u, strokePaint(p.fg, t))
                    rr(c, l + 20 * u, cy - 2.2f * u, l + 21.6f * u, cy + 2.2f * u, 0.5f * u,
                        fillPaint(p.fg))
                    val gap = 0.9f * u
                    val cellW = (20 * u - 2 * t - gap * 5) / 4
                    val lit = ((level * 100 + 12) / 25).toInt()
                    for (i in 0 until 4) {
                        val x = l + t + gap + i * (cellW + gap)
                        rr(c, x, top + t + gap, x + cellW, top + 12 * u - t - gap, 0.7f * u,
                            fillPaint(if (i < lit) p.fg else alpha(p.fg, 0.22f)))
                    }
                }
                "8" -> { // arc gauge: 270 degrees open at the bottom, percent in the opening
                    val pnt = strokePaint(track, s * 0.11f)
                    tmp.set(cx - s * 0.4f, cy - s * 0.4f, cx + s * 0.4f, cy + s * 0.4f)
                    c.drawArc(tmp, 135f, 270f, false, pnt)
                    pnt.color = p.fg
                    c.drawArc(tmp, 135f, 270f * level, false, pnt)
                    clockText(c, "64", cx, cy, s * 0.26f, p.fg)
                }
                "9" -> { // bar: thin rounded track, no frame or terminal
                    val u = s * 0.075f
                    val l = cx - 12 * u
                    val h = 5 * u
                    rr(c, l, cy - h / 2, l + 24 * u, cy + h / 2, h / 2, fillPaint(track))
                    rr(c, l, cy - h / 2, l + max(h, 24 * u * level), cy + h / 2, h / 2,
                        fillPaint(p.fg))
                }
                "10" -> { // hexagon outline filling from the bottom, percent inside
                    val sw = s * 0.1f
                    val r = s * 0.45f
                    fun hex(radius: Float, out: Path) {
                        out.reset()
                        for (i in 0 until 6) {
                            val a = Math.toRadians(60.0 * i - 30.0)
                            val x = cx + radius * cos(a).toFloat()
                            val y = cy + radius * sin(a).toFloat()
                            if (i == 0) out.moveTo(x, y) else out.lineTo(x, y)
                        }
                        out.close()
                    }
                    c.save()
                    c.clipRect(cx - r, cy + r - 2 * r * level, cx + r, cy + r)
                    hex(r - sw * 0.9f, path)
                    c.drawPath(path, fillPaint(alpha(p.fg, 0.45f)))
                    c.restore()
                    hex(r, path)
                    c.drawPath(path, strokePaint(p.fg, sw))
                    clockText(c, "64", cx, cy, s * 0.24f, p.fg)
                }
                else -> { // "0" portrait; SystemUI also falls back to it for unknown values
                    val w = s * 0.5f
                    rr(c, cx - w / 2, cy - s / 2, cx + w / 2, cy + s / 2, w * 0.18f,
                        strokePaint(p.fg, s * 0.06f))
                    rr(c, cx - w * 0.2f, cy - s / 2 - s * 0.08f, cx + w * 0.2f, cy - s / 2,
                        s * 0.03f, fillPaint(p.fg))
                    rr(c, cx - w / 2 + s * 0.08f, cy + s / 2 - s * 0.08f - (s - s * 0.16f) * level,
                        cx + w / 2 - s * 0.08f, cy + s / 2 - s * 0.08f, w * 0.08f, fillPaint(p.fg))
                }
            }
        }
        m["status_bar_battery_style"] = batteryStyle
        m["qs_battery_style"] = batteryStyle

        m["statusbar_battery_bar_style"] = Spec(3, 0.7f, 2400) { c, b, v, t, p ->
            val bar = statusStrip(c, b, p)
            val h = bar.height()
            clockText(c, "12:30", bar.left, bar.centerY(), h * 0.72f, p.fg, Paint.Align.LEFT)
            battery(c, bar.right, bar.centerY(), h * 0.62f, p.fg)
            val level = 0.35f + 0.5f * ease(abs(t * 2 - 1))
            val y = bar.top - h * 0.55f
            val full = RectF(b.left - b.width() * 0.1f, y, b.right + b.width() * 0.1f, y + h * 0.18f)
            val w = full.width() * level
            val paint = fillPaint(p.accent)
            when (v) {
                "1" -> c.drawRect(full.centerX() - w / 2, full.top, full.centerX() + w / 2,
                    full.bottom, paint)
                "2" -> c.drawRect(full.right - w, full.top, full.right, full.bottom, paint)
                else -> c.drawRect(full.left, full.top, full.left + w, full.bottom, paint)
            }
        }

        m["status_bar_clock"] = Spec(3, 0.6f, 0) { c, b, v, _, p ->
            val bar = statusStrip(c, b, p)
            val h = bar.height()
            val size = h * 0.72f
            val iconsLeft = battery(c, bar.right, bar.centerY(), h * 0.62f, p.fg)
            val iconsStart = sysIcons(c, iconsLeft - h * 0.3f, bar.centerY(), h * 0.7f, p.fg)
            when (v) {
                "0" -> clockText(c, "12:30", iconsStart - h * 0.4f, bar.centerY(), size,
                    p.accent, Paint.Align.RIGHT)
                "1" -> clockText(c, "12:30", bar.centerX(), bar.centerY(), size, p.accent)
                else -> clockText(c, "12:30", bar.left, bar.centerY(), size, p.accent,
                    Paint.Align.LEFT)
            }
            if (v != "2") {
                c.drawCircle(bar.left + h * 0.3f, bar.centerY(), h * 0.22f, fillPaint(p.dim))
            }
        }

        m["status_bar_logo_position"] = Spec(2, 0.6f, 0) { c, b, v, _, p ->
            val bar = statusStrip(c, b, p)
            val h = bar.height()
            fun logo(x: Float) {
                path.reset()
                path.moveTo(x - h * 0.35f, bar.centerY())
                path.quadTo(x, bar.centerY() - h * 0.45f, x + h * 0.25f, bar.centerY())
                path.quadTo(x, bar.centerY() + h * 0.45f, x - h * 0.35f, bar.centerY())
                path.moveTo(x + h * 0.2f, bar.centerY())
                path.lineTo(x + h * 0.45f, bar.centerY() - h * 0.25f)
                path.lineTo(x + h * 0.45f, bar.centerY() + h * 0.25f)
                path.close()
                c.drawPath(path, fillPaint(p.accent))
            }
            text.textSize = h * 0.72f
            text.typeface = Typeface.DEFAULT_BOLD
            val clockW = text.measureText("12:30")
            clockText(c, "12:30", bar.left, bar.centerY(), h * 0.72f, p.fg, Paint.Align.LEFT)
            val iconsLeft = battery(c, bar.right, bar.centerY(), h * 0.62f, p.fg)
            val start = sysIcons(c, iconsLeft - h * 0.3f, bar.centerY(), h * 0.7f, p.fg)
            if (v == "1") logo(start - h * 0.8f) else logo(bar.left + clockW + h * 0.8f)
        }

        m["ichthys_privacy_indicator_style"] = Spec(2, 0.6f, 1600) { c, b, v, t, p ->
            val bar = statusStrip(c, b, p)
            val h = bar.height()
            clockText(c, "12:30", bar.left, bar.centerY(), h * 0.72f, p.fg, Paint.Align.LEFT)
            val bl = battery(c, bar.right, bar.centerY(), h * 0.62f, p.fg)
            val a = 0.65f + 0.35f * sin(t * 2 * PI.toFloat())
            c.drawCircle(bar.centerX(), bar.centerY(), h * 0.38f, fillPaint(Color.BLACK))
            if (v == "1") {
                c.drawCircle(bl - h * 0.7f, bar.centerY(), h * 0.2f, fillPaint(alpha(p.green, a)))
            } else {
                c.drawCircle(bar.centerX(), bar.centerY(), h * 0.52f,
                    strokePaint(alpha(p.green, a), h * 0.14f))
            }
        }

        // ---- Quick settings

        m["qs_panel_style"] = Spec(2, 0.9f, 0) { c, b, v, _, p ->
            val cols = if (v == "1") 3 else 2
            val rows = if (v == "1") 3 else 3
            val cw = b.width() / cols
            val ch = b.height() / rows
            for (i in 0 until cols * rows) {
                val cx = b.left + (i % cols) * cw + cw / 2
                val top = b.top + (i / cols) * ch
                val on = i % 3 == 0
                if (v == "1") {
                    c.drawCircle(cx, top + ch * 0.38f, ch * 0.28f,
                        fillPaint(if (on) p.accent else alpha(p.fg, 0.14f)))
                    glyph(c, cx, top + ch * 0.38f, ch * 0.3f, i, if (on) p.screen else p.fg)
                    rr(c, cx - cw * 0.28f, top + ch * 0.78f, cx + cw * 0.28f, top + ch * 0.88f,
                        ch * 0.05f, fillPaint(p.dim))
                } else {
                    rr(c, cx - cw * 0.45f, top + ch * 0.08f, cx + cw * 0.45f, top + ch * 0.9f,
                        ch * 0.3f, fillPaint(if (on) p.accent else alpha(p.fg, 0.12f)))
                    glyph(c, cx - cw * 0.22f, top + ch * 0.49f, ch * 0.32f, i,
                        if (on) p.screen else p.fg)
                    textLines(c, cx - cw * 0.04f, top + ch * 0.36f, cw * 0.4f, 2, ch * 0.16f,
                        if (on) alpha(p.screen, 0.7f) else p.dim)
                }
            }
        }

        m["theme_studio_qs"] = Spec(2, 0.9f, 0) { c, b, v, _, p ->
            val r = min(b.width(), b.height()) * 0.11f
            val pitch = r * 2.5f
            val rowH = if (v == "shoal") pitch * 0.866f else pitch
            var i = 0
            var row = 0
            var y = b.top + r * 1.2f
            while (y < b.bottom - r) {
                val shift = if (v == "shoal" && row % 2 == 1) pitch / 2 else 0f
                var x = b.left + r * 1.3f + shift
                while (x < b.right - r * 0.9f) {
                    val on = (i * 7) % 5 == 0
                    c.drawCircle(x, y, r, fillPaint(if (on) p.accent else alpha(p.fg, 0.14f)))
                    glyph(c, x, y, r, i, if (on) p.screen else p.fg)
                    x += pitch
                    i++
                }
                y += rowH
                row++
            }
        }

        m["qs_tile_shape"] = Spec(3, 0.85f, 1800) { c, b, v, t, p ->
            val s = min(b.width(), b.height()) * 0.36f
            for (i in 0 until 4) {
                val cx = b.centerX() + (if (i % 2 == 0) -1 else 1) * s * 0.62f
                val cy = b.centerY() + (if (i < 2) -1 else 1) * s * 0.62f
                val r = RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
                val on = i == 0 || i == 3
                val paint = fillPaint(if (on) p.accent else alpha(p.fg, 0.16f))
                when (v) {
                    "1" -> { squircle(r, path); c.drawPath(path, paint) }
                    "2" -> c.drawRoundRect(r, s * 0.22f, s * 0.22f, paint)
                    "3" -> c.drawRect(r, paint)
                    "4" -> c.drawOval(r, paint)
                    else -> { // dynamic: the shape follows the tile's state
                        val k = if (i == 0) ease(abs(t * 2 - 1)) else if (on) 1f else 0f
                        val rad = lerp(s / 2, s * 0.24f, k)
                        c.drawRoundRect(r, rad, rad, if (i == 0) fillPaint(
                            if (k > 0.5f) p.accent else alpha(p.fg, 0.16f)) else paint)
                    }
                }
                glyph(c, cx, cy, s * 0.5f, i, if (on) p.screen else p.fg)
            }
        }

        m["qs_tile_animation_style"] = Spec(3, 0.8f, 1500) { c, b, v, t, p ->
            val k = seg(t, 0.05f, 0.6f) // the tap plays, then the tile rests
            val s = min(b.width(), b.height()) * 0.5f
            val cx = b.centerX()
            val cy = b.centerY()
            val on = k > 0.5f
            c.save()
            var alphaMul = 1f
            val damp = 1f - k
            when (v) {
                "1" -> { val sc = 1f - 0.18f * sin(k * PI.toFloat()) * damp * 2f
                    c.scale(sc, sc, cx, cy) }
                "3" -> { // flip around the vertical axis
                    val cam = Camera()
                    cam.rotateY(k * 180f)
                    cam.getMatrix(matrix)
                    matrix.preTranslate(-cx, -cy)
                    matrix.postTranslate(cx, cy)
                    c.concat(matrix)
                }
                "4" -> alphaMul = 1f - 0.75f * sin(k * PI.toFloat())
                "5" -> { val sc = 1f + 0.16f * sin(k * PI.toFloat()); c.scale(sc, sc, cx, cy) }
                "6" -> c.translate(sin(k * PI.toFloat() * 6) * s * 0.12f * damp, 0f)
                "7" -> c.rotate(sin(k * PI.toFloat() * 5) * 12f * damp, cx, cy)
                "8" -> c.rotate(ease(k) * 360f, cx, cy)
                "9" -> { val q = sin(k * PI.toFloat())
                    c.scale(1f + 0.22f * q, 1f - 0.22f * q, cx, cy + s / 2) }
                "10" -> { val cam = Camera()
                    cam.rotateX(sin(k * PI.toFloat()) * 28f)
                    cam.getMatrix(matrix)
                    matrix.preTranslate(-cx, -cy)
                    matrix.postTranslate(cx, cy)
                    c.concat(matrix) }
                "11" -> { val q = if (k < 0.5f) sin(k * 2 * PI.toFloat()) else
                        0.6f * sin((k - 0.5f) * 2 * PI.toFloat())
                    val sc = 1f + 0.16f * q
                    c.scale(sc, sc, cx, cy) }
                "12" -> c.rotate(sin(k * PI.toFloat() * 4) * 16f * damp, cx, cy - s / 2)
                "2" -> Unit
                else -> { val sc = 1f - 0.08f * sin(k * PI.toFloat()); c.scale(sc, sc, cx, cy) }
            }
            val r = RectF(cx - s * 0.75f, cy - s / 2, cx + s * 0.75f, cy + s / 2)
            val col = if (on) p.accent else alpha(p.fg, 0.16f)
            c.drawRoundRect(r, s * 0.32f, s * 0.32f, fillPaint(alpha(col, Color.alpha(col) / 255f * alphaMul)))
            if (v == "2" && k > 0f && k < 1f) {
                c.save()
                path.reset()
                path.addRoundRect(r, s * 0.32f, s * 0.32f, Path.Direction.CW)
                c.clipPath(path)
                c.drawCircle(r.left + s * 0.4f, cy, s * 1.6f * k, fillPaint(alpha(p.accent, 1f - k * 0.3f)))
                c.restore()
            }
            glyph(c, r.left + s * 0.42f, cy, s * 0.45f, 1, alpha(if (on) p.screen else p.fg, alphaMul))
            textLines(c, r.left + s * 0.8f, cy - s * 0.14f, s * 0.6f, 2, s * 0.2f,
                alpha(if (on) p.screen else p.dim, alphaMul))
            c.restore()
        }

        m["qs_brightness_slider_shape"] = Spec(2, 0.5f, 0) { c, b, v, _, p ->
            val h = b.height() * 0.42f
            val track = RectF(b.left + b.width() * 0.04f, b.centerY() - h / 2,
                b.right - b.width() * 0.04f, b.centerY() + h / 2)
            val end = track.left + track.width() * 0.62f
            val rad = when (v) { "3" -> 0f; "2" -> h * 0.25f; else -> h / 2 }
            c.drawRoundRect(track, rad, rad, fillPaint(alpha(p.fg, 0.14f)))
            tmp.set(track.left, track.top, end, track.bottom)
            c.drawRoundRect(tmp, rad, rad, fillPaint(alpha(p.accent, 0.55f)))
            val ts = h * 1.1f
            val thumb = RectF(end - ts / 2, b.centerY() - ts / 2, end + ts / 2, b.centerY() + ts / 2)
            when (v) {
                "1" -> c.drawOval(thumb, fillPaint(p.accent))
                "2" -> c.drawRoundRect(thumb, ts * 0.25f, ts * 0.25f, fillPaint(p.accent))
                "3" -> c.drawRect(thumb, fillPaint(p.accent))
                else -> { tmp.set(track.left, track.top, end, track.bottom)
                    c.drawRoundRect(tmp, rad, rad, fillPaint(p.accent)) }
            }
            c.drawCircle(track.left + h * 0.55f, b.centerY(), h * 0.16f, fillPaint(p.screen))
        }

        m["qs_brightness_slider_position"] = Spec(2, 1.1f, 0) { c, b, v, _, p ->
            val s = phone(c, b, p)
            val panel = RectF(s.left, s.top, s.right, s.top + s.height() * 0.62f)
            rr(c, panel.left, panel.top, panel.right, panel.bottom, s.width() * 0.08f,
                fillPaint(p.panel))
            val sh = panel.height() * 0.11f
            val top = v != "1"
            val sliderY = if (top) panel.top + sh * 0.7f else panel.bottom - sh * 1.7f
            rr(c, panel.left + sh * 0.5f, sliderY, panel.right - sh * 0.5f, sliderY + sh, sh / 2,
                fillPaint(alpha(p.fg, 0.14f)))
            rr(c, panel.left + sh * 0.5f, sliderY, panel.left + panel.width() * 0.6f,
                sliderY + sh, sh / 2, fillPaint(p.accent))
            val gridTop = if (top) sliderY + sh * 1.6f else panel.top + sh * 0.7f
            val cw = (panel.width() - sh) / 2
            for (i in 0 until 6) {
                val l = panel.left + sh * 0.5f + (i % 2) * cw
                val tt = gridTop + (i / 2) * sh * 1.5f
                rr(c, l + sh * 0.1f, tt, l + cw - sh * 0.1f, tt + sh * 1.25f, sh * 0.5f,
                    fillPaint(if (i == 0 || i == 3) p.accent else alpha(p.fg, 0.12f)))
            }
        }

        // ---- Navigation and buttons

        m["recents_type"] = Spec(3, 1.35f, 0) { c, b, v, _, p ->
            val s = phone(c, b, p)
            val w = s.width()
            val h = s.height()
            c.save()
            c.clipRect(s)
            val cardCols = intArrayOf(p.wall1, p.wall2, p.accent, 0xFF5E7C8C.toInt())
            fun card(l: Float, t: Float, r: Float, bot: Float, i: Int, header: Boolean = true) {
                rr(c, l, t, r, bot, w * 0.06f, fillPaint(cardCols[i % 4]))
                if (header) {
                    rr(c, l, t, r, t + (bot - t) * 0.16f, w * 0.06f, fillPaint(alpha(p.panel, 0.9f)))
                    c.drawCircle(l + (bot - t) * 0.08f + w * 0.02f, t + (bot - t) * 0.08f,
                        min((bot - t) * 0.05f, w * 0.05f), fillPaint(p.fg))
                }
            }
            when (v) {
                "1" -> for (i in 0 until 5) { // classic: overlapping vertical stack
                    val sc = 0.78f + i * 0.055f
                    val cw = w * sc
                    val top = s.top + h * (0.06f + i * 0.13f)
                    card(s.centerX() - cw / 2, top, s.centerX() + cw / 2, top + h * 0.42f, i)
                }
                "2" -> { // omni slim: a side panel of apps
                    wallpaper(c, s, p)
                    rr(c, s.right - w * 0.3f, s.top + h * 0.12f, s.right + w, s.bottom - h * 0.12f,
                        w * 0.08f, fillPaint(p.panel))
                    for (i in 0 until 6) {
                        c.drawCircle(s.right - w * 0.15f, s.top + h * (0.2f + i * 0.11f),
                            w * 0.08f, fillPaint(cardCols[i % 4]))
                    }
                }
                "3", "8" -> { // cascades: cards fanning up from the bottom
                    val n = 5
                    for (i in 0 until n) {
                        val d = (n - 1 - i).toFloat()
                        val sc = if (v == "8") 1f - d * 0.07f else 1f - d * 0.04f
                        val top = if (v == "8") s.top + h * (0.08f + i * 0.1f)
                            else s.top + h * (0.04f + i * i * 0.035f)
                        val cw = w * 0.86f * sc
                        card(s.centerX() - cw / 2, top, s.centerX() + cw / 2,
                            top + h * 0.5f * sc, i)
                    }
                }
                "4" -> for (i in 0 until 9) { // three-column grid
                    val cw = w / 3
                    val l = s.left + (i % 3) * cw + cw * 0.08f
                    val top = s.top + h * 0.1f + (i / 3) * h * 0.27f
                    card(l, top, l + cw * 0.84f, top + h * 0.24f, i, false)
                    c.drawCircle(l + cw * 0.42f, top - h * 0.03f + h * 0.27f, w * 0.03f,
                        fillPaint(p.dim))
                }
                "5" -> for (i in 0 until 4) { // tilted deck
                    c.save()
                    val top = s.top + h * (0.1f + i * 0.14f)
                    c.rotate(-8f + i * 2f, s.centerX(), top + h * 0.2f)
                    card(s.left + w * 0.12f, top, s.right - w * 0.12f, top + h * 0.38f, i)
                    c.restore()
                }
                "6" -> for (i in 0 until 2) { // full-width vertical cards
                    val top = s.top + h * (0.08f + i * 0.47f)
                    card(s.left + w * 0.06f, top, s.right - w * 0.06f, top + h * 0.42f, i)
                }
                "7" -> for (i in 0 until 8) { // slim rows
                    val top = s.top + h * (0.08f + i * 0.11f)
                    rr(c, s.left + w * 0.06f, top, s.right - w * 0.06f, top + h * 0.085f,
                        w * 0.05f, fillPaint(p.panel))
                    c.drawCircle(s.left + w * 0.16f, top + h * 0.0425f, h * 0.025f,
                        fillPaint(cardCols[i % 4]))
                    rr(c, s.left + w * 0.28f, top + h * 0.035f, s.left + w * 0.7f,
                        top + h * 0.05f, h * 0.008f, fillPaint(p.dim))
                }
                else -> { // stock: a carousel of big cards
                    card(s.left + w * 0.14f, s.top + h * 0.18f, s.right - w * 0.14f,
                        s.top + h * 0.78f, 0, false)
                    card(s.left - w * 0.7f, s.top + h * 0.22f, s.left + w * 0.08f,
                        s.top + h * 0.74f, 1, false)
                    card(s.right - w * 0.08f, s.top + h * 0.22f, s.right + w * 0.7f,
                        s.top + h * 0.74f, 2, false)
                    c.drawCircle(s.centerX(), s.top + h * 0.12f, w * 0.06f, fillPaint(p.fg))
                }
            }
            c.restore()
        }

        // ---- Sound

        m["volume_panel_style"] = Spec(2, 1.15f, 0) { c, b, v, _, p ->
            val s = phone(c, b, p)
            wallpaper(c, s, p)
            val w = s.width()
            val h = s.height()
            val x = s.right - w * 0.24f
            val sw = w * 0.17f
            if (v == "1") { // One UI: separate floating capsule and settings pill
                rr(c, x - sw / 2, s.top + h * 0.22f, x + sw / 2, s.top + h * 0.66f, sw / 2,
                    fillPaint(p.panel))
                rr(c, x - sw / 2, s.top + h * 0.44f, x + sw / 2, s.top + h * 0.66f, sw / 2,
                    fillPaint(p.accent))
                c.drawCircle(x, s.top + h * 0.73f, sw * 0.5f, fillPaint(p.panel))
                c.drawCircle(x, s.top + h * 0.73f, sw * 0.16f, strokePaint(p.fg, sw * 0.08f))
            } else { // AOSP: one panel holding ringer, slider and settings
                rr(c, x - sw * 0.8f, s.top + h * 0.16f, x + sw * 0.8f, s.top + h * 0.8f, sw * 0.8f,
                    fillPaint(p.panel))
                c.drawCircle(x, s.top + h * 0.23f, sw * 0.5f, fillPaint(p.accent))
                rr(c, x - sw * 0.3f, s.top + h * 0.32f, x + sw * 0.3f, s.top + h * 0.68f, sw * 0.3f,
                    fillPaint(alpha(p.fg, 0.16f)))
                rr(c, x - sw * 0.3f, s.top + h * 0.5f, x + sw * 0.3f, s.top + h * 0.68f, sw * 0.3f,
                    fillPaint(p.accent))
                c.drawCircle(x, s.top + h * 0.74f, sw * 0.16f, strokePaint(p.fg, sw * 0.08f))
            }
        }

        // ---- Fingerprint (drawn as SystemUI draws them: IchthysFpIconDrawable, UdfpsAnimation)

        m["ichthys_udfps_icon_style"] = Spec(3, 0.85f, 0) { c, b, v, _, p ->
            c.drawRoundRect(b, b.width() * 0.08f, b.width() * 0.08f, fillPaint(p.night))
            fpIcon(c, b.centerX(), b.centerY(), min(b.width(), b.height()) * 0.6f,
                v.toIntOrNull() ?: 0, Color.WHITE)
        }

        m["ichthys_udfps_pressed_style"] = Spec(2, 0.8f, 0) { c, b, v, _, p ->
            c.drawRoundRect(b, b.width() * 0.06f, b.width() * 0.06f, fillPaint(p.night))
            val r = min(b.width(), b.height()) * 0.2f
            fpPressed(c, b.centerX(), b.centerY(), r, v.toIntOrNull() ?: 0)
            fpIcon(c, b.centerX(), b.centerY(), r * 1.6f, 1, alpha(Color.WHITE, 0.9f))
        }

        m["ichthys_udfps_anim_effect"] = Spec(3, 0.85f, 1600) { c, b, v, t, p ->
            c.drawRoundRect(b, b.width() * 0.08f, b.width() * 0.08f, fillPaint(p.night))
            val r = min(b.width(), b.height()) * 0.15f
            val effect = v.toIntOrNull() ?: 0
            if (effect == 0) {
                // the chosen animation pack: show a stand-in loop
                val pnt = strokePaint(alpha(p.accent, 0.9f), r * 0.12f)
                pnt.pathEffect = DashPathEffect(floatArrayOf(r * 0.3f, r * 0.25f), t * r * 4f)
                c.drawCircle(b.centerX(), b.centerY(), r * 1.5f, pnt)
            } else if (effect > 0) {
                fpEffect(c, b.centerX(), b.centerY(), r, r * 2.2f, effect, t, p.accent)
            }
            fpIcon(c, b.centerX(), b.centerY(), r * 1.6f, 1, Color.WHITE)
        }

        m["ichthys_udfps_success_effect"] = Spec(2, 0.8f, 1800) { c, b, v, t, p ->
            c.drawRoundRect(b, b.width() * 0.06f, b.width() * 0.06f, fillPaint(p.night))
            val r = min(b.width(), b.height()) * 0.18f
            val k = seg(t, 0.2f, 0.75f)
            fpIcon(c, b.centerX(), b.centerY(), r * 1.6f, 1,
                alpha(Color.WHITE, if (k > 0f && v != "0") 0.45f else 1f))
            if (k > 0f) fpSuccess(c, b.centerX(), b.centerY(), r, v.toIntOrNull() ?: 0, k, p.accent)
        }

        m["ichthys_udfps_fail_effect"] = Spec(3, 0.85f, 1600) { c, b, v, t, p ->
            c.drawRoundRect(b, b.width() * 0.08f, b.width() * 0.08f, fillPaint(p.night))
            val r = min(b.width(), b.height()) * 0.18f
            val k = seg(t, 0.2f, 0.7f)
            c.save()
            if (v == "1" && k > 0f && k < 1f) {
                c.translate(sin(k * PI.toFloat() * 6) * r * 0.22f * (1f - k), 0f)
            }
            fpIcon(c, b.centerX(), b.centerY(), r * 1.6f, 1, Color.WHITE)
            if (v == "2" && k > 0f && k < 1f) {
                c.drawCircle(b.centerX(), b.centerY(), r * 1.3f,
                    strokePaint(alpha(p.red, 1f - k), r * 0.14f))
            }
            c.restore()
        }

        // ---- Lock screen

        m["pulse_renderer"] = Spec(3, 0.75f, 2200) { c, b, v, t, p ->
            c.drawRoundRect(b, b.width() * 0.08f, b.width() * 0.08f, fillPaint(p.night))
            val n = 14
            val bw = b.width() / n
            fun amp(i: Int): Float {
                val x = i / n.toFloat()
                val tt = t * 2 * PI.toFloat()
                return (0.25f + 0.35f * abs(sin(x * 7f + tt * 2)) +
                    0.3f * abs(sin(x * 3f - tt * 3 + i))).coerceIn(0.08f, 0.95f)
            }
            val base = b.bottom - b.height() * 0.12f
            val maxH = b.height() * 0.72f
            when (v) {
                "fading" -> for (i in 0 until n) {
                    val blocks = (amp(i) * 8).toInt()
                    for (j in 0 until blocks) {
                        val y = base - j * maxH / 8
                        c.drawRect(b.left + i * bw + bw * 0.15f, y - maxH / 8 * 0.7f,
                            b.left + (i + 1) * bw - bw * 0.15f, y, fillPaint(alpha(p.accent, 1f - j / 8f)))
                    }
                }
                "neon" -> {
                    val pnt = strokePaint(p.accent, bw * 0.45f)
                    pnt.maskFilter = BlurMaskFilter(bw * 0.5f, BlurMaskFilter.Blur.SOLID)
                    for (i in 0 until n) {
                        val x = b.left + i * bw + bw / 2
                        c.drawLine(x, base, x, base - maxH * amp(i), pnt)
                    }
                    stroke.maskFilter = null
                }
                "retro" -> for (i in 0 until n) {
                    val segs = (amp(i) * 10).toInt()
                    for (j in 0 until segs) {
                        val col = when { j >= 8 -> p.red; j >= 6 -> 0xFFFFC94D.toInt(); else -> p.green }
                        val y = base - j * maxH / 10
                        c.drawRect(b.left + i * bw + bw * 0.12f, y - maxH / 10 * 0.6f,
                            b.left + (i + 1) * bw - bw * 0.12f, y, fillPaint(col))
                    }
                }
                "minimal" -> {
                    path.reset()
                    for (i in 0..40) {
                        val x = b.left + b.width() * i / 40f
                        val y = b.centerY() + sin(i / 40f * 12f + t * 2 * PI.toFloat() * 2) *
                            b.height() * 0.18f * sin(i / 40f * PI.toFloat())
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    c.drawPath(path, strokePaint(p.accent, b.height() * 0.025f))
                }
                "sparkle" -> for (i in 0 until n) {
                    val x = b.left + i * bw + bw / 2
                    val y = base - maxH * amp(i)
                    c.drawLine(x, base, x, y, strokePaint(alpha(p.accent, 0.35f), bw * 0.3f))
                    val tw = 0.5f + 0.5f * sin(t * 20f + i * 2f)
                    c.drawCircle(x, y, bw * (0.15f + 0.2f * tw), fillPaint(Color.WHITE))
                }
                "matrix" -> for (i in 0 until n) {
                    val cells = (amp(i) * 9).toInt()
                    for (j in 0 until cells) {
                        val y = base - j * maxH / 9
                        val a = if (j == cells - 1) 1f else 0.25f + 0.5f * j / cells
                        rr(c, b.left + i * bw + bw * 0.25f, y - maxH / 9 * 0.55f,
                            b.left + (i + 1) * bw - bw * 0.25f, y, bw * 0.1f,
                            fillPaint(alpha(if (j == cells - 1) 0xFFB8FFB0.toInt() else p.green, a)))
                    }
                }
                "particle" -> for (i in 0 until n * 2) {
                    val ph = (t + (i * 0.618f) % 1f) % 1f
                    val x = b.left + (i % n) * bw + bw / 2 + sin(i + ph * 6f) * bw * 0.4f
                    val y = base - maxH * ph * amp(i % n) * 1.2f
                    c.drawCircle(x, y, bw * 0.2f * (1 - ph * 0.6f), fillPaint(alpha(p.accent, 1 - ph)))
                }
                "waveform" -> {
                    path.reset()
                    val mid = b.centerY()
                    path.moveTo(b.left, mid)
                    for (i in 0 until n) path.lineTo(b.left + i * bw + bw / 2, mid - maxH / 2 * amp(i))
                    path.lineTo(b.right, mid)
                    for (i in n - 1 downTo 0) path.lineTo(b.left + i * bw + bw / 2, mid + maxH / 2 * amp(i))
                    path.close()
                    c.drawPath(path, fillPaint(alpha(p.accent, 0.8f)))
                }
                else -> for (i in 0 until n) { // solid
                    val x = b.left + i * bw + bw / 2
                    c.drawLine(x, base, x, base - maxH * amp(i), strokePaint(p.accent, bw * 0.55f).apply {
                        strokeCap = Paint.Cap.BUTT
                    })
                    stroke.strokeCap = Paint.Cap.ROUND
                }
            }
        }

        m["edge_light_style"] = Spec(2, 1.1f, 2000) { c, b, v, t, p ->
            val s = phone(c, b, p, p.night)
            clockText(c, "12:30", s.centerX(), s.top + s.height() * 0.3f, s.width() * 0.28f,
                alpha(Color.WHITE, 0.8f))
            val a = 0.35f + 0.65f * ease(abs(t * 2 - 1))
            val pnt = strokePaint(alpha(p.accent, a), s.width() * 0.05f)
            pnt.maskFilter = BlurMaskFilter(s.width() * 0.04f, BlurMaskFilter.Blur.NORMAL)
            if (v == "rounded") {
                c.drawRoundRect(s, s.width() * 0.1f, s.width() * 0.1f, pnt)
            } else {
                c.drawLine(s.left, s.top + s.height() * 0.08f, s.left, s.bottom - s.height() * 0.08f, pnt)
                c.drawLine(s.right, s.top + s.height() * 0.08f, s.right, s.bottom - s.height() * 0.08f, pnt)
            }
            stroke.maskFilter = null
        }

        m["ls_media_art_filter"] = Spec(3, 0.85f, 0) { c, b, v, _, p ->
            val art = albumArt(b.width().toInt().coerceAtLeast(8), b.height().toInt().coerceAtLeast(8))
            val blur = v == "1" || v == "6"
            val src = if (blur) {
                val small = Bitmap.createScaledBitmap(art, max(2, art.width / 14), max(2, art.height / 14), true)
                Bitmap.createScaledBitmap(small, art.width, art.height, true)
            } else art
            val cm = ColorMatrix()
            when (v) {
                "2", "6" -> cm.setSaturation(0f)
                "3" -> cm.set(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f))
                "4" -> {
                    val r = Color.red(p.accent) / 255f
                    val g = Color.green(p.accent) / 255f
                    val bl = Color.blue(p.accent) / 255f
                    cm.set(floatArrayOf(
                        0.3f * r, 0.59f * r, 0.11f * r, 0f, 0f,
                        0.3f * g, 0.59f * g, 0.11f * g, 0f, 0f,
                        0.3f * bl, 0.59f * bl, 0.11f * bl, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f))
                }
                "5" -> cm.set(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f))
            }
            val pnt = fillPaint(Color.WHITE)
            pnt.colorFilter = ColorMatrixColorFilter(cm)
            pnt.isFilterBitmap = true
            c.save()
            path.reset()
            path.addRoundRect(b, b.width() * 0.08f, b.width() * 0.08f, Path.Direction.CW)
            c.clipPath(path)
            c.drawBitmap(src, b.left, b.top, pnt)
            c.restore()
            fill.colorFilter = null
        }

        // ---- Volume: the horizontal panel's looks (SystemUI HorizontalVolumeSlider). One row
        // each: stream icon, track, level. Drawn with the theme's accent and text colours, as
        // the real row draws with the Shoal tokens.

        m["volume_horizontal_style"] = Spec(2, 0.5f, 0) { c, b, v, _, p ->
            volumeRow(c, b, v, 1, p)
        }

        m["volume_horizontal_thickness"] = Spec(3, 0.5f, 0) { c, b, v, _, p ->
            volumeRow(c, b, "0", v.toIntOrNull() ?: 1, p)
        }

        // ---- Weather: each icon pack's own sun, cloud, rain and snow (OmniJaws condition
        // codes 32, 26, 11, 16), tinted in the text colour when the pack is line art.

        m["weather_icon_pack"] = Spec(2, 0.5f, 0) { c, b, v, _, p ->
            val codes = intArrayOf(32, 26, 11, 16)
            val n = codes.size
            val s = min(b.width() / (n + 1.2f), b.height() * 0.78f)
            val gap = (b.width() - n * s) / (n + 1)
            val top = b.centerY() - s / 2
            for (i in 0 until n) {
                val d = LockWeather.icon(p.ctx, v, codes[i]) ?: continue
                val m2 = d.mutate()
                if (LockWeather.isMono(v)) m2.setTint(p.fg) else m2.setTintList(null)
                val l = b.left + gap + i * (s + gap)
                m2.setBounds(l.toInt(), top.toInt(), (l + s).toInt(), (top + s).toInt())
                m2.draw(c)
            }
        }

        // ---- IchthysOS: charger-connected animation (System > Battery and display)

        m[ChargingAnimationSettings.KEY] = chargingSpec()

        return m
    }

    /**
     * One horizontal volume row in look [style] ("0" Shoal pill, "1" thin line, "2" segmented,
     * "3" filled card) at [thickness] (0 slim, 1 regular, 2 bold), as HorizontalVolumeSlider
     * lays it out: the icon in the accent, the fill up to 62 %, the level in a mono-ish bold.
     */
    private fun volumeRow(c: Canvas, b: RectF, style: String, thickness: Int, p: Pal) {
        val frac = 0.62f
        val w = b.width() * 0.92f
        val l = b.centerX() - w / 2
        val r = l + w
        val cy = b.centerY()
        val unit = b.height() / 10f
        val weight = floatArrayOf(0.75f, 1f, 1.25f)[thickness.coerceIn(0, 2)]
        val track = alpha(p.fg, 0.10f)
        val textSize = unit * 1.6f
        fun speaker(cx: Float, s: Float, color: Int) {
            path.reset()
            path.moveTo(cx - s * 0.5f, cy - s * 0.22f)
            path.lineTo(cx - s * 0.15f, cy - s * 0.22f)
            path.lineTo(cx + s * 0.25f, cy - s * 0.5f)
            path.lineTo(cx + s * 0.25f, cy + s * 0.5f)
            path.lineTo(cx - s * 0.15f, cy + s * 0.22f)
            path.lineTo(cx - s * 0.5f, cy + s * 0.22f)
            path.close()
            c.drawPath(path, fillPaint(color))
            tmp.set(cx - s * 0.1f, cy - s * 0.5f, cx + s * 0.7f, cy + s * 0.5f)
            c.drawArc(tmp, -40f, 80f, false, strokePaint(color, s * 0.12f))
        }
        when (style) {
            "1" -> { // thin line, round knob
                val ic = unit * 2.2f
                speaker(l + ic * 0.5f, ic, p.accent)
                val x0 = l + ic * 1.4f
                val x1 = r - unit * 4f
                val knob = unit * 1.4f * weight
                val tw = unit * 0.4f * weight
                val kx = x0 + knob / 2 + (x1 - x0 - knob) * frac
                c.drawRoundRect(RectF(x0, cy - tw / 2, x1, cy + tw / 2), tw / 2, tw / 2,
                    fillPaint(track))
                c.drawRoundRect(RectF(x0, cy - tw / 2, kx, cy + tw / 2), tw / 2, tw / 2,
                    fillPaint(p.accent))
                c.drawCircle(kx, cy, knob / 2, fillPaint(p.accent))
                clockText(c, "19", r, cy, textSize, p.fg, Paint.Align.RIGHT)
            }
            "2" -> { // segmented bars
                val ic = unit * 2.2f
                speaker(l + ic * 0.5f, ic, p.accent)
                val x0 = l + ic * 1.4f
                val x1 = r - unit * 4f
                val n = 12
                val gap = unit * 0.3f
                val bw = (x1 - x0 - gap * (n - 1)) / n
                val bh = unit * 2f * weight
                val lit = Math.round(frac * n)
                for (i in 0 until n) {
                    val x = x0 + i * (bw + gap)
                    rr(c, x, cy - bh / 2, x + bw, cy + bh / 2, min(bw, bh) / 2,
                        fillPaint(if (i < lit) p.accent else track))
                }
                clockText(c, "19", r, cy, textSize, p.fg, Paint.Align.RIGHT)
            }
            "3" -> { // the row is the card
                val h = unit * 4.6f * weight
                val rad = h * 0.22f
                tmp.set(l, cy - h / 2, r, cy + h / 2)
                c.save()
                path.reset()
                path.addRoundRect(tmp, rad, rad, Path.Direction.CW)
                c.clipPath(path)
                c.drawRect(tmp, fillPaint(alpha(p.fg, 0.07f)))
                c.drawRect(l, tmp.top, l + w * frac, tmp.bottom, fillPaint(alpha(p.accent, 0.35f)))
                c.restore()
                rr(c, l, tmp.top, r, tmp.bottom, rad, strokePaint(p.faint, unit * 0.12f))
                val ic = min(unit * 2.2f, h * 0.5f)
                speaker(l + unit * 1.6f, ic, p.accent)
                textLines(c, l + unit * 3.4f, cy - ic * 0.22f, w * 0.3f, 1, ic * 0.44f, p.dim)
                clockText(c, "19", r - unit * 1.2f, cy, textSize, p.fg, Paint.Align.RIGHT)
            }
            else -> { // Shoal pill: icon and level inside the track
                val h = unit * 3.6f * weight
                tmp.set(l, cy - h / 2, r, cy + h / 2)
                c.save()
                path.reset()
                path.addRoundRect(tmp, h / 2, h / 2, Path.Direction.CW)
                c.clipPath(path)
                c.drawRect(tmp, fillPaint(track))
                c.drawRect(l, tmp.top, l + w * frac, tmp.bottom, fillPaint(alpha(p.accent, 0.45f)))
                c.restore()
                rr(c, l, tmp.top, r, tmp.bottom, h / 2, strokePaint(p.faint, unit * 0.12f))
                val ic = min(unit * 2.2f, h * 0.55f)
                speaker(l + h * 0.55f, ic, p.accent)
                clockText(c, "19", r - h * 0.4f, cy, min(textSize, h * 0.5f), p.fg,
                    Paint.Align.RIGHT)
            }
        }
    }

    private var artCache: Bitmap? = null

    /** A made-up album cover: dusk sky, a sun, a hill. */
    private fun albumArt(w: Int, h: Int): Bitmap {
        artCache?.let { if (it.width == w && it.height == h) return it }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pnt = Paint(Paint.ANTI_ALIAS_FLAG)
        pnt.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF2B2D7A.toInt(),
            0xFFF07B54.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pnt)
        pnt.shader = null
        pnt.color = 0xFFFFD36B.toInt()
        c.drawCircle(w * 0.62f, h * 0.5f, min(w, h) * 0.2f, pnt)
        pnt.color = 0xFF1E5E4A.toInt()
        val hill = Path()
        hill.moveTo(0f, h * 0.72f)
        hill.quadTo(w * 0.35f, h * 0.5f, w * 0.7f, h * 0.7f)
        hill.quadTo(w * 0.88f, h * 0.78f, w.toFloat(), h * 0.66f)
        hill.lineTo(w.toFloat(), h.toFloat())
        hill.lineTo(0f, h.toFloat())
        hill.close()
        c.drawPath(hill, pnt)
        pnt.color = 0xFF0E2E26.toInt()
        c.drawRect(0f, h * 0.88f, w.toFloat(), h.toFloat(), pnt)
        artCache = bmp
        return bmp
    }

    // ---------------------------------------------------------------- fingerprint drawing
    // Ported from SystemUI's IchthysFpIconDrawable and UdfpsAnimation so the pictures match.

    private fun fpArcs(c: Canvas, cx: Float, cy: Float, u: Float, count: Int, step: Float,
            gapDeg: Float, pnt: Paint) {
        for (i in 0 until count) {
            val r = (2.2f + i * step) * u
            tmp.set(cx - r, cy - r, cx + r, cy + r)
            val start = 90f + gapDeg / 2f + (if (i % 2 == 0) 0f else 12f)
            c.drawArc(tmp, start, 360f - gapDeg, false, pnt)
        }
    }

    private fun fpIcon(c: Canvas, cx: Float, cy: Float, size: Float, style: Int, color: Int) {
        val u = size / 24f
        val pnt = strokePaint(color, 1.6f * u)
        val dot = fillPaint(color)
        when (style) {
            2 -> fpArcs(c, cx, cy, u, 3, 3.2f, 60f, pnt)
            3 -> {
                pnt.pathEffect = DashPathEffect(floatArrayOf(0.01f, 2.4f * u), 0f)
                pnt.strokeWidth = 2.1f * u
                fpArcs(c, cx, cy, u, 4, 2.6f, 30f, pnt)
            }
            4 -> {
                c.drawCircle(cx, cy, 10.5f * u, pnt)
                fpArcs(c, cx, cy, u * 0.6f, 4, 2.4f, 40f, pnt)
            }
            5 -> for (i in 0 until 4) {
                val r = (3f + i * 2.4f) * u
                tmp.set(cx - r, cy - r - u, cx + r, cy + r - u)
                c.drawArc(tmp, 110f, 120f, false, pnt)
                tmp.offset(0f, 2 * u)
                c.drawArc(tmp, -70f, 120f, false, pnt)
            }
            6 -> {
                for (i in 1..3) c.drawCircle(cx, cy, i * 3.4f * u, pnt)
                c.drawLine(cx, cy, cx + 7.2f * u, cy - 7.2f * u, pnt)
                c.drawCircle(cx + 5f * u, cy - 5f * u, 1.2f * u, dot)
            }
            7 -> {
                pnt.strokeWidth = 2.6f * u
                c.drawCircle(cx, cy, 6f * u, pnt)
                pnt.strokeWidth = u
                c.drawCircle(cx, cy, 10.5f * u, pnt)
                c.drawCircle(cx, cy, 1.4f * u, dot)
            }
            8 -> {
                val d = 0.9f * u
                val pitch = 2.9f * u
                val rowH = pitch * 0.866f
                val lim = 10.5f * u
                for (row in -4..4) {
                    val y = row * rowH
                    val shift = (abs(row) % 2) * pitch / 2f
                    for (col in -4..4) {
                        val x = col * pitch + shift
                        val dd = hypot(x, y)
                        if (dd > lim) continue
                        c.drawCircle(cx + x, cy + y, d * (1f - 0.35f * dd / lim), dot)
                    }
                }
            }
            0 -> { // the system's own fingerprint glyph (ridges with a broken core)
                pnt.strokeWidth = 1.5f * u
                for (i in 0 until 5) {
                    val r = (2.4f + i * 2.1f) * u
                    tmp.set(cx - r, cy - r + 1.5f * u, cx + r, cy + r + 1.5f * u)
                    c.drawArc(tmp, 200f - i * 4f, 140f + i * 8f, false, pnt)
                }
                c.drawLine(cx, cy - 0.5f * u, cx, cy + 6f * u, pnt)
            }
            else -> fpArcs(c, cx, cy, u, 5, 2.2f, 40f, pnt)
        }
    }

    private fun fpPressed(c: Canvas, cx: Float, cy: Float, r: Float, style: Int) {
        val col = 0xFFFFFFFF.toInt()
        val pr = r
        when (style) {
            1 -> c.drawCircle(cx, cy, pr, fillPaint(alpha(col, 0.35f)))
            2 -> c.drawCircle(cx, cy, pr, strokePaint(col, r * 0.08f))
            3 -> {
                fill.shader = RadialGradient(cx, cy, pr * 1.6f,
                    intArrayOf(alpha(col, 0.55f), alpha(col, 0.22f), Color.TRANSPARENT),
                    floatArrayOf(r / (pr * 1.6f), 0.7f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, pr * 1.6f, fill)
                fill.shader = null
            }
        }
    }

    private fun fpEffect(c: Canvas, cx: Float, cy: Float, r: Float, outer: Float, effect: Int,
            t: Float, col: Int) {
        val inner = r * 1.08f
        when (effect) {
            1 -> for (i in 0 until 3) {
                val ph = (t + i / 3f) % 1f
                c.drawCircle(cx, cy, inner + (outer - inner) * ph,
                    strokePaint(alpha(col, (1f - ph) * 0.9f), r * 0.06f))
            }
            2 -> {
                val mid = (inner + outer * 0.85f) / 2f
                val band = outer * 0.85f - inner
                val sweep = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, alpha(col, 0.15f),
                    alpha(col, 0.9f), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 0.98f, 1f))
                matrix.setRotate(t * 360f, cx, cy)
                sweep.setLocalMatrix(matrix)
                val pnt = strokePaint(col, band)
                pnt.shader = sweep
                c.drawCircle(cx, cy, mid, pnt)
                c.drawCircle(cx, cy, outer * 0.85f, strokePaint(alpha(col, 0.5f), r * 0.03f))
            }
            3 -> {
                val n = 20
                for (i in 0 until n) {
                    val ph = (t + (i * 0.618f) % 1f) % 1f
                    val ang = i * (2 * PI / n) + i * 0.37
                    val d = inner + (outer - inner) * ph
                    c.drawCircle(cx + cos(ang).toFloat() * d, cy + sin(ang).toFloat() * d,
                        r * (0.07f - 0.04f * ph), fillPaint(alpha(col, 1f - ph)))
                }
            }
            4 -> {
                val dot = r * 0.075f
                val pitch = dot * 3.2f
                val rowH = pitch * 0.866f
                val rows = Math.ceil((outer / rowH).toDouble()).toInt()
                for (row in -rows..rows) {
                    val y = row * rowH
                    val shift = (abs(row) % 2) * pitch / 2f
                    val cols = Math.ceil((outer / pitch).toDouble()).toInt() + 1
                    for (cc in -cols..cols) {
                        val x = cc * pitch + shift
                        val d = hypot(x, y)
                        if (d < inner || d > outer) continue
                        val wave = 1f - min(1f, abs((d - inner) / (outer - inner) - t) * 4f)
                        if (wave <= 0.02f) continue
                        c.drawCircle(cx + x, cy + y, dot * (0.6f + 0.4f * wave),
                            fillPaint(alpha(col, wave)))
                    }
                }
            }
            5 -> {
                val s = 0.5f + 0.5f * sin(t * 2 * PI.toFloat())
                val pr = inner + (outer * 0.8f - inner) * s
                val a0 = min(inner / pr, 0.85f)
                fill.shader = RadialGradient(cx, cy, pr,
                    intArrayOf(Color.TRANSPARENT, alpha(col, 0.55f), Color.TRANSPARENT),
                    floatArrayOf(a0, max(a0 + 0.05f, 0.8f), 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, pr, fill)
                fill.shader = null
            }
            6 -> for (i in 0 until 3) {
                val rad = inner + (outer - inner) * (0.25f + 0.3f * i)
                tmp.set(cx - rad, cy - rad, cx + rad, cy + rad)
                val start = (t * 360f * (if (i % 2 == 0) 1f else -1.4f)) + i * 70f
                val sw = 90f + 60f * sin((t + i * 0.3f) * 2 * PI.toFloat())
                c.drawArc(tmp, start, sw, false, strokePaint(alpha(col, 0.75f - i * 0.15f), r * 0.09f))
            }
            7 -> {
                val rad = inner + (outer - inner) * 0.25f
                tmp.set(cx - rad, cy - rad, cx + rad, cy + rad)
                val sweep = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, col),
                    floatArrayOf(0.25f, 1f))
                matrix.setRotate(t * 360f - 90f, cx, cy)
                sweep.setLocalMatrix(matrix)
                val pnt = strokePaint(col, r * 0.1f)
                pnt.shader = sweep
                c.drawArc(tmp, t * 360f - 360f + 90f, 270f, false, pnt)
            }
            8 -> {
                c.save()
                path.reset()
                path.addCircle(cx, cy, outer * 0.8f, Path.Direction.CW)
                path.addCircle(cx, cy, inner, Path.Direction.CCW)
                c.clipPath(path)
                val top = cy - outer * 0.8f
                val h = outer * 1.6f
                val bandY = top + h * t
                val gap = r * 0.09f
                var y = top
                while (y < top + h) {
                    val near = 1f - min(1f, abs(y - bandY) / (h * 0.18f))
                    val jitter = if (near > 0.6f && ((y / gap + t * 40).toInt() % 3 == 0))
                        r * 0.12f else 0f
                    c.drawLine(cx - outer + jitter, y, cx + outer + jitter, y,
                        strokePaint(alpha(col, 0.15f + 0.8f * near), r * 0.03f))
                    y += gap
                }
                c.restore()
            }
        }
    }

    private fun fpSuccess(c: Canvas, cx: Float, cy: Float, r: Float, style: Int, k: Float,
            col: Int) {
        val rad = r * 1.3f
        when (style) {
            1 -> {
                tmp.set(cx - rad, cy - rad, cx + rad, cy + rad)
                c.drawArc(tmp, -90f, 360f * min(1f, k * 1.4f), false, strokePaint(col, r * 0.12f))
            }
            2 -> {
                val pnt = strokePaint(col, r * 0.12f)
                c.drawCircle(cx, cy, rad, pnt)
                val q0 = min(1f, k * 1.5f)
                val x0 = cx - rad * 0.45f
                val y0 = cy
                val x1 = cx - rad * 0.1f
                val y1 = cy + rad * 0.35f
                val x2 = cx + rad * 0.5f
                val y2 = cy - rad * 0.35f
                if (q0 < 0.4f) {
                    val q = q0 / 0.4f
                    c.drawLine(x0, y0, x0 + (x1 - x0) * q, y0 + (y1 - y0) * q, pnt)
                } else {
                    val q = (q0 - 0.4f) / 0.6f
                    c.drawLine(x0, y0, x1, y1, pnt)
                    c.drawLine(x1, y1, x1 + (x2 - x1) * q, y1 + (y2 - y1) * q, pnt)
                }
            }
            3 -> {
                val n = 16
                for (i in 0 until n) {
                    val ang = i * 2 * PI / n
                    val d = r * (1.1f + 1.3f * k)
                    c.drawCircle(cx + cos(ang).toFloat() * d, cy + sin(ang).toFloat() * d,
                        r * 0.08f * (1f - k * 0.5f), fillPaint(alpha(col, 1f - k)))
                }
            }
        }
    }
}
