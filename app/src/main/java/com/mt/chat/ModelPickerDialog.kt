package com.mt.chat

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ModelPickerDialog(
    private val act: MainActivity,
    private val current: String,
    private val onPick: (String) -> Unit
) {

    private var filter = "all"
    private var keyword = ""
    private val items = ArrayList<Models.Item>()

    private lateinit var adapter: Adapter
    private lateinit var tvCount: TextView

    fun show() {
        val view = LayoutInflater.from(act).inflate(R.layout.dialog_model_picker, null, false)
        val dlg = Dialog(act)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(view)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val et = view.findViewById<EditText>(R.id.etSearch)
        val rv = view.findViewById<RecyclerView>(R.id.rvModels)
        tvCount = view.findViewById(R.id.tvCount)

        val chipAll = view.findViewById<TextView>(R.id.chipAll)
        val chipText = view.findViewById<TextView>(R.id.chipText)
        val chipVision = view.findViewById<TextView>(R.id.chipVision)

        fun paintChips() {
            chipAll.setBackgroundResource(if (filter == "all") R.drawable.bg_chip_on else R.drawable.bg_chip)
            chipText.setBackgroundResource(if (filter == "text") R.drawable.bg_chip_on else R.drawable.bg_chip)
            chipVision.setBackgroundResource(if (filter == "vision") R.drawable.bg_chip_on else R.drawable.bg_chip)
        }

        adapter = Adapter(items) { id ->
            onPick(id)
            dlg.dismiss()
        }
        rv.layoutManager = LinearLayoutManager(act)
        rv.adapter = adapter

        fun reload() {
            items.clear()
            for (m in Models.all) {
                if (filter == "text" && m.vision) continue
                if (filter == "vision" && !m.vision) continue
                if (keyword.isNotEmpty()) {
                    val k = keyword.lowercase()
                    val hit = m.name.lowercase().contains(k) || m.id.contains(k) ||
                            Models.cap(m.id).contains(keyword)
                    if (!hit) continue
                }
                items.add(m)
            }
            tvCount.text = "共 " + items.size + " 个可用模型（本 Key 支持）"
            adapter.notifyDataSetChanged()
        }

        chipAll.setOnClickListener { filter = "all"; paintChips(); reload() }
        chipText.setOnClickListener { filter = "text"; paintChips(); reload() }
        chipVision.setOnClickListener { filter = "vision"; paintChips(); reload() }

        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                keyword = s?.toString()?.trim() ?: ""
                reload()
            }
        })

        paintChips()
        reload()
        dlg.show()
    }

    private class Adapter(
        private val list: List<Models.Item>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvName)
            val badge: TextView = v.findViewById(R.id.tvBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_model, parent, false))

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = list[position]
            holder.name.text = m.name
            holder.badge.text = if (m.vision) "文字+图片" else "文字"
            holder.itemView.setOnClickListener { onClick(m.id) }
        }
    }
}
