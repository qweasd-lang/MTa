package com.mt.chat

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val host: MainActivity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val T_USER = 0
        const val T_AI = 1
        const val T_THINK = 2
    }

    var msgs: List<Msg> = emptyList()
    var thinking: Boolean = false

    override fun getItemCount(): Int = msgs.size + if (thinking) 1 else 0

    override fun getItemViewType(position: Int): Int {
        if (position >= msgs.size) return T_THINK
        return if (msgs[position].role == "user") T_USER else T_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_USER -> UserVH(inf.inflate(R.layout.item_msg_user, parent, false))
            T_AI -> AiVH(inf.inflate(R.layout.item_msg_ai, parent, false))
            else -> ThinkVH(inf.inflate(R.layout.item_thinking, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserVH -> {
                val m = msgs[position]
                holder.bind(m)
            }
            is AiVH -> {
                val m = msgs[position]
                holder.bind(m)
            }
            is ThinkVH -> holder.start()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ThinkVH) holder.stop()
        super.onViewRecycled(holder)
    }

    fun updateBubbleAt(position: Int, text: String) {
        val vh = host.rv.findViewHolderForAdapterPosition(position)
        if (vh is AiVH) vh.bubble.text = text
    }

    fun refresh() {
        notifyDataSetChanged()
    }

    inner class UserVH(v: View) : RecyclerView.ViewHolder(v) {
        val bubble: TextView = v.findViewById(R.id.bubble)
        val time: TextView = v.findViewById(R.id.time)
        val img: ImageView = v.findViewById(R.id.img)

        fun bind(m: Msg) {
            bubble.text = m.text
            bubble.visibility = if (m.text.isEmpty()) View.GONE else View.VISIBLE
            time.text = TimeFmt.rel(m.ts)
            if (m.img != null) {
                img.visibility = View.VISIBLE
                host.loadBase64(m.img, img)
            } else {
                img.visibility = View.GONE
            }
        }
    }

    inner class AiVH(v: View) : RecyclerView.ViewHolder(v) {
        val bubble: TextView = v.findViewById(R.id.bubble)
        val time: TextView = v.findViewById(R.id.time)

        fun bind(m: Msg) {
            bubble.text = m.text
            time.text = TimeFmt.rel(m.ts)
        }
    }

    inner class ThinkVH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvThink)
        private var anim: ObjectAnimator? = null

        fun start() {
            anim?.cancel()
            tv.alpha = 1f
            anim = ObjectAnimator.ofFloat(tv, "alpha", 1f, 0.3f).apply {
                duration = 850
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
        }

        fun stop() {
            anim?.cancel()
            anim = null
            tv.alpha = 1f
        }
    }
}
