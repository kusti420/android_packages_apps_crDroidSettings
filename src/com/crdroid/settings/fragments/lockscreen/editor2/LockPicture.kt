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

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.android.settingslib.ichthys.clock.DepthSource
import com.android.settingslib.ichthys.clock.SubjectOutline
import com.android.settingslib.ichthys.sky.LivingSky
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * IchthysOS lock screen editor v2: one picture as the lock screen would show it, and what the
 * editor knows about it.
 * - [backdrop]: the picture cut to the screen's aspect ratio, at half the screen's size.
 * - [cutout]: only the subject (IchthysDepth's mask), drawn over the clock in the preview.
 * - [outline]: the subject's top outline in full screen pixels (the Shape mode, the Ideas).
 * - [palette]: colours the clock can take from the picture; [darkBehindClock]: the area above the
 *   subject is dark, so light ink reads there.
 * - IchthysOS Living Sky: [sky] (the sky's coverage in alpha, the backdrop's size), [land] (the
 *   rest, for the weather's far layers) and [skyInfo] (whether Living Sky can grade it, and how);
 *   null while IchthysDepth hasn't found the sky yet.
 *
 * Never changed once made (a new picture is a new object), so Compose can skip the previews that
 * show it while only the design changes (the sheets' many small previews during a drag).
 */
@Immutable
class LockPicture(
    val backdrop: ImageBitmap?,
    val cutout: ImageBitmap?,
    val outline: SubjectOutline.Profile?,
    val palette: List<Int>,
    val darkBehindClock: Boolean,
    /** The half-size picture itself, for the Ideas' segmentation of a not-yet-wallpaper. */
    val source: Bitmap?,
    val sky: Bitmap? = null,
    val land: Bitmap? = null,
    val skyInfo: LivingSky.SkyInfo? = null,
    /** The subject's coverage (alpha), the backdrop's size, for the graded cut-out. */
    val cutoutMask: Bitmap? = null,
) {
    /** Living Sky can grade this picture. */
    val livingSky: Boolean
        get() = sky != null && source != null && skyInfo?.hasSky == true

    companion object {
        val EMPTY = LockPicture(null, null, null, emptyList(), true, null)
    }
}

object LockPictures {
    private const val TAG = "LockEditor2"
    private const val MASKS = "content://org.ichthys.depth/mask"
    private const val SEGMENT = "content://org.ichthys.depth.segment"
    /** IchthysOS Living Sky: IchthysDepth's sky masks (MaskProvider), like [MASKS]. */
    private const val SKIES = LivingSky.SKY_URI

    /**
     * The current lock wallpaper (or the home one when the lock screen has none). Its mask comes
     * from IchthysDepth's MaskProvider; while it isn't made yet, the picture has no cut-out (the
     * provider starts making it, and notifies [MASKS]/<user>). Blocking.
     */
    fun current(context: Context, w: Int, h: Int): LockPicture =
        try {
            val user = UserHandle.myUserId()
            val wm = context.getSystemService(WallpaperManager::class.java)!!
            val lockId = wm.getWallpaperIdForUser(WallpaperManager.FLAG_LOCK, user)
            val shown = if (lockId > 0) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
            if (wm.getWallpaperInfo(shown, user) != null) {
                // A live wallpaper: no picture to show or cut.
                LockPicture.EMPTY
            } else {
                val pic =
                    wm.getBitmapAsUser(user, false, WallpaperManager.FLAG_LOCK, false)
                        ?: wm.getBitmapAsUser(user, false, WallpaperManager.FLAG_SYSTEM, true)
                if (pic == null) LockPicture.EMPTY
                else {
                    val region = centerCrop(pic.width, pic.height, w, h)
                    val back = crop(pic, region, w / 2, h / 2)
                    val key = "L$lockId" + "S" +
                        wm.getWallpaperIdForUser(WallpaperManager.FLAG_SYSTEM, user)
                    fun open(base: String): Bitmap? =
                        try {
                            context.contentResolver
                                .openFileDescriptor(Uri.parse("$base/$user/$key"), "r")
                                ?.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) }
                        } catch (e: Exception) {
                            null
                        }
                    fun cut(m: Bitmap): Bitmap {
                        val sx = m.width.toFloat() / pic.width
                        val sy = m.height.toFloat() / pic.height
                        return crop(m, Rect((region.left * sx).toInt(), (region.top * sy).toInt(),
                            (region.right * sx).toInt(), (region.bottom * sy).toInt()),
                            w / 2, h / 2)
                    }
                    val cutMask = open(MASKS)?.let { cut(it) }
                    // Opening it also starts it (MaskProvider notifies SKIES/<user> when made).
                    val skyMask =
                        if (LivingSky.readOptions(context.contentResolver, user).enabled ||
                            wantSky) open(SKIES)?.let { cut(it) } else null
                    build(back, cutMask, w, h, skyMask)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "lock wallpaper not loaded", e)
            LockPicture.EMPTY
        }

    /** A photo from the photo picker, cut to the screen; no mask yet ([withSubject] adds it). */
    fun photo(context: Context, uri: Uri, w: Int, h: Int): LockPicture =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= w / 2) sample *= 2
            val pic =
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                }
            if (pic == null) LockPicture.EMPTY
            else build(crop(pic, centerCrop(pic.width, pic.height, w, h), w / 2, h / 2), null, w, h)
        } catch (e: Exception) {
            Log.w(TAG, "photo not loaded", e)
            LockPicture.EMPTY
        }

    /** One of the painted wallpapers. */
    fun builtin(context: Context, res: Int, w: Int, h: Int): LockPicture =
        try {
            val pic = BitmapFactory.decodeResource(context.resources, res)
            build(crop(pic, centerCrop(pic.width, pic.height, w, h), w / 2, h / 2), null, w, h)
        } catch (e: Exception) {
            LockPicture.EMPTY
        }

    /**
     * IchthysOS Living Sky: ask for the current wallpaper's sky even while Living Sky is off (the
     * editor's Wallpaper sheet, to say whether the photo has one). Set before [current].
     */
    @Volatile var wantSky = false

    /**
     * [picture] with its subject found by IchthysDepth's segment call (U²-Netp, ~4 s), and, IchthysOS
     * Living Sky, its sky (the "sky" call returns both from one run). The same picture without a
     * cut-out when IchthysDepth can't (missing, or no subject). Blocking.
     */
    fun withSubject(context: Context, picture: LockPicture, w: Int, h: Int): LockPicture {
        // The current wallpaper's masks come from MaskProvider (its sky too, see [wantSky]).
        if (picture.cutout != null) return picture
        val src = picture.source ?: return picture
        return try {
            val small = scaleToLongSide(src, 640)
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 90, out)
            val image = out.toByteArray()
            fun call(method: String): Bundle? =
                context.contentResolver.call(Uri.parse(SEGMENT), method, null,
                    Bundle().apply { putByteArray("image", image) })
            fun decode(bytes: ByteArray?): Bitmap? =
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    ?.let { Bitmap.createScaledBitmap(it, src.width, src.height, true) }
            // An IchthysDepth without the sky call answers null: the subject alone then.
            val both = try { call("sky") } catch (e: Exception) { null }
            val mask: Bitmap?
            val sky: Bitmap?
            if (both != null) {
                mask = decode(both.getByteArray("subject"))
                sky = decode(both.getByteArray("mask"))
            } else {
                mask = decode(call("segment")?.getByteArray("mask"))
                sky = null
            }
            if (mask == null && sky == null) return picture
            build(src, mask, w, h, sky)
        } catch (e: Exception) {
            Log.w(TAG, "no subject from IchthysDepth", e)
            picture
        }
    }

    /**
     * Cut-out, outline and palette for [back] (half screen size) and its [mask] (same size, alpha =
     * subject; null: none). A mask covering almost nothing counts as none, like SystemUI's.
     */
    private fun build(back: Bitmap, mask: Bitmap?, w: Int, h: Int, sky: Bitmap? = null): LockPicture {
        var outline = mask?.let { SubjectOutline.fromMask(it, w, h) }
        var cut: Bitmap? = null
        // IchthysOS: the same test SystemUI's lock screen makes (DepthSource): only a real
        // subject stands in front of the clock.
        val real = mask != null && DepthSource.isRealSubject(mask, back).first
        if (mask != null && outline != null && coverage(mask) >= 0.01f && real) {
            cut = Bitmap.createBitmap(back.width, back.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(cut)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            c.drawBitmap(mask, null, Rect(0, 0, back.width, back.height), paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(back, 0f, 0f, paint)
        } else {
            outline = null
        }
        // IchthysOS Living Sky: the sky as SystemUI sees it (LivingSky.analyse), and the land.
        val skyInfo = sky?.let { LivingSky.analyse(back, it) }
        // No real subject but a crisp skyline: the clock stays above it, as on the lock screen.
        if (outline == null && sky != null && skyInfo?.hasSky == true) {
            outline = DepthSource.skyline(sky, back, w, h)
        }
        val dark = darkAbove(back, outline)
        val land =
            sky?.takeIf { skyInfo?.hasSky == true }?.let {
                Bitmap.createBitmap(back.width, back.height, Bitmap.Config.ALPHA_8).also { l ->
                    val c = Canvas(l)
                    c.drawColor(Color.BLACK)
                    c.drawBitmap(it, null, Rect(0, 0, back.width, back.height),
                        Paint(Paint.FILTER_BITMAP_FLAG).apply {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                        })
                }
            }
        return LockPicture(
            back.asImageBitmap(),
            cut?.asImageBitmap(),
            outline,
            palette(back, mask, dark),
            dark,
            back,
            sky = sky,
            land = land,
            skyInfo = skyInfo,
            cutoutMask = cut,
        )
    }

    private fun coverage(mask: Bitmap): Float {
        val small = Bitmap.createScaledBitmap(mask, 54, 120, true)
        val px = IntArray(54 * 120)
        small.getPixels(px, 0, 54, 0, 0, 54, 120)
        return px.count { (it ushr 24) > 128 }.toFloat() / px.size
    }

    /**
     * Colours for the clock: the subject's most vivid colours first (what a person would pick from
     * the photo), then the picture's main colours (WallpaperColors), lightened or darkened to read
     * on the area behind the clock. At most six, none too close to another.
     */
    private fun palette(back: Bitmap, mask: Bitmap?, dark: Boolean): List<Int> {
        val out = ArrayList<Int>()
        val small = Bitmap.createScaledBitmap(back, 54, 120, true)
        val px = IntArray(54 * 120)
        small.getPixels(px, 0, 54, 0, 0, 54, 120)
        val m =
            mask?.let {
                val s = Bitmap.createScaledBitmap(it, 54, 120, true)
                IntArray(54 * 120).also { a -> s.getPixels(a, 0, 54, 0, 0, 54, 120) }
            }
        // Vivid colours, weighted by saturation, inside the subject when there is one.
        val bins = HashMap<Int, FloatArray>()
        val hsv = FloatArray(3)
        for (i in px.indices) {
            if (m != null && (m[i] ushr 24) < 128) continue
            Color.colorToHSV(px[i], hsv)
            if (hsv[1] < 0.25f || hsv[2] < 0.3f) continue
            val bin = (hsv[0] / 20f).toInt()
            val b = bins.getOrPut(bin) { FloatArray(4) }
            val wgt = hsv[1] * hsv[2]
            b[0] += wgt
            b[1] += Color.red(px[i]) * wgt
            b[2] += Color.green(px[i]) * wgt
            b[3] += Color.blue(px[i]) * wgt
        }
        bins.values.sortedByDescending { it[0] }.take(3).forEach {
            out.add(Color.rgb((it[1] / it[0]).toInt(), (it[2] / it[0]).toInt(), (it[3] / it[0]).toInt()))
        }
        try {
            val wc = WallpaperColors.fromBitmap(back)
            listOfNotNull(wc.primaryColor, wc.secondaryColor, wc.tertiaryColor).forEach {
                out.add(it.toArgb())
            }
        } catch (e: Exception) {
            // No main colours: the vivid ones do.
        }
        val tuned = ArrayList<Int>()
        for (c in out) {
            val t = readable(c, dark)
            if (tuned.none { distance(it, t) < 60 }) tuned.add(t)
        }
        return tuned.take(6)
    }

    /** [c] moved to a lightness that reads on a dark ([dark]) or light background. */
    fun readable(c: Int, dark: Boolean): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        if (dark) {
            // A light tint of the colour: bright, not fully saturated.
            hsv[2] = max(hsv[2], 0.96f)
            hsv[1] = min(hsv[1], 0.42f)
        } else {
            hsv[2] = min(hsv[2], 0.3f)
        }
        return Color.HSVToColor(hsv)
    }

    private fun distance(a: Int, b: Int): Int =
        kotlin.math.abs(Color.red(a) - Color.red(b)) + kotlin.math.abs(Color.green(a) - Color.green(b)) +
            kotlin.math.abs(Color.blue(a) - Color.blue(b))

    /** Whether the area the clock sits in (above the subject, or the top third) is dark. */
    private fun darkAbove(back: Bitmap, outline: SubjectOutline.Profile?): Boolean {
        val bottom =
            ((outline?.headTop ?: 0.35f).coerceIn(0.15f, 0.6f) * back.height).roundToInt()
        val top = (back.height * 0.08f).roundToInt()
        if (bottom <= top) return true
        val strip = Bitmap.createScaledBitmap(
            Bitmap.createBitmap(back, 0, top, back.width, bottom - top), 32, 16, true)
        val px = IntArray(32 * 16)
        strip.getPixels(px, 0, 32, 0, 0, 32, 16)
        var lum = 0f
        for (p in px) {
            lum += 0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)
        }
        return lum / px.size / 255f < 0.55f
    }

    private fun crop(src: Bitmap, region: Rect, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, region, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun scaleToLongSide(src: Bitmap, side: Int): Bitmap {
        val k = side.toFloat() / max(src.width, src.height)
        if (k >= 1f) return src
        return Bitmap.createScaledBitmap(src, (src.width * k).roundToInt(),
            (src.height * k).roundToInt(), true)
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
