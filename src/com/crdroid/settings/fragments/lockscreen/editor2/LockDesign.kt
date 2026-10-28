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

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.internal.util.crdroid.ThemeUtils
import com.android.settings.R
import com.android.settingslib.ichthys.clock.DepthClockView
import com.android.settingslib.ichthys.clock.LockWeather
import com.android.settingslib.ichthys.sky.LivingSky
import com.crdroid.settings.utils.SystemUtils
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/*
 * IchthysOS lock screen editor v2 (kusti420/ichthysos-tracker#58): everything the editor can change,
 * as one value. The editor works on a copy (the draft) and writes it only on Apply, so Cancel
 * leaves the lock screen exactly as it was. Every field is a Settings key the lock screen already
 * reads (KeyguardCustomClockView, DepthClockView, IchthysDepthRepository, SystemUI's shortcuts).
 */

/** The Depth face's index (ClockUtils.CLOCK_LAYOUTS, KeyguardCustomClockView.LAYOUT_NAMES). */
const val DEPTH_STYLE = 80

/** Settings.Secure keys. */
object LockKeys {
    const val STYLE = Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE
    const val COLOR_MODE = "lock_screen_custom_clock_color_mode"
    const val CUSTOM_COLOR = "lock_screen_custom_clock_custom_color"
    const val OPACITY = "lock_screen_custom_clock_opacity"
    const val SCALE = "lock_screen_custom_clock_size_scale"
    const val MARGIN_TOP = "lock_screen_custom_clock_margin_top"
    const val OFFSET_X = "ichthys_clock_offset_x"
    const val ROTATION = "ichthys_clock_rotation"
    const val SHOW_DATE = "ichthys_clock_show_date"
    const val FILL = DepthClockView.KEY_FILL
    const val DETAILS = DepthClockView.KEY_DETAILS
    const val DEPTH = DepthClockView.KEY_DEPTH
    const val SHAPE_STYLE = DepthClockView.KEY_SHAPE_STYLE
    const val FONT = DepthClockView.KEY_FONT
    const val GLASS_FROST = DepthClockView.KEY_GLASS_FROST
    const val GLASS_OPACITY = DepthClockView.KEY_GLASS_OPACITY
    const val DATE_POS = DepthClockView.KEY_DATE_POS
    const val DATE_ALIGN = DepthClockView.KEY_DATE_ALIGN
    const val LOOK = "ichthys_lockscreen_look"
    const val LOOKS = "ichthys_lockscreen_looks"
    const val SHOW_NOTIFICATIONS = "lock_screen_show_notifications"
    const val PRIVATE_NOTIFICATIONS = "lock_screen_allow_private_notifications"

    const val MODE_DEFAULT = "default"
    const val MODE_ACCENT = "accent"
    const val MODE_CUSTOM = "custom"

    /** KeyguardCustomClockView's defaults: the clock's top padding and its margin setting. */
    const val BASE_TOP_DP = 72
    const val DEFAULT_MARGIN_TOP = 15
    /** The status bar the preview assumes above the clock's box. */
    const val STATUS_BAR_DP = 40
    /**
     * The clock face's distance from the screen edge on the real lock screen: the upper
     * region's padding (notification_side_paddings_single + notification_panel_margin_horizontal,
     * 16 dp on this device) plus KeyguardCustomClockView's own 24 dp. With 24 alone the preview's
     * face came out 32 dp wider than on the phone.
     */
    const val SIDE_DP = 40
}

/**
 * One whole lock screen. Offsets are in screen pixels, like the settings that hold them; [scale] is
 * 0.5..1.5, [rot] degrees. [shortcutStart] / [shortcutEnd] are SystemUI affordance ids ("" none).
 */
