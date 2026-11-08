package com.nova.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

class VoiceActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, intent.getStringExtra("lang") ?: "ar-SA")
        try {
            startActivityForResult(i, 1)
        } catch (e: Exception) {
            Toast.makeText(this, "الإملاء الصوتي غير متاح على جهازك", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val t = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!t.isNullOrBlank()) {
                KeyboardService.pendingVoice = t
                KeyboardService.instance?.postFlush()
            }
        }
        finish()
    }
}
