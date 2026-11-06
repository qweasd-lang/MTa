package com.mt.chat

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

object ZhipuClient {

    private const val ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    private const val KP = "MT#2026!chat~x"
    private const val ENC = "LGwTCwlXUxlQX1BDSk4rbUFWVgAHR1IJUhYYTXpnRgIeWX9PIj1XJwcXDiZJV0gLUw=="

    val apiKey: String by lazy {
        try {
            val raw = Base64.decode(ENC, Base64.DEFAULT)
            val sb = StringBuilder()
            for (i in raw.indices) {
                sb.append((raw[i].toInt() xor KP[i % KP.length].code).toChar())
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun chat(
        model: String,
        temperature: Double,
        messages: JSONArray,
        cb: (ok: Boolean, payload: String) -> Unit
    ) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val body = JSONObject()
                body.put("model", model)
                body.put("temperature", temperature)
                body.put("messages", messages)

                conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000
                    readTimeout = 120000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer " + apiKey)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

                if (code in 200..299) {
                    val content = try {
                        JSONObject(text).optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content") ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    cb(true, if (content.isEmpty()) "（无回复）" else content)
                } else {
                    var detail = text
                    try {
                        val e = JSONObject(text).optJSONObject("error")
                        if (e != null) {
                            val m = e.optString("message", "")
                            detail = if (m.isNotEmpty()) m else e.optString("code", text)
                        }
                    } catch (ex: Exception) {
                    }
                    cb(false, code.toString() + (if (detail.isEmpty()) "" else "：" + detail.take(160)))
                }
            } catch (e: Exception) {
                cb(false, e.message ?: "网络异常")
            } finally {
                try { conn?.disconnect() } catch (e: Exception) {}
            }
        }.start()
    }
}
