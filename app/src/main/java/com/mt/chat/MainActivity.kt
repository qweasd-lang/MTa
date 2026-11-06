package com.mt.chat

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
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
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private companion object {
        const val SYS = "你是MT聊天助手，一个专业的中文AI助手。开场请先说：\"Hi 我是MT聊天助手\"。始终使用简体中文回复。"
    }

    lateinit var rv: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var store: ChatStore
    private lateinit var etInput: EditText
    private lateinit var sendBtn: TextView
    private lateinit var welcomePane: View
    private lateinit var mePane: View
    private lateinit var tabMsg: View
    private lateinit var tabMe: View
    private lateinit var inputBar: View
    private lateinit var drawer: View
    private lateinit var drawerMask: View
    private lateinit var convList: LinearLayout
    private lateinit var loginPane: View
    private var sentCode: String = ""
    private var codeMode: Boolean = true
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
        temperature = Math.round(prefs.getFloat("temp", 0.7f) * 10f) / 10.0

        store = ChatStore(this)
        store.load()

        rv = findViewById(R.id.rv)
        etInput = findViewById(R.id.etInput)
        sendBtn = findViewById(R.id.btnSend)
        imgBar = findViewById(R.id.imgBar)
        imgPreview = findViewById(R.id.imgPreview)

        welcomePane = findViewById(R.id.welcomePane)
        mePane = findViewById(R.id.mePane)
        tabMsg = findViewById(R.id.tabMsg)
        tabMe = findViewById(R.id.tabMe)
        inputBar = findViewById(R.id.inputBar)

        applyGradient(findViewById(R.id.tvTitle))
        applyGradient(findViewById(R.id.tvWelcomeTitle))

        tabMsg.setOnClickListener { switchTab(true) }
        tabMe.setOnClickListener { switchTab(false) }
        findViewById<TextView>(R.id.meWx).setOnClickListener { openFeedback() }
        findViewById<TextView>(R.id.meSp).setOnClickListener { openSponsor() }

        adapter = ChatAdapter(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<TextView>(R.id.btnMenu).setOnClickListener { toggleDrawer() }
        findViewById<TextView>(R.id.btnSet).setOnClickListener { openSettings() }
        findViewById<TextView>(R.id.btnTrans).setOnClickListener { openTranslate() }
        findViewById<TextView>(R.id.btnImg).setOnClickListener { pickImage.launch("image/*") }
        findViewById<TextView>(R.id.btnClearImg).setOnClickListener {
            pendingImage = null; imgBar.visibility = View.GONE
        }
        sendBtn.setOnClickListener { send() }
        etInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                if (!event.isShiftPressed) { send(); true } else false
            } else false
        }
        findViewById<TextView>(R.id.tvTitle).setOnClickListener {
            ModelPickerDialog(this, model) { id -> applyModel(id) }.show()
        }

        ensureConv()
        refresh()
        initExtra()
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
        welcomePane.visibility = if (c.msgs.isEmpty()) View.VISIBLE else View.GONE
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
            setOnClickListener { openFeedback() }
        }
        val spBtn = MaterialButton(this).apply {
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
        setSendState()
        refresh()

        val sendModel = if (img != null && !Models.find(model).vision) "glm-4.6" else model
        if (sendModel != model) toast("当前模型不支持图片，本次自动用 GLM-4.6")

        ZhipuClient.chat(sendModel, temperature, buildMessages(c)) { ok, payload ->
            ui.post {
                thinking = false
                setSendState()
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
            prefs.edit().putFloat("temp", (Math.round(temperature * 10.0) / 10.0).toFloat()).apply()
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

    private fun switchTab(toMsg: Boolean) {
        if (toMsg) {
            mePane.visibility = View.GONE
            rv.visibility = View.VISIBLE
            inputBar.visibility = View.VISIBLE
            tabMsg.setBackgroundResource(R.drawable.bg_tab_on)
            tabMe.setBackgroundResource(0)
            refresh()
        } else {
            mePane.visibility = View.VISIBLE
            rv.visibility = View.GONE
            welcomePane.visibility = View.GONE
            inputBar.visibility = View.GONE
            tabMe.setBackgroundResource(R.drawable.bg_tab_on)
            tabMsg.setBackgroundResource(0)
            updateMe()
        }
    }

    private fun setSendState() {
        sendBtn.setBackgroundResource(if (thinking) R.drawable.bg_send_off else R.drawable.bg_send)
        sendBtn.text = if (thinking) "回复中" else "发送"
    }

    private fun applyGradient(tv: TextView) {
        tv.post {
            val w = tv.paint.measureText(tv.text.toString()).toInt().coerceAtLeast(1)
            tv.paint.shader = LinearGradient(
                0f, 0f, w.toFloat(), tv.textSize,
                intArrayOf(Color.WHITE, 0xFF36E7C9.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
    }

    private fun initExtra() {
        drawer = findViewById(R.id.drawer)
        drawerMask = findViewById(R.id.drawerMask)
        convList = findViewById(R.id.convList)
        loginPane = findViewById(R.id.loginPane)

        drawer.post { drawer.translationX = -drawer.width.toFloat() }
        drawerMask.setOnClickListener { closeDrawer() }
        findViewById<TextView>(R.id.btnNewConv).setOnClickListener { newConvFromDrawer() }
        findViewById<TextView>(R.id.btnTrans2).setOnClickListener { openTranslate() }

        findViewById<TextView>(R.id.meAccount).setOnClickListener { toast("当前手机号：" + maskPhone(phone())) }
        findViewById<TextView>(R.id.meInfo).setOnClickListener { toast("暂无新通知") }
        findViewById<TextView>(R.id.meSetPwd).setOnClickListener { toast("原生版暂不支持设置密码") }
        findViewById<TextView>(R.id.meLogout).setOnClickListener { logout() }

        setupLogin()
        if (phone().isEmpty()) loginPane.visibility = View.VISIBLE else loginPane.visibility = View.GONE
        updateMe()
    }

    private fun phone(): String = prefs.getString("phone", "") ?: ""

    private fun maskPhone(p: String): String =
        if (p.length == 11) p.substring(0, 3) + "****" + p.substring(7) else "未绑定手机号"

    private fun updateMe() {
        try {
            findViewById<TextView>(R.id.tvMePhone).text = maskPhone(phone())
            findViewById<TextView>(R.id.meAccountDesc).text = "手机号：" + maskPhone(phone())
            findViewById<TextView>(R.id.tvMeFoot).text =
                "模型：" + Models.label(model) + " · 温度：" + String.format(Locale.US, "%.1f", temperature) +
                "\n本地会话数：" + store.convs.size + " · 配置持久化：已开启"
        } catch (e: Exception) { }
    }

    private fun setupLogin() {
        val tabCode = loginPane.findViewById<TextView>(R.id.tabCode)
        val tabPwd = loginPane.findViewById<TextView>(R.id.tabPwd)
        val etPhone = loginPane.findViewById<EditText>(R.id.etPhone)
        val codeRow = loginPane.findViewById<View>(R.id.codeRow)
        val etPwd = loginPane.findViewById<EditText>(R.id.etPwd)
        val etCode = loginPane.findViewById<EditText>(R.id.etCode)
        val tvTip = loginPane.findViewById<TextView>(R.id.tvLoginTip)

        fun paint() {
            tabCode.setBackgroundResource(if (codeMode) R.drawable.bg_primary_btn else 0)
            tabPwd.setBackgroundResource(if (codeMode) 0 else R.drawable.bg_primary_btn)
            tabCode.setTextColor(if (codeMode) 0xFFFFFFFF.toInt() else 0xFF9AA3C7.toInt())
            tabPwd.setTextColor(if (codeMode) 0xFF9AA3C7.toInt() else 0xFFFFFFFF.toInt())
            codeRow.visibility = if (codeMode) View.VISIBLE else View.GONE
            etPwd.visibility = if (codeMode) View.GONE else View.VISIBLE
            tvTip.text = if (codeMode) "验证码登录" else "密码登录"
        }
        tabCode.setOnClickListener { codeMode = true; paint() }
        tabPwd.setOnClickListener { codeMode = false; paint() }
        paint()

        loginPane.findViewById<TextView>(R.id.btnGetCode).setOnClickListener {
            val p = etPhone.text.toString().trim()
            if (!p.matches(Regex("^1\\d{10}$"))) { toast("请输入正确的 11 位手机号"); return@setOnClickListener }
            sentCode = String.format(Locale.US, "%04d", Random.nextInt(10000))
            toast("验证码：" + sentCode + "（模拟短信）")
        }

        loginPane.findViewById<TextView>(R.id.btnLogin).setOnClickListener {
            val p = etPhone.text.toString().trim()
            if (!p.matches(Regex("^1\\d{10}$"))) { toast("手机号格式错误"); return@setOnClickListener }
            if (codeMode) {
                val c = etCode.text.toString().trim()
                if (sentCode.isEmpty()) { toast("请先点「获取验证码」"); return@setOnClickListener }
                if (c != sentCode) { toast("验证码错误"); return@setOnClickListener }
            } else {
                if (etPwd.text.toString().trim().length < 6) { toast("密码至少 6 位"); return@setOnClickListener }
            }
            prefs.edit().putString("phone", p).apply()
            loginPane.visibility = View.GONE
            toast("登录成功")
            updateMe()
        }
    }

    private fun logout() {
        prefs.edit().remove("phone").apply()
        loginPane.visibility = View.VISIBLE
        toast("已退出登录")
    }

    private fun toggleDrawer() {
        if (drawer.translationX < -drawer.width / 2f) openDrawer() else closeDrawer()
    }

    private fun openDrawer() {
        buildConvList()
        drawerMask.visibility = View.VISIBLE
        drawer.animate().translationX(0f).setDuration(220).start()
    }

    private fun closeDrawer() {
        drawer.animate().translationX(-drawer.width.toFloat()).setDuration(200).start()
        drawerMask.postDelayed({ drawerMask.visibility = View.GONE }, 200)
    }

    private fun newConvFromDrawer() {
        val c = Conv(System.currentTimeMillis(), "新对话")
        store.convs.add(0, c)
        store.setCurrent(c.id)
        store.save()
        refresh()
        closeDrawer()
    }

    private fun buildConvList() {
        convList.removeAllViews()
        if (store.convs.isEmpty()) {
            val tv = TextView(this)
            tv.text = "暂无对话\n点上方「新建对话」开始"
            tv.setTextColor(0xFF9AA3C7.toInt())
            tv.textSize = 12f
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(0, dp(40), 0, dp(40))
            convList.addView(tv)
            return
        }
        for (c in store.convs) {
            val row = layoutInflater.inflate(R.layout.item_conv, convList, false)
            row.findViewById<TextView>(R.id.tvTitle).text = c.title
            val last = c.msgs.lastOrNull()
            row.findViewById<TextView>(R.id.tvDesc).text =
                if (last == null) "空对话" else TimeFmt.rel(last.ts) + " · " + last.text.take(18)
            row.setBackgroundResource(R.drawable.bg_row_conv)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            row.layoutParams = lp
            row.setOnClickListener {
                store.setCurrent(c.id); refresh(); updateMe(); closeDrawer()
            }
            row.findViewById<TextView>(R.id.btnDel).setOnClickListener {
                store.convs.remove(c)
                if (store.currentId == c.id) store.setCurrent(store.convs.firstOrNull()?.id ?: 0L)
                store.save(); ensureConv(); refresh(); buildConvList(); updateMe()
            }
            convList.addView(row)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
