/*
 * Copyright (C) 2026 IchthysOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.preferences

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R

/**
 * Picks an entry of one of the crDroid fingerprint resource packs in a dialog, so that the picker
 * is a row of the fingerprint page instead of a page of its own. The preference key is the
 * Settings.System key it writes (the index of the chosen entry):
 *  - `udfps_icon`: an icon of com.crdroid.udfps.icons (array `udfps_icons`);
 *  - `udfps_anim_style`: an animation of com.crdroid.udfps.animations (arrays
 *    `udfps_animation_styles` / `_previews` / `_titles`).
 *
 * [isAvailable] is false when the pack is not installed; the page then removes the row.
 */
class UdfpsPackPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    private class Entry(val thumbnail: String, val animation: String?, val label: String?)

    private var dialog: AlertDialog? = null
    private var packRes: Resources? = null
    private var entries: List<Entry> = emptyList()

    private val isAnimationPack: Boolean
        get() = key == Settings.System.UDFPS_ANIM_STYLE

    private val packPackage: String
        get() = if (isAnimationPack) ANIMATIONS_PACKAGE else ICONS_PACKAGE

    /** Whether the resource pack is installed and has entries. */
    val isAvailable: Boolean
        get() = entries.isNotEmpty()

    init {
        isPersistent = false
        loadPack()
    }

    private fun loadPack() {
        val pkg = packPackage
        val res = try {
            context.packageManager.getResourcesForApplication(pkg)
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }
        fun array(name: String): Array<String>? {
            val id = res.getIdentifier(name, "array", pkg)
            return if (id == 0) null else res.getStringArray(id)
        }
        entries = if (isAnimationPack) {
            val styles = array("udfps_animation_styles") ?: return
            val previews = array("udfps_animation_previews") ?: return
            val titles = array("udfps_animation_titles") ?: return
            List(minOf(styles.size, previews.size, titles.size)) {
                Entry(previews[it], styles[it], titles[it])
            }
        } else {
            (array("udfps_icons") ?: return).map { Entry(it, null, null) }
        }
        packRes = res
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        updateSummary()
    }

    override fun onDetached() {
        dialog?.dismiss()
        dialog = null
        super.onDetached()
    }

    private fun currentIndex(): Int =
        Settings.System.getIntForUser(context.contentResolver, key, 0, UserHandle.USER_CURRENT)

    private fun updateSummary() {
        // Icons have no names; animations do.
        summary = entries.getOrNull(currentIndex())?.label
    }

    private fun drawable(name: String?): Drawable? {
        val res = packRes ?: return null
        val id = res.getIdentifier(name ?: return null, "drawable", packPackage)
        if (id == 0) return null
        return try {
            res.getDrawable(id, context.theme)
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    override fun onClick() {
        if (entries.isEmpty()) return
        val content = LayoutInflater.from(context).inflate(R.layout.selector_item_view, null)
        val landscape = context.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
        content.findViewById<RecyclerView>(R.id.recycler_view).apply {
            layoutManager = GridLayoutManager(context, if (landscape) 4 else 3)
            adapter = EntryAdapter(currentIndex())
            post {
                // Keep a long pack scrollable inside the dialog instead of off screen.
                val max = (context.resources.displayMetrics.heightPixels * 0.6f).toInt()
                if (height > max) {
                    layoutParams.height = max
                    requestLayout()
                }
            }
        }
        dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { dialog = null }
            .show()
    }

    private inner class EntryAdapter(private var selected: Int) :
        RecyclerView.Adapter<EntryAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_option, parent, false))

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = entries[position]
            holder.image.background = null
            holder.image.setImageDrawable(drawable(entry.thumbnail))
            if (entry.label == null) {
                holder.name.visibility = View.GONE
                holder.image.setPadding(20, 20, 20, 20)
            } else {
                holder.name.visibility = View.VISIBLE
                holder.name.text = entry.label
            }
            holder.itemView.isActivated = position == selected
            holder.itemView.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index == RecyclerView.NO_POSITION) return@setOnClickListener
                val old = selected
                selected = index
                notifyItemChanged(old)
                notifyItemChanged(index)
                Settings.System.putIntForUser(
                    context.contentResolver, key, index, UserHandle.USER_CURRENT)
                updateSummary()
                // Play the animation once in place as a preview.
                (drawable(entries[index].animation) as? AnimationDrawable)?.let { animation ->
                    holder.image.background = animation
                    animation.isOneShot = true
                    animation.stop()
                    animation.start()
                }
            }
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.option_label)
            val image: ImageView = itemView.findViewById(R.id.option_thumbnail)
        }
    }

    companion object {
        private const val ICONS_PACKAGE = "com.crdroid.udfps.icons"
        private const val ANIMATIONS_PACKAGE = "com.crdroid.udfps.animations"

        /** Back to the first entry of both packs, which is SystemUI's default. */
        @JvmStatic
        fun reset(context: Context) {
            val resolver = context.contentResolver
            Settings.System.putIntForUser(
                resolver, Settings.System.UDFPS_ICON, 0, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(
                resolver, Settings.System.UDFPS_ANIM_STYLE, 0, UserHandle.USER_CURRENT)
        }
    }
}
