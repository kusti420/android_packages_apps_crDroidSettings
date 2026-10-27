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

import android.content.ContentValues
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log

/**
 * IchthysOS lock screen editor v2: the lock screen's two shortcuts, through SystemUI's
 * CustomizationProvider (what the stock wallpaper picker uses; Settings holds
 * CUSTOMIZE_SYSTEM_UI). Everything here blocks on a provider call: call it off the main thread.
 */
object LockShortcuts {
    private const val TAG = "LockEditor2"
    private const val AUTHORITY = "com.android.systemui.customization"
    private const val BASE = "content://$AUTHORITY/lockscreen_quickaffordance"
    private val AFFORDANCES: Uri = Uri.parse("$BASE/affordances")
    private val SELECTIONS: Uri = Uri.parse("$BASE/selections")

    const val SLOT_START = "bottom_start"
    const val SLOT_END = "bottom_end"

    /** One shortcut SystemUI offers. [enabled] false: it needs setting up first (still choosable). */
    class Affordance(val id: String, val name: String, val icon: Drawable?, val enabled: Boolean)

    /** What there is, and what is on each side now. Null when SystemUI can't be asked. */
    class Catalog(val all: List<Affordance>, val start: String, val end: String) {
        fun byId(id: String): Affordance? = all.firstOrNull { it.id == id }
    }

    fun load(context: Context): Catalog? =
        try {
            val sysRes = context.packageManager.getResourcesForApplication("com.android.systemui")
            val all = ArrayList<Affordance>()
            context.contentResolver.query(AFFORDANCES, null, null, null, null)?.use { c ->
                val id = c.getColumnIndex("id")
                val name = c.getColumnIndex("name")
                val icon = c.getColumnIndex("icon")
                val enabled = c.getColumnIndex("is_enabled")
                while (c.moveToNext()) {
                    val drawable =
                        try {
                            sysRes.getDrawable(c.getInt(icon), null)
                        } catch (e: Exception) {
                            null
                        }
                    all.add(
                        Affordance(c.getString(id), c.getString(name), drawable,
                            enabled < 0 || c.getInt(enabled) != 0))
                }
            }
            var start = ""
            var end = ""
            context.contentResolver.query(SELECTIONS, null, null, null, null)?.use { c ->
                val slot = c.getColumnIndex("slot_id")
                val id = c.getColumnIndex("affordance_id")
                while (c.moveToNext()) {
                    when (c.getString(slot)) {
                        SLOT_START -> start = c.getString(id)
                        SLOT_END -> end = c.getString(id)
                    }
                }
            }
            Catalog(all, start, end)
        } catch (e: Exception) {
            Log.w(TAG, "shortcuts not readable", e)
            null
        }

    /** Puts [id] on [slot] ("" leaves it empty). */
    fun select(context: Context, slot: String, id: String) {
        try {
            context.contentResolver.delete(SELECTIONS, null, arrayOf(slot))
            if (id.isNotEmpty()) {
                context.contentResolver.insert(
                    SELECTIONS,
                    ContentValues().apply {
                        put("slot_id", slot)
                        put("affordance_id", id)
                    },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "shortcut $id not set on $slot", e)
        }
    }
}