data class LockDesign(
    val style: Int = DEPTH_STYLE,
    val fill: Int = DepthClockView.FILL_SOLID,
    val depth: Int = DepthClockView.DEPTH_OVERLAP,
    val shape: Int = DepthClockView.SHAPE_STRETCH,
    val font: Int = DepthClockView.FONT_ROUNDED,
    /** The glass finish: clear (0) or frosted (1), and Apple's opacity slider (-1 auto, 0..100). */
    val glassFrost: Int = 0,
    val glassOpacity: Int = -1,
    val colorMode: String = LockKeys.MODE_DEFAULT,
    val color: Int = Color.WHITE,
    val opacity: Int = 100,
    val scale: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rot: Float = 0f,
    val showDate: Boolean = true,
    val details: Boolean = false,
    val datePos: Int = DepthClockView.DATE_ABOVE,
    val dateAlign: Int = DepthClockView.ALIGN_CENTRE,
    val shortcutStart: String = "",
    val shortcutEnd: String = "",
    val showNotifications: Boolean = true,
    val privateNotifications: Boolean = true,
    val look: String = "custom",
    // IchthysOS lock screen weather (LockWeather's keys).
    val weather: Boolean = false,
    val weatherPos: Int = LockWeather.POS_DATE,
    val weatherTemp: Boolean = true,
    val weatherCondition: Boolean = true,
    val weatherLocation: Boolean = false,
    val weatherHighLow: Boolean = false,
    val weatherIcons: String = LockWeather.ICONS_FOLLOW,
    val weatherSize: Int = 100,
    /** Not edited here (the Weather page has them); kept so previews match the lock screen. */
    val weatherWind: Boolean = false,
    val weatherHumidity: Boolean = false,
    // IchthysOS Living Sky (LivingSky's keys): the photo's sky follows the time and the weather.
    val livingSky: Boolean = false,
    val skyWeather: Boolean = true,
    val skyIntensity: Int = 100,
) {
    val isDepthFace: Boolean
        get() = style == DEPTH_STYLE

    /** The weather options as the lock screen reads them. */
    val weatherOptions: LockWeather.Options
        get() = LockWeather.Options(
            enabled = weather, temp = weatherTemp, condition = weatherCondition,
            location = weatherLocation, highLow = weatherHighLow, wind = weatherWind,
            humidity = weatherHumidity, icons = weatherIcons, size = weatherSize, pos = weatherPos)

    /** Where the weather really shows (the date-line place only on the Depth face's date). */
    val weatherPlace: Int
        get() = LockWeather.placeFor(weatherOptions, isDepthFace, showDate)

    /** The clock colour as the faces get it (null: the face's own colours). */
    fun inkOrNull(accent: Int): Int? =
        when (colorMode) {
            LockKeys.MODE_CUSTOM -> color
            LockKeys.MODE_ACCENT -> accent
            else -> null
        }

    /** Same placement, look and clock (ignores shortcuts, notifications and the look's id). */
    fun sameClockAs(o: LockDesign): Boolean =
        copy(shortcutStart = "", shortcutEnd = "", showNotifications = true,
            privateNotifications = true, look = "", dx = 0f, dy = 0f, scale = 1f, rot = 0f) ==
            o.copy(shortcutStart = "", shortcutEnd = "", showNotifications = true,
                privateNotifications = true, look = "", dx = 0f, dy = 0f, scale = 1f, rot = 0f) &&
            abs(dx - o.dx) < 1 && abs(dy - o.dy) < 1 && abs(scale - o.scale) < 0.005f &&
            abs(rot - o.rot) < 0.5f

    /** The parts a saved look keeps (not shortcuts or notification options). */
    fun toJson(id: String, label: String): JSONObject =
        JSONObject()
            .put("id", id)
            .put("label", label)
            .put("style", style)
            .put("fill", fill)
            .put("depth", depth)
            .put("shape", shape)
            .put("font", font)
            .put("glassFrost", glassFrost)
            .put("glassOpacity", glassOpacity)
            .put("colorMode", colorMode)
            .put("color", color)
            .put("opacity", opacity)
            .put("dx", dx.toDouble())
            .put("dy", dy.toDouble())
            .put("scale", scale.toDouble())
            .put("rot", rot.toDouble())
            .put("showDate", showDate)
            .put("details", details)
            .put("datePos", datePos)
            .put("dateAlign", dateAlign)
            .put("weather", weather)
            .put("weatherPos", weatherPos)
            .put("weatherTemp", weatherTemp)
            .put("weatherCondition", weatherCondition)
            .put("weatherLocation", weatherLocation)
            .put("weatherHighLow", weatherHighLow)
            .put("weatherIcons", weatherIcons)
            .put("weatherSize", weatherSize)
            .put("livingSky", livingSky)

    /** [o] (a saved look; v1 looks have only the first few fields) over this design. */
    fun withLook(o: JSONObject): LockDesign =
        copy(
            style = o.optInt("style", style),
            fill = o.optInt("fill", fill),
            depth = o.optInt("depth", depth),
            shape = o.optInt("shape", DepthClockView.SHAPE_STRETCH),
            font = o.optInt("font", DepthClockView.FONT_TALL),
            glassFrost = o.optInt("glassFrost", 0),
            glassOpacity = o.optInt("glassOpacity", -1),
            colorMode = o.optString("colorMode", colorMode),
            color = o.optInt("color", color),
            opacity = o.optInt("opacity", 100),
            dx = o.optDouble("dx", 0.0).toFloat(),
            dy = o.optDouble("dy", 0.0).toFloat(),
            scale = o.optDouble("scale", 1.0).toFloat(),
            rot = o.optDouble("rot", 0.0).toFloat(),
            showDate = o.optBoolean("showDate", true),
            details = o.optBoolean("details", false),
            datePos = o.optInt("datePos", DepthClockView.DATE_ABOVE),
            dateAlign = o.optInt("dateAlign", DepthClockView.ALIGN_CENTRE),
            look = o.optString("id", "custom"),
            // Looks saved before the weather keep the weather as it is.
            weather = o.optBoolean("weather", weather),
            weatherPos = o.optInt("weatherPos", weatherPos),
            weatherTemp = o.optBoolean("weatherTemp", weatherTemp),
            weatherCondition = o.optBoolean("weatherCondition", weatherCondition),
            weatherLocation = o.optBoolean("weatherLocation", weatherLocation),
            weatherHighLow = o.optBoolean("weatherHighLow", weatherHighLow),
            weatherIcons = o.optString("weatherIcons", weatherIcons),
            weatherSize = o.optInt("weatherSize", weatherSize),
            // Looks saved before Living Sky keep it as it is.
            livingSky = o.optBoolean("livingSky", livingSky),
        )
}

