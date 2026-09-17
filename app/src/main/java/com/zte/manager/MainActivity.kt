package com.zte.manager

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val baseUrl = "http://192.168.0.1"
    private var sessionCookie: String? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvInfo = findViewById(R.id.tvInfo)

        findViewById<Button>(R.id.btnLogin).setOnClickListener { loginAndFetch() }
        findViewById<Button>(R.id.btnB1).setOnClickListener { setBand("0x1", "B1") }
        findViewById<Button>(R.id.btnB3).setOnClickListener { setBand("0x4", "B3") }
        findViewById<Button>(R.id.btnB8).setOnClickListener { setBand("0x80", "B8") }
        findViewById<Button>(R.id.btnB40).setOnClickListener { setBand("0x8000000000", "B40") }
        findViewById<Button>(R.id.btnReset).setOnClickListener { setBand("0x1a0080800d7", "جميع الترددات") }
        findViewById<Button>(R.id.btn4GOnly).setOnClickListener { setNetworkMode("Only_LTE") }
        findViewById<Button>(R.id.btnReboot).setOnClickListener { rebootRouter() }
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02X".format(it) }
    }

    private fun loginAndFetch() {
        val time = System.currentTimeMillis()
        val req = Request.Builder().url("$baseUrl/goform/goform_get_cmd_process?isTest=false&cmd=RD,LD&_=$time").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvStatus.text = "الحالة: فشل الاتصال بالراوتر!" }
            }
            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val ld = json.optString("LD", "")
                val passHash = sha256Hex(sha256Hex("admin") + ld)
                val postBody = FormBody.Builder().add("isTest", "false").add("goformId", "LOGIN").add("password", passHash).build()
                val loginReq = Request.Builder().url("$baseUrl/goform/goform_set_cmd_process").post(postBody).build()
                client.newCall(loginReq).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) {
                        sessionCookie = response.headers("Set-Cookie").firstOrNull { it.contains("stok") }
                        runOnUiThread { tvStatus.text = "الحالة: تم تسجيل الدخول بنجاح" }
                        fetchDeviceInfo()
                    }
                })
            }
        })
    }

    private fun fetchDeviceInfo() {
        val time = System.currentTimeMillis()
        val cmds = "network_type,network_provider,cell_id,lte_band_lock,wa_inner_version"
        val req = Request.Builder().url("$baseUrl/goform/goform_get_cmd_process?isTest=false&cmd=$cmds&multi_data=1&_=$time")
            .apply { sessionCookie?.let { header("Cookie", it) } }.build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val infoText = """
                    • نوع الشبكة: ${json.optString("network_type", "N/A")}
                    • المزود: ${json.optString("network_provider", "N/A")}
                    • Cell ID المتصل: ${json.optString("cell_id", "N/A")}
                    • القناع الحالي: ${json.optString("lte_band_lock", "N/A")}
                """.trimIndent()
                runOnUiThread { tvInfo.text = infoText }
            }
        })
    }

    private fun setBand(mask: String, bandName: String) {
        tvStatus.text = "الحالة: جاري قفل التردد $bandName..."
        val postBody = FormBody.Builder().add("isTest", "false").add("goformId", "LTE_BAND_LOCK").add("lte_band_lock", mask).build()
        val req = Request.Builder().url("$baseUrl/goform/goform_set_cmd_process")
            .apply { sessionCookie?.let { header("Cookie", it) } }.post(postBody).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    tvStatus.text = "الحالة: تم تطبيق قفل التردد $bandName"
                }
            }
        })
    }

    private fun setNetworkMode(mode: String) {
        val postBody = FormBody.Builder().add("isTest", "false").add("goformId", "SET_BEARER_PREFERENCE").add("BearerPreference", mode).build()
        val req = Request.Builder().url("$baseUrl/goform/goform_set_cmd_process")
            .apply { sessionCookie?.let { header("Cookie", it) } }.post(postBody).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { tvStatus.text = "الحالة: تم الضبط على 4G Only" }
            }
        })
    }

    private fun rebootRouter() {
        val postBody = FormBody.Builder().add("isTest", "false").add("goformId", "REBOOT").build()
        val req = Request.Builder().url("$baseUrl/goform/goform_set_cmd_process")
            .apply { sessionCookie?.let { header("Cookie", it) } }.post(postBody).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { tvStatus.text = "الحالة: جاري إعادة التشغيل..." }
            }
        })
    }
}
