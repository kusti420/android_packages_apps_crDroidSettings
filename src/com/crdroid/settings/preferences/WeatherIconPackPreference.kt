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

import android.content.ContentValues
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceDataStore
import com.android.internal.util.crdroid.OmniJawsClient
import com.android.settings.R
import com.android.settingslib.ichthys.clock.LockWeather

/**
 * IchthysOS: the weather icon pack, picked in Settings instead of OmniJaws' bare list. The row
 * lists OmniJaws' built-in packs that are really installed ([LockWeather.ICON_PACKS], checked by
 * resolving each pack's sun icon) and, through [OptionPreviews] ("weather_icon_pack"), the picker
 * shows every pack's own sun, cloud, rain and snow next to its name.
 *
 * The value is OmniJaws' own setting: read from its settings provider (the "icon_pack" column
 * every weather client reads) and written back through that provider, which OmniJaws answers by
 * storing the pack and telling SystemUI and the lock screen to reload their icons.
 */
class WeatherIconPackPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : ListPreference(context, attrs) {

    init {
        val packs =
            LockWeather.ICON_PACKS.filter { (pack, _) ->
                LockWeather.icon(context, pack, SAMPLE_CODE) != null
            }
        entries = packs.map { it.second }.toTypedArray()
        entryValues = packs.map { it.first }.toTypedArray()
        preferenceDataStore = OmniJawsIconPackStore(context)
        if (packs.isEmpty()) {
            // No OmniJaws (or none of its packs): nothing to pick.
            isEnabled = false
            summary = context.getString(R.string.weather_icon_pack_unavailable)
        }
    }

    /** OmniJaws' "icon_pack" setting, behind the preference's data store. */
    private class OmniJawsIconPackStore(private val context: Context) : PreferenceDataStore() {

        override fun getString(key: String, defValue: String?): String? {
            try {
                context.contentResolver
                    .query(
                        OmniJawsClient.SETTINGS_URI,
                        OmniJawsClient.SETTINGS_PROJECTION,
                        null,
                        null,
                        null,
                    )
                    ?.use { c ->
                        val col = c.getColumnIndex(COLUMN_ICON_PACK)
                        if (col >= 0 && c.moveToFirst()) {
                            val v = c.getString(col)
                            if (!v.isNullOrEmpty()) return v
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read the weather icon pack", e)
            }
            return defValue ?: DEFAULT_PACK
        }

        override fun putString(key: String, value: String?) {
            if (value.isNullOrEmpty()) return
            try {
                val values = ContentValues().apply { put(COLUMN_ICON_PACK, value) }
                context.contentResolver.update(OmniJawsClient.SETTINGS_URI, values, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot store the weather icon pack", e)
            }
        }
    }

    companion object {
        private const val TAG = "WeatherIconPackPref"
        /** Settings provider column, as OmniJaws' WeatherContentProvider names it. */
        private const val COLUMN_ICON_PACK = "icon_pack"
        private const val DEFAULT_PACK = "org.omnirom.omnijaws.tabler"
        /** OmniJaws condition code "sunny": every pack has it, so it proves the pack exists. */
        private const val SAMPLE_CODE = 32
    }
}
