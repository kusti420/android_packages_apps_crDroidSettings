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
import android.graphics.Color
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.android.settings.R
import com.android.settingslib.ichthys.clock.AdaptiveShape
import com.android.settingslib.ichthys.clock.DepthClockView
import com.android.settingslib.ichthys.clock.LockWeather
import com.android.settingslib.ichthys.clock.SubjectOutline
import com.crdroid.settings.fragments.lockscreen.ClockUtils
import kotlin.math.max
import kotlin.math.min

/**
 * IchthysOS lock screen editor v2: where things are on the lock screen, in screen pixels, the way
 * KeyguardCustomClockView places them (and the preview draws them): the face is [faceWidth] wide,
 * its top at [top0] + dy, scaled about its top centre and moved by dx.
 */
class LockGeometry(private val context: Context, val width: Int, val height: Int) {
    val density = context.resources.displayMetrics.density
    val faceWidth = (width - 2 * LockKeys.SIDE_DP * density).toInt()
    val top0 = (LockKeys.STATUS_BAR_DP + LockKeys.BASE_TOP_DP) * density + LockKeys.DEFAULT_MARGIN_TOP
    /** Room kept between a clock and the subject (DepthClockView's SHAPE_GAP_DP). */
    val gap = 16 * density

    /** The Depth face's natural digit block: top and height in the face, half width. */
    class Block(val top: Float, val height: Float, val halfWidth: Float)

    private val blocks = HashMap<String, Block>()
    private val faceHeights = HashMap<Int, Int>()