/** A saved look: its id, its name, and the JSON [LockDesign.withLook] reads. */
class SavedLook(val id: String, val label: String, val json: JSONObject)

/** What the lock wallpaper should become on Apply. */
sealed class WallpaperDraft {
    /** Leave it. */
    object Current : WallpaperDraft()

    /** A photo from the photo picker ([home]: the home screen's too). */
    data class Photo(val uri: Uri, val home: Boolean) : WallpaperDraft()

    /** One of the painted depth wallpapers (see [BUILTIN_WALLPAPERS]). */
    data class Builtin(val id: String) : WallpaperDraft()
}

/**
 * Wallpapers that come with IchthysOS: id, picture (1080 x 2400), name. The koi pond is painted
 * for the depth lock screen; the photos are the ones the lock screen concepts were made with.
 */
val BUILTIN_WALLPAPERS =
    listOf(
        Triple("koi", R.drawable.ichthys_wallpaper_koi, R.string.ichthys_wallpaper_koi),
        Triple("peyto_lake", R.drawable.ichthys_wall_peyto_lake, R.string.ichthys_wall_peyto_lake),
        Triple("dog_rock_sky", R.drawable.ichthys_wall_dog_rock_sky, R.string.ichthys_wall_dog_rock_sky),
        Triple("lighthouse_white", R.drawable.ichthys_wall_lighthouse_white,
            R.string.ichthys_wall_lighthouse_white),
        Triple("woman_red_sky", R.drawable.ichthys_wall_woman_red_sky,
            R.string.ichthys_wall_woman_red_sky),
        Triple("man_white_shirt", R.drawable.ichthys_wall_man_white_shirt,
            R.string.ichthys_wall_man_white_shirt),
        Triple("sea_stack", R.drawable.ichthys_wall_sea_stack, R.string.ichthys_wall_sea_stack),
        Triple("treeline_lake", R.drawable.ichthys_wall_treeline_lake,
            R.string.ichthys_wall_treeline_lake),
        Triple("ridge_haze", R.drawable.ichthys_wall_ridge_haze, R.string.ichthys_wall_ridge_haze),
        Triple("sunset_paddy", R.drawable.ichthys_wall_sunset_paddy,
            R.string.ichthys_wall_sunset_paddy),
        Triple("quiraing", R.drawable.ichthys_wall_quiraing, R.string.ichthys_wall_quiraing),
        Triple("green_hill", R.drawable.ichthys_wall_green_hill, R.string.ichthys_wall_green_hill),
        Triple("sky_meadows", R.drawable.ichthys_wall_sky_meadows, R.string.ichthys_wall_sky_meadows),
        Triple("light_cloud", R.drawable.ichthys_wall_light_cloud, R.string.ichthys_wall_light_cloud),
        Triple("city_skyline", R.drawable.ichthys_wall_city_skyline,
            R.string.ichthys_wall_city_skyline),
        Triple("toronto_sunset", R.drawable.ichthys_wall_toronto_sunset,
            R.string.ichthys_wall_toronto_sunset),
        Triple("vintage_car_night", R.drawable.ichthys_wall_vintage_car_night,
            R.string.ichthys_wall_vintage_car_night),
        Triple("fluid_abstract", R.drawable.ichthys_wall_fluid_abstract,
            R.string.ichthys_wall_fluid_abstract),
        Triple("copper_lines", R.drawable.ichthys_wall_copper_lines,
            R.string.ichthys_wall_copper_lines),
    )

