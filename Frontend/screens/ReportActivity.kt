package deo.raghav.medaware.screens

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import deo.raghav.medaware.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class ReportsActivity : AppCompatActivity() {

    // -- Palette --
    private val BG      = Color.parseColor("#F4F6FA")
    private val SURFACE = Color.WHITE
    private val PRIMARY = Color.parseColor("#1A1A2E")
    private val MUTED   = Color.parseColor("#6B7280")
    private val ACCENT  = Color.parseColor("#4F46E5")
    private val GREEN   = Color.parseColor("#10B981")
    private val RED     = Color.parseColor("#EF4444")
    private val BORDER  = Color.parseColor("#E5E7EB")

    private data class LogEntry(
        val medicineName: String,
        val status: String,
        val timestamp: String
    )

    private var sDateStr  = ""
    private var eDateStr    = ""
    private var searchQuery   = ""

    private lateinit var btnStart: Button
    private lateinit var btnEnd: Button
    private lateinit var logsContainer: LinearLayout

    @SuppressLint("NewApi", "ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val uid = sharedPref.getInt("uid", -1)

        // 1. Initialize Container
        logsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setPadding(dp(16), dp(8), dp(16), dp(40))
        }

        // 2. Main Wrapper
        val mainWrapper = RelativeLayout(this).apply {
            layoutParams = RelativeLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(BG)
        }

        // 3. Bottom Nav
        val bottomNav = BottomNavigationView(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(MATCH, dp(60)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
            setBackgroundColor(Color.WHITE)
            inflateMenu(R.menu.bottom_nav_menu)
            selectedItemId = R.id.nav_reports

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        startActivity(Intent(context, HomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        })
                        finish()
                        true
                    }
                    R.id.nav_reports -> true
                    else -> false
                }
            }
        }

        // 4. Scrollable Content
        val scroll = ScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(MATCH, MATCH).apply {
                addRule(RelativeLayout.ABOVE, bottomNav.id)
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(root)

        root.addView(buildTopBar())
        root.addView(buildFiltersCard(uid))
        root.addView(logsContainer)

        mainWrapper.addView(scroll)
        mainWrapper.addView(bottomNav)

        setContentView(mainWrapper)
        renderLogs(uid)
    }

    private fun buildTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(PRIMARY)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(40), dp(16), dp(16))

            val backBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_media_previous)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                setOnClickListener { finish() }
            }
            addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), 0, 0, 0)
            }
            col.addView(tv("Medication Reports", 18f, Color.WHITE, Typeface.BOLD))
            col.addView(tv("Daily record of taken & missed doses", 12f, Color.parseColor("#9CA3AF")))
            addView(col)
        }
    }

    private fun buildFiltersCard(uid: Int): CardView {
        return CardView(this).apply {
            radius = 0f
            cardElevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)

            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }

            val searchRow = LinearLayout(context).apply {
                background = roundedBg(BORDER, dp(8))
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
            }
            val searchEdit = EditText(context).apply {
                hint = "Search medicine name…"
                textSize = 13f
                background = null
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        searchQuery = s.toString().trim()
                        renderLogs(uid)
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
            searchRow.addView(searchEdit)
            inner.addView(searchRow)

            btnStart = dateBtn("Start Date") { raw, fmt -> sDateStr = raw; btnStart.text = fmt }
            btnEnd = dateBtn("End Date") { raw, fmt -> eDateStr = raw; btnEnd.text = fmt }

            val dateRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            dateRow.addView(buildDateTile(btnStart, "From"), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) })
            dateRow.addView(buildDateTile(btnEnd, "To"), LinearLayout.LayoutParams(0, WRAP, 1f))
            inner.addView(dateRow)

            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            actionRow.addView(Button(context).apply {
                text = "Search"; setTextColor(Color.WHITE); background = roundedBg(ACCENT, dp(8))
                setOnClickListener { renderLogs(uid) }
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) })

            actionRow.addView(Button(context).apply {
                text = "Reset"; background = roundedBg(BORDER, dp(8))
                setOnClickListener {
                    sDateStr = ""; eDateStr = ""; searchQuery = ""
                    btnStart.text = "Start Date"; btnEnd.text = "End Date"; searchEdit.setText("")
                    renderLogs(uid)
                }
            })
            inner.addView(actionRow)
            addView(inner)
        }
    }

    private fun buildDateTile(btn: Button, label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(BORDER, dp(8))
        setPadding(dp(10), dp(8), dp(10), dp(8))
        addView(tv(label, 10f, MUTED))
        btn.apply { textSize = 12f; background = null; setPadding(0, dp(2), 0, 0); gravity = Gravity.START }
        addView(btn)
    }

    private fun renderLogs(uid: Int) {
        if (sDateStr.isNotEmpty() && eDateStr.isNotEmpty() && eDateStr < sDateStr) {
            logsContainer.removeAllViews()
            logsContainer.addView(tv("⚠ Invalid Date Range", 14f, RED).apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0) })
            return
        }
        fetchLogsFromServer(uid)
    }

    private fun fetchLogsFromServer(uid: Int) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("uid", uid)
            put("sdate", if (sDateStr.isEmpty()) "2000-01-01" else sDateStr)
            put("edate", if (eDateStr.isEmpty()) "2099-12-31" else eDateStr)
        }

        val request = Request.Builder()
            .url("http://192.168.137.1:8080/get_log")
            .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@ReportsActivity, "Connection Failed", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val data = response.body?.string() ?: ""
                    try {
                        val jsonArray = JSONArray(data)
                        val list = mutableListOf<LogEntry>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)

                            // CRITICAL FIX: Get actual name from backend
                            val medName = obj.optString("medicine_name", "Medicine")

                            list.add(LogEntry(medName, obj.optString("status", "Missed"), obj.optString("date", "")))
                        }

                        // Apply Search Filter locally if needed
                        val filtered = if (searchQuery.isEmpty()) list else list.filter { it.medicineName.contains(searchQuery, true) }

                        runOnUiThread { renderLogsToUI(filtered) }
                    } catch (e: Exception) {
                        runOnUiThread { logsContainer.removeAllViews(); logsContainer.addView(tv("No Records Found", 14f, MUTED)) }
                    }
                }
            }
        })
    }

    private fun renderLogsToUI(logs: List<LogEntry>) {
        logsContainer.removeAllViews()
        if (logs.isEmpty()) {
            logsContainer.addView(tv("No records matching your search.", 15f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(40), 0, 0) })
            return
        }

        val grouped = groupByDay(logs)
        for ((day, items) in grouped) {
            logsContainer.addView(tv(day, 13f, ACCENT, Typeface.BOLD).apply { setPadding(0, dp(16), 0, dp(8)) })
            for (item in items) {
                logsContainer.addView(entryCard(item))
            }
        }
    }

    private fun groupByDay(logs: List<LogEntry>): Map<String, List<LogEntry>> {
        val dbFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.ENGLISH)
        val outFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        return logs.groupBy {
            try { outFmt.format(dbFmt.parse(it.timestamp)!!) } catch(e: Exception) { "Recent" }
        }
    }

    private fun entryCard(e: LogEntry): CardView {
        val taken = e.status.contains("Verified", true)
        val color = if (taken) GREEN else RED

        return CardView(this).apply {
            radius = dp(12).toFloat(); cardElevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }

            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(View(context).apply { setBackgroundColor(color); layoutParams = LinearLayout.LayoutParams(dp(5), dp(60)) })

            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            col.addView(tv(e.medicineName, 15f, PRIMARY, Typeface.BOLD))
            col.addView(tv(if (taken) "✓ Taken" else "✗ Missed", 13f, color))
            row.addView(col)
            addView(row)
        }
    }

    private fun dateBtn(label: String, onSet: (String, String) -> Unit) = Button(this).apply {
        text = label
        setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(this@ReportsActivity, { _, y, m, d ->
                val raw = String.format("%04d-%02d-%02d", y, m + 1, d)
                val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Calendar.getInstance().apply { set(y, m, d) }.time)
                onSet(raw, fmt)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun tv(t: String, s: Float, c: Int, st: Int = Typeface.NORMAL) = TextView(this).apply {
        text = t; textSize = s; setTextColor(c); typeface = Typeface.defaultFromStyle(st)
    }

    private fun roundedBg(color: Int, corner: Int) = GradientDrawable().apply { setColor(color); cornerRadius = corner.toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT
}
