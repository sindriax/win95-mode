package com.win95mode.app

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed interface IconRow {
    class Header(val title: String, val caption: String? = null) : IconRow
    class Cell(val key: String, val label: String, val image: () -> Bitmap, val onClick: () -> Unit) : IconRow
    class Action(val label: String, val onClick: () -> Unit) : IconRow
}

class IconsAdapter(private val iconPx: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var rows: List<IconRow> = emptyList()
        set(value) {
            field = value
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is IconRow.Header -> 0
        is IconRow.Cell -> 1
        is IconRow.Action -> 2
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = when (viewType) {
            0 -> R.layout.item_header
            1 -> R.layout.item_icon
            else -> R.layout.item_action
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        if (viewType == 1) view.findViewById<ImageView>(R.id.icon_image).layoutParams.apply {
            width = iconPx
            height = iconPx
        }
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val v = holder.itemView
        when (val row = rows[position]) {
            is IconRow.Header -> {
                v.findViewById<TextView>(R.id.header_title).text = row.title
                v.findViewById<TextView>(R.id.header_caption).apply {
                    text = row.caption
                    visibility = if (row.caption == null) View.GONE else View.VISIBLE
                }
            }
            is IconRow.Cell -> {
                v.findViewById<ImageView>(R.id.icon_image).setImageBitmap(row.image())
                v.findViewById<TextView>(R.id.icon_label).text = row.label
                v.contentDescription = row.label
                v.setOnClickListener { row.onClick() }
            }
            is IconRow.Action -> v.findViewById<TextView>(R.id.action_button).apply {
                text = row.label
                setOnClickListener { row.onClick() }
            }
        }
    }

    fun isFullWidth(position: Int) = rows.getOrNull(position) !is IconRow.Cell
}

class WallpapersAdapter(
    private val tileWidthPx: Int,
    private val thumbnail: (Wallpaper) -> Bitmap,
    private val current: () -> String?,
    private val onClick: (Wallpaper) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount() = Wallpapers.all.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wallpaper, parent, false)
        view.findViewById<View>(R.id.wall_frame).layoutParams.height = (tileWidthPx * 1.6f).toInt()
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val v = holder.itemView
        val wallpaper = Wallpapers.all[position]
        val name = v.context.getString(wallpaper.labelRes)
        val isCurrent = current() == wallpaper.key
        v.findViewById<ImageView>(R.id.wall_image).setImageBitmap(thumbnail(wallpaper))
        v.findViewById<View>(R.id.wall_live).visibility = if (wallpaper.live) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.wall_label).text =
            if (isCurrent) v.context.getString(R.string.wall_current, name) else name
        v.contentDescription = if (isCurrent) v.context.getString(R.string.wall_current_desc, name) else name
        v.setOnClickListener { onClick(wallpaper) }
    }
}
