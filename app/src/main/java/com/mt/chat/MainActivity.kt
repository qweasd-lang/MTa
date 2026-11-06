package com.mt.chat

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private companion object {
        const val SYS = "你是MT聊天助手，一个专业的中文AI助手。开场请先说：\"Hi 我是MT聊天助手\"。始终使用简体中文回复。"
    }

    lateinit var rv: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var store: ChatStore
    private lateinit var etInput: EditText
    private lateinit var sendBtn: MaterialButton
    private lateinit var imgBar: LinearLayout
    private lateinit var imgPreview: ImageView

    private val ui = Handler(Looper.getMainLooper())
    private var pendingImage: String? = null
    private var thinking = false
    private var model: String = Models.DEFAULT
    private var temperature: Double = 0.7

    private val prefs by lazy { getSharedPreferences("mt_cfg", Context.MODE_PRIVATE) }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val b64 = compress(uri)
        if (b64 == null) { toast("图片读取失败"); return@registerForActivityResult }
        pendingImage = b64
        showPreview(b64)
        toast("已选择图片，发送时附带")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        model = prefs.getString("model", Models.DEFAULT) ?: Models.DEFAULT
        if (Models.all.none { it.id == model }) model = Models.DEFAULT
        temperature = prefs.getFloat("temp", 0.7f).toDouble()

        store = ChatStore(this)
        store.load()

        rv = findViewById(R.id.rv)
        etInput = findViewById(R.id.etInput)
        sendBtn = findViewById(R.id.btnSend)
        imgBar = findViewById(R.id.imgBar)
        imgPreview = findViewById(R.id.imgPreview)

        adapter = ChatAdapter(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<TextView>(R.id.btnMenu).setOnClickListener { openConvs() }
        findViewById<TextView>(R.id.btnSet).setOnClickListener { openSettings() }
        findViewById<TextView>(R.id.btnTrans).setOnClickListener { openTranslate() }
        findViewById<TextView>(R.id.btnImg).setOnClickListener { pickImage.launch("image/*") }
        findViewById<TextView>(R.id.btnClearImg).setOnClickListener {
            pendingImage = null; imgBar.visibility = View.GONE
        }
        sendBtn.setOnClickListener { send() }
        findViewById<TextView>(R.id.tvTitle).setOnClickListener {
            ModelPickerDialog(this, model) { id -> applyModel(id) }.show()
        }

        ensureConv()
        refresh()
    }

    // ---------------- 会话 ----------------

    private fun ensureConv(): Conv {
        var c = store.convs.firstOrNull { it.id == store.currentId }
        if (c == null) {
            c = Conv(System.currentTimeMillis(), "新对话")
            store.convs.add(0, c)
            store.setCurrent(c.id)
            store.save()
        }
        return c
    }

    private fun refresh() {
        val c = ensureConv()
        adapter.msgs = c.msgs.toList()
        adapter.thinking = thinking
        adapter.refresh()
        if (adapter.itemCount > 0) rv.scrollToPosition(adapter.itemCount - 1)
    }

    private fun openConvs() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.panel))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "我的对话"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val newBtn = MaterialButton(this).apply {
            text = "＋ 新建对话"
            isTextAllCaps = false
            setOnClickListener {
                val c = Conv(System.currentTimeMillis(), "新对话")
                store.convs.add(0, c)
                store.setCurrent(c.id)
                store.save(); refresh(); dlg.dismiss()
            }
        }
        root.addView(newBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        val sv = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(list)

        for (c in store.convs) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_conv, list, false)
            row.findViewById<TextView>(R.id.tvTitle).text = c.title
            val last = c.msgs.lastOrNull()
            row.findViewById<TextView>(R.id.tvDesc).text =
                if (last == null) "空对话" else TimeFmt.rel(last.ts) + " · " + last.text.take(20)
            row.setOnClickListener {
                store.setCurrent(c.id); refresh(); dlg.dismiss()
            }
            row.findViewById<TextView>(R.id.btnDel).setOnClickListener {
                store.convs.remove(c)
                if (store.currentId == c.id) store.setCurrent(store.convs.firstOrNull()?.id ?: 0L)
                store.save(); ensureConv(); refresh(); dlg.dismiss()
            }
            list.addView(row)
        }

        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))

        val fbRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val fbBtn = MaterialButton(this).apply {
            text = "💬 加微信反馈"; isTextAllCaps = false
            setOnClickListener { openFeedback() }
        }
        val spBtn = MaterialButton(this).apply {
            text = "☕ 赞助支持"; isTextAllCaps = false
            setOnClickListener { openSponsor() }
        }
        fbRow.addView(fbBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        fbRow.addView(spBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(fbRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        dlg.setContentView(root)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dlg.show()
    }

    // ---------------- 发送 ----------------

    private fun send() {
        if (thinking) { toast("正在回复中，请稍候"); return }
        val text = etInput.text.toString().trim()
        val img = pendingImage
        if (text.isEmpty() && img == null) return

        val c = ensureConv()
        c.msgs.add(Msg("user", text, System.currentTimeMillis(), img))
        if (c.title == "新对话") {
            c.title = if (text.isNotEmpty()) text.take(18) else "图片对话"
        }
        etInput.setText("")
        pendingImage = null
        imgBar.visibility = View.GONE
        store.save()
        thinking = true
        refresh()

        val sendModel = if (img != null && !Models.find(model).vision) "glm-4.6" else model
        if (sendModel != model) toast("当前模型不支持图片，本次自动用 GLM-4.6")

        ZhipuClient.chat(sendModel, temperature, buildMessages(c)) { ok, payload ->
            ui.post {
                thinking = false
                val answer = Msg("assistant", if (ok) payload else "出错了：" + payload, System.currentTimeMillis())
                c.msgs.add(answer)
                store.save()
                refresh()
                if (ok) typewrite(c.msgs.size - 1, payload)
            }
        }
    }

    private fun buildMessages(c: Conv): JSONArray {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", SYS))
        for (m in c.msgs) {
            val o = JSONObject()
            o.put("role", if (m.role == "user") "user" else "assistant")
            if (m.img != null) {
                val content = JSONArray()
                content.put(JSONObject().put("type", "text").put("text", if (m.text.isEmpty()) "请分析这张图片" else m.text))
                content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", m.img)))
                o.put("content", content)
            } else {
                o.put("content", m.text)
            }
            arr.put(o)
        }
        return arr
    }

    private fun typewrite(position: Int, full: String) {
        if (full.isEmpty()) return
        val total = full.length
        val step = maxOf(1, minOf(40, total / 150))
        var i = 0
        val runnable = object : Runnable {
            override fun run() {
                i = minOf(total, i + step)
                adapter.updateBubbleAt(position, full.substring(0, i))
                rv.scrollToPosition(adapter.itemCount - 1)
                if (i < total) ui.postDelayed(this, 16)
            }
        }
        ui.post(runnable)
    }

    // ---------------- 设置 ----------------

    private fun applyModel(id: String) {
        model = id
        prefs.edit().putString("model", id).apply()
        toast("已切换：" + Models.label(id))
    }

    private fun openSettings() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null, false)
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(v)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvPick = v.findViewById<TextView>(R.id.tvPickModel)
        val seek = v.findViewById<SeekBar>(R.id.seekTemp)
        val tvTemp = v.findViewById<TextView>(R.id.tvTemp)
        val c1 = v.findViewById<TextView>(R.id.chipT1)
        val c2 = v.findViewById<TextView>(R.id.chipT2)
        val c3 = v.findViewById<TextView>(R.id.chipT3)

        tvPick.text = Models.label(model)
        seek.progress = (temperature * 10).toInt().coerceIn(0, 10)
        tvTemp.text = String.format(Locale.US, "%.1f", temperature)

        fun paintChips() {
            val cur = String.format(Locale.US, "%.1f", temperature)
            c1.setBackgroundResource(if (cur == "0.2") R.drawable.bg_chip_on else R.drawable.bg_chip)
            c2.setBackgroundResource(if (cur == "0.7") R.drawable.bg_chip_on else R.drawable.bg_chip)
            c3.setBackgroundResource(if (cur == "1.0") R.drawable.bg_chip_on else R.drawable.bg_chip)
        }
        paintChips()

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                temperature = progress / 10.0
                tvTemp.text = String.format(Locale.US, "%.1f", temperature)
                paintChips()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        c1.setOnClickListener { temperature = 0.2; seek.progress = 2; paintChips() }
        c2.setOnClickListener { temperature = 0.7; seek.progress = 7; paintChips() }
        c3.setOnClickListener { temperature = 1.0; seek.progress = 10; paintChips() }

        tvPick.setOnClickListener {
            dlg.dismiss()
            ModelPickerDialog(this, model) { id -> applyModel(id) }.show()
        }

        v.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dlg.dismiss() }
        v.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            prefs.edit().putFloat("temp", temperature.toFloat()).apply()
            dlg.dismiss()
            toast("设置已更新")
        }

        dlg.show()
    }

    // ---------------- 微信反馈 / 赞助 ----------------

    private fun openQr(resId: Int, title: String, tip: String) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_qr, null, false)
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(v)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        v.findViewById<TextView>(R.id.tvQrTitle).text = title
        v.findViewById<TextView>(R.id.tvQrTip).text = tip
        v.findViewById<ImageView>(R.id.ivQr).setImageResource(resId)
        v.findViewById<MaterialButton>(R.id.btnQrClose).setOnClickListener { dlg.dismiss() }
        dlg.show()
    }

    private fun openFeedback() = openQr(R.drawable.wxqr, "加微信反馈", "扫码添加微信，反馈 BUG 或提出建议\n添加时请备注：MT聊天助手")

    private fun openSponsor() = openQr(R.drawable.sponsor, "赞助支持", "扫码赞赏，支持开发者继续维护与更新\n感谢每一份鼓励 ❤️")

    // ---------------- 翻译 ----------------

    private fun openTranslate() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_translate, null, false)
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(v)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val etSrc = v.findViewById<EditText>(R.id.etSrc)
        val etLang = v.findViewById<EditText>(R.id.etLang)
        val tvRes = v.findViewById<TextView>(R.id.tvTransResult)

        v.findViewById<MaterialButton>(R.id.btnTCancel).setOnClickListener { dlg.dismiss() }
        v.findViewById<MaterialButton>(R.id.btnDoTrans).setOnClickListener {
            val src = etSrc.text.toString().trim()
            if (src.isEmpty()) { toast("请输入原文"); return@setOnClickListener }
            val lang = etLang.text.toString().trim().ifEmpty { "中文" }
            tvRes.text = "翻译中…"
            val msgs = JSONArray()
            msgs.put(JSONObject().put("role", "system").put("content", "你是一个翻译专家，将用户输入翻译成" + lang + "，只输出译文，不要解释。"))
            msgs.put(JSONObject().put("role", "user").put("content", src))
            ZhipuClient.chat(model, temperature, msgs) { ok, payload ->
                ui.post { tvRes.text = if (ok) payload else "翻译失败：" + payload }
            }
        }
        dlg.show()
    }

    // ---------------- 图片 ----------------

    private fun compress(uri: Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val bytes = input.readBytes()
            input.close()
            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val maxW = 2400
            val maxH = 1080
            val scale = minOf(1f, maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
            if (scale < 1f) {
                bmp = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 86, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun showPreview(b64: String) {
        imgBar.visibility = View.VISIBLE
        loadBase64(b64, imgPreview)
    }

    fun loadBase64(b64: String, iv: ImageView) {
        try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) iv.setImageBitmap(bmp) else iv.setImageDrawable(null)
        } catch (e: Exception) {
            iv.setImageDrawable(null)
        }
    }

    // ---------------- 工具 ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
