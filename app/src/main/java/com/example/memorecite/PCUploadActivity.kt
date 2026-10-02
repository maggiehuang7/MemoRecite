package com.example.memorecite

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class PCUploadActivity : AppCompatActivity() {

    private var server: PCWebServer? = null
    private lateinit var tvStatus: TextView
    private lateinit var tvUrl: TextView
    private lateinit var btnToggle: Button
    private lateinit var etGroupName: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pc_upload)

        tvStatus = findViewById(R.id.tvStatus)
        tvUrl = findViewById(R.id.tvUrl)
        btnToggle = findViewById(R.id.btnToggle)
        etGroupName = findViewById(R.id.etGroupName)

        etGroupName.setText(getString(R.string.pc_group_hint))

        btnToggle.setOnClickListener { toggleServer() }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun toggleServer() {
        if (server != null) stopServer() else startServer()
    }

    private fun startServer() {
        val ip = getLocalIp() ?: run {
            Toast.makeText(this, getString(R.string.pc_no_wifi), Toast.LENGTH_LONG).show()
            return
        }

        val defaultGroup = etGroupName.text.toString().trim().ifBlank {
            getString(R.string.pc_group_hint)
        }

        try {
            val s = PCWebServer(
                appContext = applicationContext,
                defaultGroupName = defaultGroup,
                onUploaded = { count ->
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            getString(R.string.pc_received, count),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                port = 8080
            )
            s.start(5000, false)
            server = s

            tvUrl.text = "http://$ip:8080"
            tvStatus.text = getString(R.string.pc_running)
            btnToggle.text = getString(R.string.pc_stop)
            etGroupName.isEnabled = false

        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.pc_start_failed, e.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopServer() {
        try { server?.stop() } catch (_: Exception) {}
        server = null

        tvUrl.text = getString(R.string.pc_stopped_url)
        tvStatus.text = getString(R.string.pc_stopped)
        btnToggle.text = getString(R.string.pc_start)
        etGroupName.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    private fun getLocalIp(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val sorted = interfaces.sortedByDescending {
                it.name.startsWith("wlan") || it.name.startsWith("en")
            }
            for (intf in sorted) {
                if (!intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}