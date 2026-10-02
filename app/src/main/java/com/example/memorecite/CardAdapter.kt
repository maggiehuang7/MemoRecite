package com.example.memorecite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class CardAdapter(
    private val cards: List<MemoCard>,
    private val onClick: (MemoCard) -> Unit,
    private val onLongClick: (MemoCard) -> Unit,
    private val isSelectModeProvider: () -> Boolean,
    private val isSelectedProvider: (MemoCard) -> Boolean
) : RecyclerView.Adapter<CardAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvCheck: TextView = view.findViewById(R.id.tvCheck)
        val ivThumb: ImageView = view.findViewById(R.id.ivThumb)
        val tvFront: TextView = view.findViewById(R.id.tvCardFront)
        val tvBack: TextView = view.findViewById(R.id.tvCardBack)
        val tvStatus: TextView = view.findViewById(R.id.tvCardStatus)
        val tvCustomMark: TextView = view.findViewById(R.id.tvCustomMark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = cards[position]
        val ctx = holder.itemView.context

        holder.tvFront.text = c.front
        holder.tvBack.text = c.back
        holder.tvStatus.text = EbbinghausScheduler.describeNext(ctx, c)

        holder.tvCustomMark.visibility =
            if (c.customIntervals != null) View.VISIBLE else View.GONE

        if (c.hasImage) {
            ImageStore.file(ctx, c.imageFile)?.let { f ->
                holder.ivThumb.load(f)
                holder.ivThumb.visibility = View.VISIBLE
            } ?: run { holder.ivThumb.visibility = View.GONE }
        } else {
            holder.ivThumb.visibility = View.GONE
        }

        val inSelect = isSelectModeProvider()
        val selected = isSelectedProvider(c)
        holder.tvCheck.visibility = if (inSelect) View.VISIBLE else View.GONE
        holder.tvCheck.text = if (selected) "✅" else "⭕"

        holder.itemView.setOnClickListener { onClick(c) }
        holder.itemView.setOnLongClickListener { onLongClick(c); true }
    }

    override fun getItemCount() = cards.size
}