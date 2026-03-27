package deo.raghav.medaware.screens

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import deo.raghav.medaware.R
import deo.raghav.medaware.networking.SocketManager
import deo.raghav.medaware.utility.Constants.ALARM_TIMEOUT
import org.json.JSONObject
import java.util.Locale

class AlarmActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var mname: String
    private var isAlarmActive: Boolean = true

    private val socketManager = SocketManager()
    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeoutTask = Runnable {
        handleMissedDose()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language (e.g., US English)
            val result = tts.setLanguage(Locale.UK)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language is not supported!")
            }

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    // When speech finishes, if alarm is still active, speak again
                    runOnUiThread {
                        if (isAlarmActive) {
                            speak()
                        }
                    }
                }
            })
            speak()

        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }

    private val rid by lazy {
        intent.getIntExtra("REMINDER_ID", -1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Tell the OS to show this even if the phone is locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_alarm)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tts = TextToSpeech(this, this)

        val reminder_name = findViewById<TextView>(R.id.reminder_name)
        val nameToShow = intent.getStringExtra("MNAME") ?: "Medication Time"
        mname = nameToShow
        reminder_name.text = buildString {
            append("Medicine : ")
            append(mname)
        }
        val dismiss = findViewById<Button>(R.id.dismiss)

        timeoutHandler.postDelayed(timeoutTask, ALARM_TIMEOUT)

        dismiss.setOnClickListener {
            isAlarmActive = false // Stop the loop
            stopEverything()

            val intent = Intent(this, MainActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("REMINDER_ID", rid)
            baseContext.startActivity(intent)
            finish()
        }
    }

    private fun speak() {
        if (!isAlarmActive) return

        val textToSpeak = "Time to take your medicine $mname"

        // Utterance ID is required for onDone to trigger
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "medicine_loop_id")

        tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "medicine_loop_id")
    }

    private fun handleMissedDose() {
        Log.d("ALARM_TIMEOUT", "User missed the dose")
        isAlarmActive = false // Stop the loop

        val sharedPreferences: SharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val uid = sharedPreferences.getInt("uid", 1)
        val data = JSONObject()
        data.put("uid", uid)
        data.put("rid", rid)
        socketManager.initialize()
        socketManager.connect()
        socketManager.sendEvent("missed", data)
        stopEverything()
        socketManager.disconnect()
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Reminder missed")
            builder.setMessage("This reminder was missed")
            builder.setPositiveButton("OK", null)
            builder.setPositiveButton("OK") { dialog, _ ->
                finish() // Close the alarm screen
            }
            builder.create().show()
        }

    }

    private fun stopEverything() {
        isAlarmActive = false // Stop the loop
        // Stop the timer
        timeoutHandler.removeCallbacks(timeoutTask)

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything() // Ensure no memory leaks or phantom music
        socketManager.disconnect()
    }
}