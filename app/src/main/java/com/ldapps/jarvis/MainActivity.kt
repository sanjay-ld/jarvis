package com.ldapps.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tapText: TextView
    private lateinit var pauseBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var micBtn: ImageButton
    private lateinit var bubbleBtn: Button

    private var isPaused = false
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tapText = findViewById(R.id.tapText)
        pauseBtn = findViewById(R.id.pauseBtn)
        cancelBtn = findViewById(R.id.cancelBtn)
        micBtn = findViewById(R.id.micBtn)
        bubbleBtn = findViewById(R.id.startBubbleBtn)

        checkPermission()

        tts = TextToSpeech(this) {
            tts.language = Locale.ENGLISH
        }

        micBtn.setOnClickListener {
            if (!isPaused) listenUser()
        }

        pauseBtn.setOnClickListener {
            isPaused = true
            stopMicAnimation()
            tapText.text = "Jarvis Paused"
            speak("Paused")
        }

        cancelBtn.setOnClickListener {
            isPaused = false
            stopMicAnimation()
            tapText.text = "Tap mic to talk to Jarvis"
            speak("Cancelled")
        }

        // ⭐ Floating Bubble Button ⭐
        bubbleBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(this)
            ) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                startService(Intent(this, FloatingViewService::class.java))
                speak("Floating bubble enabled")
                tapText.text = "Floating bubble enabled"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isPaused) {
            tapText.text = "Tap mic to talk to Jarvis"
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startMicAnimation() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.mic_pulse)
        micBtn.startAnimation(animation)
    }

    private fun stopMicAnimation() {
        micBtn.clearAnimation()
    }

    private fun listenUser() {
        tapText.text = "Listening..."
        speak("Listening")
        startMicAnimation()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH)

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                stopMicAnimation()
                val spoken =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                tapText.text = spoken ?: "Try Again"
                if (spoken != null) openApp(spoken)
            }

            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onError(p0: Int) {
                stopMicAnimation()
                tapText.text = "Try Again"
                speak("Please try again")
            }
        })

        recognizer.startListening(intent)
    }

    private fun openApp(userSpeech: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val speech = userSpeech.lowercase()

        var bestAppPackage = ""
        var bestScore = 0

        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val score = levenshtein(speech, label)

            if (score > bestScore) {
                bestScore = score
                bestAppPackage = app.packageName
            }
        }

        when {
            "youtube" in speech -> bestAppPackage = "com.google.android.youtube"
            "camera" in speech -> bestAppPackage = "com.android.camera"
            "chrome" in speech -> bestAppPackage = "com.android.chrome"
            "whatsapp" in speech -> bestAppPackage = "com.whatsapp"
            "instagram" in speech -> bestAppPackage = "com.instagram.android"
            "chatgpt" in speech -> bestAppPackage = "com.openai.chatgpt"
        }

        if (bestAppPackage != "") {
            val launch = pm.getLaunchIntentForPackage(bestAppPackage)
            if (launch != null) {
                val appName = pm.getApplicationLabel(
                    pm.getApplicationInfo(bestAppPackage, 0)
                ).toString()

                tapText.text = "Opening $appName"
                speak("Opening $appName")
                startActivity(launch)
                return
            }
        }

        tapText.text = "App not found"
        speak("Sorry, I could not find that app")
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return (100 - (dp[a.length][b.length].toFloat() /
                maxOf(a.length, b.length) * 100)).toInt()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }
    }
}