/**
 * The photos' credits (title, author, licence, source), shown by the Wallpaper sheet. The Creative
 * Commons ones must be credited; the CC BY-SA crops are shared under the same licence. Names and
 * licences stay untranslated.
 */
val WALLPAPER_CREDITS =
    listOf(
        "Peyto Lake in winter: Jakub Fry\u0161, CC BY-SA 4.0 (cropped). commons.wikimedia.org/wiki/File:Peyto_Lake_in_winter.jpg",
        "Trees rising out of Cheow Lan Lake: Vyacheslav Argenberg, CC BY 4.0 (cropped). commons.wikimedia.org/wiki/File:Trees_rising_out_of_Cheow_Lan_Lake,_blue_sky,_eternal_summer_in_Surat_Thani.jpg",
        "Hurricane Ridge, Olympic National Park: Andreas Geick, CC BY-SA 3.0 (cropped). commons.wikimedia.org/wiki/File:Hurricane_Ridge,_Olympic_National_Park._-_panoramio.jpg",
        "Colorful sky over a paddy field, Vang Vieng: Basile Morin, CC BY-SA 4.0 (cropped). commons.wikimedia.org/wiki/File:Colorful_sky_with_orange_clouds_reflecting_in_the_water_of_a_paddy_field,_at_sunset,_Vang_Vieng,_Laos.jpg",
        "A city skyline (DFC 2050): PattayaPatrol, CC BY-SA 4.0 (cropped). commons.wikimedia.org/wiki/File:DFC_2050_A_city_skyline_with_a_mix_of_low-rise_buildings_and_a_tall_modern_tower_rising_above_leafy_green_trees_under_a_partly_cloudy_sky.jpg",
        "Light Breaking Thru over Piedmont: Virginia State Parks (vastateparksstaff), CC BY 2.0 (cropped). commons.wikimedia.org/wiki/File:Light_Breaking_Thru_over_Piedmont_(33113583426).jpg",
        "Touch the Sky at Sky Meadows: Virginia State Parks (vastateparksstaff), CC BY 2.0 (cropped). commons.wikimedia.org/wiki/File:Touch_the_Sky_at_Sky_Meadows_(32708244180).jpg",
        "Quiraing valley: Unsplash License. unsplash.com/photos/bCm0kpA5tm4",
        "Green hill: Pexels License. pexels.com/photo/23383399",
        "Toronto sunset: Pexels License. pexels.com/photo/19733715",
        "Young woman against blue sky: Pexels License. pexels.com/photo/4946635",
        "Man in white dress shirt: cottonbro studio, Pexels License. pexels.com/photo/4886787",
        "An Australian shepherd under the blue sky: Jeanpaul Lienard, Pexels License. pexels.com/photo/9923868",
        "Lighthouse under a blue sky: Pexels License. pexels.com/photo/11954484",
        "Dun Briste sea stack, Co. Mayo: Pexels License. pexels.com/photo/12530564",
        "Low angle shot of a vintage car: Erik Mclean, Pexels License. pexels.com/photo/9592414",
        "Koi pond, Fluid and Copper lines: made for IchthysOS.",
    )

