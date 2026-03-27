package deo.raghav.medaware.screens

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import deo.raghav.medaware.networking.HTTPManager
import deo.raghav.medaware.R
import deo.raghav.medaware.utility.Utilities
import deo.raghav.medaware.utility.Utilities.checkOverlayPermission
import kotlinx.coroutines.launch
import org.json.JSONObject

class AddReminder : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_reminder)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val mname: EditText = findViewById<EditText>(R.id.mname)
        val mdqty: EditText = findViewById<EditText>(R.id.mdqty)
        val mtqty: EditText = findViewById<EditText>(R.id.mtqty)
        val rtime: TextView = findViewById<TextView>(R.id.rtime)
        val add_reminder: Button = findViewById<Button>(R.id.add_reminder)
        var chosenTime: String = ""
        var chosenHour: Int = -1
        var chosenMinute: Int = -1
        rtime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H) // Best for Python/SQL compatibility
                .setHour(12)
                .setMinute(0)
                .setTitleText("Select Medication Time")
                .build()

            picker.show(supportFragmentManager, "ALARM_PICKER")

            picker.addOnPositiveButtonClickListener {
                val h = picker.hour
                val m = picker.minute

                chosenHour = h
                chosenMinute = m

                rtime.text = String.format("%02d:%02d:00", h, m)
                chosenTime = String.format("%02d:%02d:00", h, m)
        }
        add_reminder.setOnClickListener {
            val medicineName = mname.text.toString()
            lifecycleScope.launch {
                val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                val data = JSONObject()
                data.put("uid", sharedPreferences.getInt("uid", 1))
                data.put("rtime", chosenTime)
                val result: JSONObject? = HTTPManager.POST("/add_reminder", data)
                var rid = -1
                if (result != null) {
                    rid = result.getInt("rid")
                } else {
                    Log.e("Error", "Reminder id response is empty")
                }

                val newData = JSONObject()
                newData.put("uid", sharedPreferences.getInt("uid", 1))
                newData.put("mname", mname.text.toString())
                newData.put("dose_qty", mdqty.text.toString().toInt())
                newData.put("total_qty", mtqty.text.toString().toInt())
                newData.put("rid", rid)
                val newResult: JSONObject? = HTTPManager.POST("/add_medicine", newData)

                if (!Settings.canDrawOverlays(this@AddReminder)) {
                    Toast.makeText(this@AddReminder, "Display over other apps permission required", Toast.LENGTH_SHORT).show()
                    checkOverlayPermission(this@AddReminder)
                }


               // Utilities.scheduleAlarm(this@AddReminder, mname.text.toString(), rid, chosenHour, chosenMinute, isReschedule = false)
                Utilities.scheduleAlarm(this@AddReminder, medicineName, rid, chosenHour, chosenMinute, isReschedule = false)
                if (newResult != null) {
                    Toast.makeText(this@AddReminder, "Added reminder successfully", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}




}