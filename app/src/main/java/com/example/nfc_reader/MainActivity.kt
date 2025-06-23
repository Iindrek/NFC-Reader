package com.example.nfc_reader

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.nfcText)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "Seade ei toeta NFC'd", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let {
            val tagId = it.id.joinToString("") { byte -> "%02X".format(byte) }
            textView.text = "NFC Tag Leitud, kood:\nID: $tagId"
            sendTagToSheet(tagId)
        }
    }
}


fun sendTagToSheet(tagId: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("https://script.google.com/macros/s/AKfycbxO7IMdr_9HM0uiE7K_uq9kN9rtO9ioR5G0HbvE1qWnDjmsmxvr4uIJzU-qQ16_m-g/exec")
            val json = """
                {
                    "tagId": "$tagId"
                }
            """.trimIndent()

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; utf-8")
                doOutput = true
                outputStream.write(json.toByteArray(Charsets.UTF_8))

                val response = inputStream.bufferedReader().readText()
                Log.d("NFCApp", "Sheet Response: $response")
            }
        } catch (e: Exception) {
            Log.e("NFCApp", "Error sending to sheet: ${e.localizedMessage}", e)
        }
    }
}