object LockStore {
    private const val TAG = "LockEditor2"

    private fun int(context: Context, key: String, def: Int) =
        Settings.Secure.getIntForUser(context.contentResolver, key, def, UserHandle.USER_CURRENT)

    private fun str(context: Context, key: String): String? =
        Settings.Secure.getStringForUser(context.contentResolver, key, UserHandle.USER_CURRENT)

    private fun put(context: Context, key: String, value: Int) {
        Settings.Secure.putIntForUser(context.contentResolver, key, value, UserHandle.USER_CURRENT)
    }

    private fun put(context: Context, key: String, value: String) {
        Settings.Secure.putStringForUser(context.contentResolver, key, value, UserHandle.USER_CURRENT)
    }

    /** crDroid's weather keys are Settings.System ones. */
    private fun putSys(context: Context, key: String, value: Int) {
        Settings.System.putIntForUser(context.contentResolver, key, value, UserHandle.USER_CURRENT)
    }

    /** The lock screen as the settings have it now ([start] / [end]: the shortcuts SystemUI has). */
    fun read(context: Context, start: String = "", end: String = ""): LockDesign {
        val margin = int(context, LockKeys.MARGIN_TOP, LockKeys.DEFAULT_MARGIN_TOP)
        val w = LockWeather.readOptions(context)
        val sky = LivingSky.readOptions(context.contentResolver, UserHandle.USER_CURRENT)
        return LockDesign(
            style = int(context, LockKeys.STYLE, 0),
            fill = int(context, LockKeys.FILL, DepthClockView.FILL_SOLID),
            depth = int(context, LockKeys.DEPTH, DepthClockView.DEPTH_OFF),
            shape = int(context, LockKeys.SHAPE_STYLE, DepthClockView.SHAPE_STRETCH),
            font = int(context, LockKeys.FONT, DepthClockView.FONT_ROUNDED),
            glassFrost = int(context, LockKeys.GLASS_FROST, 0),
            glassOpacity = int(context, LockKeys.GLASS_OPACITY, -1),
            colorMode = str(context, LockKeys.COLOR_MODE) ?: LockKeys.MODE_DEFAULT,
            color = int(context, LockKeys.CUSTOM_COLOR, Color.WHITE),
            opacity = int(context, LockKeys.OPACITY, 100),
            scale = int(context, LockKeys.SCALE, 100).coerceIn(40, 180) / 100f,
            dx = int(context, LockKeys.OFFSET_X, 0).toFloat(),
            dy = (margin - LockKeys.DEFAULT_MARGIN_TOP).toFloat(),
            rot = int(context, LockKeys.ROTATION, 0).toFloat(),
            showDate = int(context, LockKeys.SHOW_DATE, 1) != 0,
            details = int(context, LockKeys.DETAILS, 0) != 0,
            datePos = int(context, LockKeys.DATE_POS, DepthClockView.DATE_ABOVE),
            dateAlign = int(context, LockKeys.DATE_ALIGN, DepthClockView.ALIGN_CENTRE),
            shortcutStart = start,
            shortcutEnd = end,
            showNotifications = int(context, LockKeys.SHOW_NOTIFICATIONS, 1) != 0,
            privateNotifications = int(context, LockKeys.PRIVATE_NOTIFICATIONS, 1) != 0,
            look = str(context, LockKeys.LOOK) ?: "custom",
            weather = w.enabled,
            weatherPos = w.pos,
            weatherTemp = w.temp,
            weatherCondition = w.condition,
            weatherLocation = w.location,
            weatherHighLow = w.highLow,
            weatherIcons = w.icons,
            weatherSize = w.size,
            weatherWind = w.wind,
            weatherHumidity = w.humidity,
            livingSky = sky.enabled,
            skyWeather = sky.weather,
            skyIntensity = sky.intensity,
        )
    }

