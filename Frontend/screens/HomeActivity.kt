package deo.raghav.medaware.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import deo.raghav.medaware.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

data class MedicineDisplay(val mname: String, val rtime: String, val dose: Int)

class HomeActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val serverUrl = "http://10.0.2.2:8080"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        recyclerView = findViewById(R.id.medicineRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        updateGreeting()
        setupClickListeners()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time user returns to this screen
        fetchDataFromServer()
    }
    private fun fetchDataFromServer() {
        // 1. Use the SAME utility class as LoginActivity to get the real UID
        val userPrefs = deo.raghav.medaware.utility.UserPrefs(this)
        val uid = userPrefs.getUid()
        if(uid== -1){
            Log.e("Medaware","User Not Logged in!")
            return
            
        }

        Thread {
            try {
                val fullList = mutableListOf<MedicineDisplay>()

                // 2. The backend expects a session, but you are sending a Body.
                // Since we can't change the Backend, we must ensure 'uid' in the body
                // matches what is in the DB for that user.
                val reminderBody = JSONObject().put("uid", uid)
                val reminderJson = makePostRequest("$serverUrl/get_reminders", reminderBody)

                Log.d("Medaware", "Server returned: $reminderJson")

                val remindersArray = JSONArray(reminderJson)


                for (i in 0 until remindersArray.length()) {
                    val rObj = remindersArray.getJSONObject(i)
                    val rid = rObj.getInt("rid")
                    val rtime = rObj.getString("rtime")

                    // 2. Fetch Medicines for this RID
                    val medicineBody = JSONObject().put("rid", rid).put("uid", uid)
                    val medicineJson = makePostRequest("$serverUrl/get_medicines", medicineBody)
                    val medsArray = JSONArray(medicineJson)

                    for (j in 0 until medsArray.length()) {
                        val mObj = medsArray.getJSONObject(j)
                        fullList.add(
                            MedicineDisplay(
                                mname = mObj.getString("mname"),
                                rtime = rtime,
                                dose = mObj.getInt("dose_qty")
                            )
                        )
                    }
                }

                // 3. Update UI on Main Thread
                runOnUiThread {
                

                recyclerView.adapter = MedicineAdapter(fullList)
                recyclerView.adapter?.notifyDataSetChanged()
            }


          }catch (e: Exception) {
                Log.e("Medaware", "Fetch Error: ${e.message}")
            }
        }.start()
    }

    private fun makePostRequest(urlStr: String, body: JSONObject): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept","application/json")
        conn.connectTimeout = 5000
        conn.doOutput = true
        conn.outputStream.use{os->
            os.write(body.toString().toByteArray(Charsets.UTF_8))
        }
        // Read response
        return try {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            Log.d("Medaware", "Response from $urlStr: $response")
            response
        } catch (e: Exception) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
            Log.e("Medaware", "Error Response: $error")
            "[]" // Return empty array on error to prevent crashes
        }
    }

    private fun updateGreeting() {
        val greetingText = findViewById<TextView>(R.id.greetingText)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            currentHour < 12 -> "Good Morning"
            currentHour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
        greetingText.text = "$greeting!"
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnAddMedicineReminder).setOnClickListener {
            startActivity(Intent(this, AddReminder::class.java))
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> true
                R.id.nav_reports -> {
                    startActivity(Intent(this, ReportsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    class MedicineAdapter(private val list: List<MedicineDisplay>) : RecyclerView.Adapter<MedicineAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.txtMedName)
            val details: TextView = v.findViewById(R.id.txtMedDetails)
            val time: TextView = v.findViewById(R.id.txtMedTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_medicine, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            h.name.text = item.mname
            h.details.text = "Dose: ${item.dose} Unit(s)"
            h.time.text = item.rtime
        }

        override fun getItemCount() = list.size
    }
}
