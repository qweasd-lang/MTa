package com.mt.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Msg(
    val role: String,
    val text: String,
    val ts: Long,
    val img: String? = null
)

class Conv(val id: Long, var title: String = "新对话") {
    val msgs: MutableList<Msg> = mutableListOf()
}

class ChatStore(ctx: Context) {
    private val file = File(ctx.filesDir, "chats.json")
    val convs = mutableListOf<Conv>()
    var currentId: Long = 0L
        private set

    fun setCurrent(id: Long) { currentId = id }

    fun load() {
        convs.clear()
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val c = Conv(o.optLong("id", System.currentTimeMillis() + i), o.optString("title", "新对话"))
                    val ma = o.optJSONArray("msgs") ?: JSONArray()
                    for (j in 0 until ma.length()) {
                        val m = ma.getJSONObject(j)
                        val img = if (m.has("img")) m.optString("img", "") else ""
                        c.msgs.add(
                            Msg(
                                m.optString("r", "user"),
                                m.optString("t", ""),
                                m.optLong("ts", System.currentTimeMillis()),
                                if (img.isEmpty()) null else img
                            )
                        )
                    }
                    convs.add(c)
                }
            } catch (e: Exception) {
            }
        }
        currentId = convs.firstOrNull()?.id ?: 0L
    }

    fun save() {
        try {
            val arr = JSONArray()
            for (c in convs) {
                val o = JSONObject()
                o.put("id", c.id)
                o.put("title", c.title)
                val ma = JSONArray()
                for (m in c.msgs) {
                    val mo = JSONObject()
                    mo.put("r", m.role)
                    mo.put("t", m.text)
                    mo.put("ts", m.ts)
                    if (m.img != null) mo.put("img", m.img)
                    ma.put(mo)
                }
                o.put("msgs", ma)
                arr.put(o)
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }
}