    /**
     * Writes what differs between [draft] and [original] (so SystemUI rebuilds the face once; the
     * face style goes last), the shortcuts, and the wallpaper. Blocking: call off the main thread.
     */
    fun apply(context: Context, draft: LockDesign, original: LockDesign, wallpaper: WallpaperDraft) {
        fun i(key: String, new: Int, old: Int) {
            if (new != old) put(context, key, new)
        }
        fun b(v: Boolean) = if (v) 1 else 0
        // The wallpaper first: IchthysDepth starts on its mask while the rest is written.
        setWallpaper(context, wallpaper)
        i(LockKeys.FILL, draft.fill, original.fill)
        i(LockKeys.SHAPE_STYLE, draft.shape, original.shape)
        i(LockKeys.FONT, draft.font, original.font)
        i(LockKeys.GLASS_FROST, draft.glassFrost, original.glassFrost)
        i(LockKeys.GLASS_OPACITY, draft.glassOpacity, original.glassOpacity)
        i(LockKeys.DATE_POS, draft.datePos, original.datePos)
        i(LockKeys.DATE_ALIGN, draft.dateAlign, original.dateAlign)
        i(LockKeys.DETAILS, if (draft.details) 1 else 0, if (original.details) 1 else 0)
        i(LockKeys.SHOW_DATE, if (draft.showDate) 1 else 0, if (original.showDate) 1 else 0)
        if (draft.colorMode != original.colorMode) put(context, LockKeys.COLOR_MODE, draft.colorMode)
        i(LockKeys.CUSTOM_COLOR, draft.color, original.color)
        i(LockKeys.OPACITY, draft.opacity, original.opacity)
        i(LockKeys.SCALE, (draft.scale * 100).roundToInt(), (original.scale * 100).roundToInt())
        i(LockKeys.MARGIN_TOP, LockKeys.DEFAULT_MARGIN_TOP + draft.dy.roundToInt(),
            LockKeys.DEFAULT_MARGIN_TOP + original.dy.roundToInt())
        i(LockKeys.OFFSET_X, draft.dx.roundToInt(), original.dx.roundToInt())
        i(LockKeys.ROTATION, draft.rot.roundToInt(), original.rot.roundToInt())
        i(LockKeys.SHOW_NOTIFICATIONS, if (draft.showNotifications) 1 else 0,
            if (original.showNotifications) 1 else 0)
        i(LockKeys.PRIVATE_NOTIFICATIONS, if (draft.privateNotifications) 1 else 0,
            if (original.privateNotifications) 1 else 0)
        i(LockKeys.DEPTH, draft.depth, original.depth)
        // IchthysOS lock screen weather; SystemUI follows these live (no restart).
        i(LockWeather.KEY_POS, draft.weatherPos, original.weatherPos)
        i(LockWeather.KEY_TEMP, b(draft.weatherTemp), b(original.weatherTemp))
        i(LockWeather.KEY_HIGH_LOW, b(draft.weatherHighLow), b(original.weatherHighLow))
        i(LockWeather.KEY_SIZE, draft.weatherSize, original.weatherSize)
        if (draft.weatherIcons != original.weatherIcons) {
            put(context, LockWeather.KEY_ICONS, draft.weatherIcons)
        }
        if (draft.weatherCondition != original.weatherCondition) {
            putSys(context, LockWeather.KEY_CONDITION, b(draft.weatherCondition))
        }
        if (draft.weatherLocation != original.weatherLocation) {
            putSys(context, LockWeather.KEY_LOCATION, b(draft.weatherLocation))
        }
        // Last of the weather: switching it on finds the other options in place.
        if (draft.weather != original.weather) {
            putSys(context, LockWeather.KEY_ENABLED, b(draft.weather))
        }
        // IchthysOS Living Sky; SystemUI follows these live. Its switch last, like the weather's.
        i(LivingSky.KEY_INTENSITY, draft.skyIntensity, original.skyIntensity)
        i(LivingSky.KEY_WEATHER, b(draft.skyWeather), b(original.skyWeather))
        i(LivingSky.KEY_ENABLED, b(draft.livingSky), b(original.livingSky))
        if (draft.look != original.look) put(context, LockKeys.LOOK, draft.look)
        if (draft.shortcutStart != original.shortcutStart) {
            LockShortcuts.select(context, LockShortcuts.SLOT_START, draft.shortcutStart)
        }
        if (draft.shortcutEnd != original.shortcutEnd) {
            LockShortcuts.select(context, LockShortcuts.SLOT_END, draft.shortcutEnd)
        }
        if (draft.style != original.style) {
            put(context, LockKeys.STYLE, draft.style)
            if ((draft.style == 0) != (original.style == 0)) {
                // Between the stock clock and a face: the stock clock and smartspace overlays
                // change, which takes a SystemUI restart (as the clock picker does).
                val theme = ThemeUtils(context)
                val face = draft.style != 0
                theme.setOverlayEnabled("android.theme.customization.hideclock",
                    if (face) "com.android.systemui.clocks.hideclock" else "android", "android")
                theme.setOverlayEnabled("android.theme.customization.smartspace",
                    if (face) "com.android.systemui.hide.smartspace" else "com.android.systemui",
                    "com.android.systemui")
                SystemUtils.restartSystemUI(context)
            }
        }
    }

