package com.example.memorecite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class CardPagerAdapter(
    private val cards: List<MemoCard>
) : RecyclerView.Adapter<CardPagerAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.ivCardImage)
        val tvFront: TextView = view.findViewById(R.id.tvFront)
        val tvBack: TextView = view.findViewById(R.id.tvBack)
        val tvProgress: TextView = view.findViewById(R.id.tvProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_memo_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val card = cards[position]
        val ctx = holder.itemView.context

        if (card.hasImage) {
            ImageStore.file(ctx, card.imageFile)?.let { f ->
                holder.ivImage.load(f)
                holder.ivImage.visibility = View.VISIBLE
            } ?: run { holder.ivImage.visibility = View.GONE }
        } else {
            holder.ivImage.visibility = View.GONE
        }

        val scale = Prefs.getFontSizeScale(ctx)
        holder.tvFront.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f * scale)
        holder.tvBack.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28f * scale)

        holder.tvFront.text = card.front
        holder.tvFront.visibility = if (card.front.isBlank()) View.GONE else View.VISIBLE
        holder.tvBack.text = card.back

        holder.tvProgress.text = ctx.getString(
            R.string.md_progress,
            card.reviewLevel + 1,
            EbbinghausScheduler.describeNext(ctx, card)
        )
    }

    override fun getItemCount() = cards.size
}