    fun depthBlock(font: Int, datePos: Int, showDate: Boolean): Block =
        blocks.getOrPut("$font/$datePos/$showDate") {
            val v = DepthClockView(context)
            v.previewDepth = DepthClockView.DEPTH_OFF
            v.previewFont = font
            v.previewDatePos = datePos
            v.setClockOptions(showDate, 0, true)
            v.measure(View.MeasureSpec.makeMeasureSpec(faceWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val r = RectF()
            v.digitBlock(r)
            if (r.isEmpty) Block(30 * density, height * 0.5f, faceWidth * 0.46f)
            else Block(r.top, r.height(), r.width() / 2f)
        }

    /** A face's natural height at [faceWidth] (the picker's own layout, measured). */
    fun faceHeight(style: Int): Int =
        faceHeights.getOrPut(style) {
            try {
                val frame = FrameLayout(context)
                val layout = ClockUtils.CLOCK_LAYOUTS.getOrNull(style) ?: return@getOrPut 300
                LayoutInflater.from(context).inflate(layout, frame, true)
                frame.measure(View.MeasureSpec.makeMeasureSpec(faceWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((height * 0.4f).toInt(), View.MeasureSpec.AT_MOST))
                max(frame.measuredHeight, (60 * density).toInt())
            } catch (e: Exception) {
                (200 * density).toInt()
            }
        }

    /** The screen y of a face's own y, for a design. */
    fun screenY(d: LockDesign, y: Float): Float = top0 + d.dy + y * d.scale

    /**
     * The lowest y the Depth face's digits may reach at scale [s] (moved by [dx]): the subject's top
     * under them less the gap, never below 62 % of the screen. Without a subject: that 62 %.
     */
    fun limit(p: SubjectOutline.Profile?, b: Block, s: Float, dx: Float): Float {
        val cx = width / 2f + dx
        val top = p?.minTop((cx - b.halfWidth * s) / width, (cx + b.halfWidth * s) / width) ?: 1f
        return min(top, 0.62f) * height - gap
    }

    /** The Shape mode's vertical factor for [d] (DepthClockView's Stretch / Tuck). */
    fun shapeFactor(p: SubjectOutline.Profile?, d: LockDesign): Float {
        val b = depthBlock(d.font, d.datePos, d.showDate)
        return AdaptiveShape.stretch(screenY(d, b.top), b.height * d.scale,
            limit(p, b, d.scale, d.dx), d.shape == DepthClockView.SHAPE_TUCK)
    }

    /**
     * The clock size at which the Shape mode's factor comes out near [target] (the clock fills the
     * room above the subject without squashing its digits), 0.5..1.5.
     */
    fun sizeForShape(p: SubjectOutline.Profile?, d: LockDesign, target: Float = 0.9f): Float {
        val b = depthBlock(d.font, d.datePos, d.showDate)
        val tuck = if (d.shape == DepthClockView.SHAPE_TUCK) 1f - AdaptiveShape.TUCK else 1f
        var s = d.scale
        repeat(3) {
            val l = limit(p, b, s, d.dx)
            s = ((l - top0 - d.dy) / (b.top + target * tuck * b.height)).coerceIn(0.5f, 1.5f)
        }
        return s
    }
}

/**
 * IchthysOS lock screen editor v2, "Ideas": many complete lock screens for one picture, from what
 * LockPictures found in it (the subject's outline, its palette, how dark the area above it is).
 * With a subject, every idea keeps the clock in the free area above the subject or behind it (the
 * subject is always drawn in front: no idea has depth off while there is a subject, so none covers
 * a face). Each idea is a [LockDesign]: the editor loads it as its draft, still editable.
 */
object SuggestionEngine {
    class Idea(val title: String, val design: LockDesign)

    /** Compact faces that fit into a band above a head: One UI Stacked, Typographic, … */
    private val COMPACT = intArrayOf(79, 78, 76, 68, 71, 64)

    fun make(context: Context, base: LockDesign, pic: LockPicture, w: Int, h: Int): List<Idea> {
        val g = LockGeometry(context, w, h)
        val p = pic.outline
        val dark = pic.darkBehindClock
        val inks = ArrayList<Int>()
        inks.add(if (dark) Color.rgb(246, 241, 234) else Color.rgb(24, 22, 26))
        inks.addAll(pic.palette.map { LockPictures.readable(it, dark) })
        fun ink(i: Int) = inks[i % inks.size]
        fun s(id: Int) = context.getString(id)

        // Every idea starts from the same plain Depth face, keeping the user's shortcuts and
        // notification options.
        val plain =
            base.copy(
                style = DEPTH_STYLE, fill = DepthClockView.FILL_SOLID,
                depth = DepthClockView.DEPTH_OVERLAP, shape = DepthClockView.SHAPE_STRETCH,
                font = DepthClockView.FONT_TALL, colorMode = LockKeys.MODE_CUSTOM, color = ink(0),
                opacity = 100, scale = 1f, dx = 0f, dy = 0f, rot = 0f, showDate = true,
                details = false, datePos = DepthClockView.DATE_ABOVE,
                dateAlign = DepthClockView.ALIGN_CENTRE, look = "idea",
            )
        val out = ArrayList<Idea>()
        fun shape(title: Int, d: LockDesign) {
            val sized = d.copy(depth = DepthClockView.DEPTH_SHAPE)
            out.add(Idea(s(title), sized.copy(scale = g.sizeForShape(p, sized))))
        }

        if (p == null || p.headTop >= 0.98f) {
            // No subject: plain clocks in the picture's colours, depth off.
            val flat = plain.copy(depth = DepthClockView.DEPTH_OFF)
            out.add(Idea(s(R.string.ichthys_ed2_idea_classic), flat))
            out.add(Idea(s(R.string.ichthys_ed2_idea_gradient),
                flat.copy(fill = DepthClockView.FILL_GRADIENT, color = ink(1))))
            out.add(Idea(s(R.string.ichthys_ed2_idea_thin),
                flat.copy(font = DepthClockView.FONT_THIN, color = ink(2), scale = 0.8f)))
            out.add(Idea(s(R.string.ichthys_ed2_idea_serif),
                flat.copy(font = DepthClockView.FONT_SERIF, color = ink(1), scale = 0.75f)))
            out.add(Idea(s(R.string.ichthys_ed2_idea_heat),
                flat.copy(fill = DepthClockView.FILL_HEAT, details = true)))
            // IchthysOS lock screen weather: today's forecast under the clock.
            out.add(Idea(s(R.string.ichthys_ed2_idea_forecast),
                flat.copy(scale = 0.85f, datePos = DepthClockView.DATE_BELOW, weather = true,
                    weatherPos = LockWeather.POS_UNDER, weatherTemp = true, weatherCondition = true,
                    weatherHighLow = true, color = ink(1))))
            COMPACT.forEachIndexed { i, style ->
                out.add(Idea(ClockUtils.getClockNames()[style],
                    flat.copy(style = style, color = ink(i + 1), dy = 60 * g.density)))
            }
            return out
        }

        val head = p.headTop * h
        val b = g.depthBlock(DepthClockView.FONT_TALL, DepthClockView.DATE_ABOVE, true)
        // Overlap with the head hiding about a third of the digits (One UI's depth look).
        val peek = ((head - g.top0) / (b.top + 0.65f * b.height)).coerceIn(0.5f, 1f)

        shape(R.string.ichthys_ed2_idea_stretch, plain)
        shape(R.string.ichthys_ed2_idea_tuck, plain.copy(shape = DepthClockView.SHAPE_TUCK, color = ink(1)))
        shape(R.string.ichthys_ed2_idea_follow, plain.copy(shape = DepthClockView.SHAPE_FOLLOW))
        out.add(Idea(s(R.string.ichthys_ed2_idea_behind), plain.copy(scale = peek)))
        out.add(Idea(s(R.string.ichthys_ed2_idea_xray),
            plain.copy(depth = DepthClockView.DEPTH_XRAY, fill = DepthClockView.FILL_HEAT,
                scale = peek)))
        shape(R.string.ichthys_ed2_idea_gradient,
            plain.copy(fill = DepthClockView.FILL_GRADIENT, color = ink(1)))
        shape(R.string.ichthys_ed2_idea_thin, plain.copy(font = DepthClockView.FONT_THIN, color = ink(2)))
        shape(R.string.ichthys_ed2_idea_serif,
            plain.copy(font = DepthClockView.FONT_SERIF, shape = DepthClockView.SHAPE_FOLLOW,
                color = ink(3)))
        // The deep look: full size, the subject over most of the minutes, X-ray to see through.
        if (head < g.screenY(plain, b.top + b.height)) {
            out.add(Idea(s(R.string.ichthys_ed2_idea_deep),
                plain.copy(depth = DepthClockView.DEPTH_XRAY)))
        }
        shape(R.string.ichthys_ed2_idea_mono,
            plain.copy(font = DepthClockView.FONT_MONO, shape = DepthClockView.SHAPE_TUCK, color = ink(2)))
        out.add(Idea(s(R.string.ichthys_ed2_idea_wide),
            plain.copy(font = DepthClockView.FONT_WIDE, color = ink(3), scale = peek * 0.9f)))
        shape(R.string.ichthys_ed2_idea_date_below, plain.copy(datePos = DepthClockView.DATE_BELOW))
        shape(R.string.ichthys_ed2_idea_details, plain.copy(details = true, color = ink(1)))
        // IchthysOS lock screen weather: in the date line (One UI's), or a forecast in the top
        // corner, clear of the subject.
        shape(R.string.ichthys_ed2_idea_weather,
            plain.copy(weather = true, weatherPos = LockWeather.POS_DATE, weatherTemp = true,
                weatherCondition = false, weatherHighLow = false))
        shape(R.string.ichthys_ed2_idea_forecast,
            plain.copy(shape = DepthClockView.SHAPE_TUCK, color = ink(2), weather = true,
                weatherPos = LockWeather.POS_TOP_START, weatherTemp = true, weatherCondition = true,
                weatherHighLow = true))

        // Compact faces in the band above the head, a little below its top edge when there is room.
        val band = head - g.gap - g.top0
        COMPACT.forEachIndexed { i, style ->
            val fh = g.faceHeight(style).toFloat()
            val sc = (band / fh).coerceIn(0.5f, 1.5f)
            val dy = max(0f, (band - fh * sc) * 0.4f)
            out.add(Idea(ClockUtils.getClockNames()[style],
                plain.copy(style = style, scale = sc, dy = dy, color = ink(i))))
        }

        // An off-centre subject leaves a side free: a smaller clock there.
        val span = p.span()
        if (span != null) {
            val centre = (span.first + span.second) / 2f
            if (centre < 0.42f || centre > 0.58f) {
                val right = centre < 0.5f
                val dx = (if (right) 1 else -1) * w * 0.2f
                val side =
                    plain.copy(dx = dx, scale = 0.55f, dy = 40 * g.density,
                        dateAlign = if (right) DepthClockView.ALIGN_END else DepthClockView.ALIGN_START)
                out.add(Idea(s(R.string.ichthys_ed2_idea_side), side))
                out.add(Idea(s(R.string.ichthys_ed2_idea_side),
                    plain.copy(style = 79, dx = dx * 0.8f, scale = 0.8f, color = ink(1))))
            }
        }
        return out
    }
}