    private fun setWallpaper(context: Context, wallpaper: WallpaperDraft) {
        try {
            val wm = WallpaperManager.getInstance(context)
            when (wallpaper) {
                is WallpaperDraft.Current -> Unit
                is WallpaperDraft.Photo -> {
                    val which =
                        if (wallpaper.home) WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
                        else WallpaperManager.FLAG_LOCK
                    context.contentResolver.openInputStream(wallpaper.uri)?.use {
                        wm.setStream(it, null, true, which)
                    }
                }
                is WallpaperDraft.Builtin -> {
                    val res = BUILTIN_WALLPAPERS.firstOrNull { it.first == wallpaper.id }?.second
                        ?: return
                    context.resources.openRawResource(res).use {
                        wm.setStream(it, null, true, WallpaperManager.FLAG_LOCK)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "wallpaper not set", e)
        }
    }

    fun readLooks(context: Context): List<SavedLook> =
        try {
            val a = JSONArray(str(context, LockKeys.LOOKS) ?: "[]")
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                SavedLook(o.getString("id"), o.optString("label", o.getString("id")), o)
            }
        } catch (e: Exception) {
            emptyList()
        }

    /** Saved looks are written at once (they are a list, not the lock screen). */
    fun writeLooks(context: Context, looks: List<SavedLook>) {
        val a = JSONArray()
        looks.forEach { a.put(it.json) }
        put(context, LockKeys.LOOKS, a.toString())
    }
}
