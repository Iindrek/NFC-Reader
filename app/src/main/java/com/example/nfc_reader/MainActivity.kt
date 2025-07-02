package com.example.nfc_reader

import android.app.PendingIntent
import android.content.Intent
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
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
    private lateinit var firmInput: EditText
    private lateinit var submitButton: Button

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        firmInput = findViewById(R.id.firmNameInput)
        textView = findViewById(R.id.nfcText)
        submitButton = findViewById(R.id.submitButton)

        firmInput.text.clear()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "Seade ei toeta NFC'd", Toast.LENGTH_LONG).show()
            finish()
        }

        submitButton.setOnClickListener {
            val firmName = firmInput.text.toString().trim()
            if (firmName.isNotEmpty()) {
                val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
                prefs.edit().putString("firmName", firmName).apply()
                Toast.makeText(this, "Firma nimi salvestatud", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Palun sisesta firma nimi", Toast.LENGTH_SHORT).show()
            }
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
        releaseMediaPlayer()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        playSound(R.raw.start_scan)

        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let {
            val tagId = it.id.joinToString("") { byte -> "%02X".format(byte) }
            val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
            val firmName = prefs.getString("firmName", "") ?: ""

            textView.text = "NFC Tag Leitud, kood:\nID: $tagId"

            // Send tag info
            sendTagToSheet(tagId, firmName) { success ->
                runOnUiThread {
                    if (success) {
                        playSound(R.raw.success)
                        Toast.makeText(this, "Andmed saadetud edukalt", Toast.LENGTH_SHORT).show()
                    } else {
                        playSound(R.raw.failure)
                        Toast.makeText(this, "Andmete saatmine ebaõnnestus", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun playSound(resId: Int) {
        releaseMediaPlayer()
        mediaPlayer = MediaPlayer.create(this, resId)
        mediaPlayer?.start()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

fun sendTagToSheet(tagId: String, firmName: String, callback: (Boolean) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("https://script.google.com/macros/s/AKfycbxO7IMdr_9HM0uiE7K_uq9kN9rtO9ioR5G0HbvE1qWnDjmsmxvr4uIJzU-qQ16_m-g/exec")
            val json = """
                {
                    "tagId": "$tagId",
                    "firm": "$firmName"
                }
            """.trimIndent()

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; utf-8")
                doOutput = true
                outputStream.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                    it.flush()
                }

                val responseCode = responseCode
                val response = inputStream.bufferedReader().readText()
                Log.d("NFCApp", "Sheet Response: $response")

                callback(responseCode == 200)
            }
        } catch (e: Exception) {
            Log.e("NFCApp", "Error sending to sheet: ${e.localizedMessage}", e)
            callback(false)
        }
    }
}
