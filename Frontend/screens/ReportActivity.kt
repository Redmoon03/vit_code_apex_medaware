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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class ReportsActivity : AppCompatActivity() {

    // -- DB Config --
    private val DB_NAME    = "medaware"
    private val TABLE_LOGS = "reminder_logs"

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

    // Filter states
    private var sDateStr  = ""
    private var eDateStr    = ""
    private var searchQuery   = ""

    // Buttons kept as fields so we can reset their text
    private lateinit var btnStart: Button
    private lateinit var btnEnd: Button

    // Container that holds only the log entries
    private lateinit var logsContainer: LinearLayout

    @SuppressLint("NewApi", "ResourceType")
    @RequiresApi(Build.VERSION_CODES.FROYO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val uid = sharedPref.getInt("uid", -1)

        logsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Add this line to ensure the container expands as items are added
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setPadding(dp(16), dp(8), dp(16), dp(40))
        }

        // 1. Create the Main Wrapper (RelativeLayout)
        val mainWrapper = RelativeLayout(this).apply {
            layoutParams = RelativeLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(BG)
        }

        // 2. Create the Bottom Navigation View
        val bottomNav = BottomNavigationView(this).apply {
            id = View.generateViewId() // Generate ID to anchor the ScrollView above it
            layoutParams = RelativeLayout.LayoutParams(MATCH, dp(60)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
            setBackgroundColor(Color.WHITE)

            // This assumes you have R.menu.bottom_nav_menu defined
            inflateMenu(R.menu.bottom_nav_menu)
            selectedItemId = R.id.nav_reports

            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        val intent = Intent(context, HomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                    R.id.nav_reports -> true
                    else -> false
                }
            }
        }

        // 3. Create the ScrollView and anchor it ABOVE the BottomNav
        val scroll = ScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(MATCH, MATCH).apply {
                addRule(RelativeLayout.ABOVE, bottomNav.id)
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(root)

        // 4. Build UI Sections
        root.addView(buildTopBar())
        root.addView(buildFiltersCard(uid))
        root.addView(rule().apply {
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                setMargins(dp(16), 0, dp(16), 0)
            }
        })

        logsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(40))
        }
        root.addView(logsContainer)

        // 5. Assemble everything into the mainWrapper
        mainWrapper.addView(scroll)
        mainWrapper.addView(bottomNav)

        setContentView(mainWrapper)

        // Initial load
        renderLogs(uid)
    }

    // ─── Top App Bar ──────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.FROYO)
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
                setOnClickListener {
                    val intent = Intent(context, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    finish()
                }
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

    // ─── Filters Card ─────────────────────────────────────────────────────────

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun buildFiltersCard(uid: Int): CardView {
        return CardView(this).apply {
            radius = dp(0).toFloat()
            cardElevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)

            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }

            // Search bar
            val searchRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBg(BORDER, dp(8))
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
            }
            val searchIcon = TextView(context).apply {
                text = "🔍"; textSize = 14f
                setPadding(0, 0, dp(6), 0)
            }
            val searchEdit = EditText(context).apply {
                hint = "Search medicine name…"
                setHintTextColor(Color.parseColor("#9CA3AF"))
                setTextColor(PRIMARY)
                textSize = 13f
                background = null
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        searchQuery = s.toString().trim()
                        renderLogs(uid)
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
            searchRow.addView(searchIcon)
            searchRow.addView(searchEdit)
            inner.addView(searchRow)

            // Date pickers row
            btnStart = dateBtn("Start Date") { date, formatted ->
                sDateStr = date
                btnStart.text = formatted
            }
            btnEnd = dateBtn("End Date") { date, formatted ->
                eDateStr = date
                btnEnd.text = formatted
            }

            val dateRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
            }
            dateRow.addView(buildDateTile(btnStart, "Start Date"),
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) })
            dateRow.addView(buildDateTile(btnEnd, "End Date"),
                LinearLayout.LayoutParams(0, WRAP, 1f))
            inner.addView(dateRow)

            val btnSearch = Button(this@ReportsActivity).apply {
                text = "Search"
                setTextColor(Color.WHITE)
                background = roundedBg(ACCENT, dp(8))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) }
                setOnClickListener { renderLogs(uid) }
            }
            val btnReset = Button(this@ReportsActivity).apply {
                text = "Reset"
                setTextColor(MUTED)
                background = roundedBg(BORDER, dp(8))
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
                setOnClickListener {
                    sDateStr = ""; eDateStr = ""
                    btnStart.text = "Start Date"
                    btnEnd.text = "End Date"
                    searchEdit.setText("")
                    renderLogs(uid)
                }
            }
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actionRow.addView(btnSearch)
            actionRow.addView(btnReset)
            inner.addView(actionRow)

            addView(inner)
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun buildDateTile(btn: Button, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(BORDER, dp(8))
            setPadding(dp(10), dp(8), dp(10), dp(8))

            addView(tv(label, 10f, MUTED))
            btn.apply {
                textSize = 12f
                setTextColor(PRIMARY)
                setTypeface(null, Typeface.BOLD)
                background = null
                setPadding(0, dp(2), 0, 0)
                gravity = Gravity.START
            }
            addView(btn, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    // ─── Render Logs ──────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)

    private fun renderLogs(uid: Int) {
        // Basic validation
        if (sDateStr.isNotEmpty() && eDateStr.isNotEmpty() && eDateStr < sDateStr) {
            logsContainer.removeAllViews()
            logsContainer.addView(tv("⚠ End date must be after start date.", 14f, RED).apply {
                setPadding(0, dp(32), 0, 0); gravity = Gravity.CENTER
            })
            return
        }

        // Call the server (this replaces the 'Thread { fetchLogs }' block)
        fetchLogsFromServer(uid)
    }

    // Add this method to handle the list once it returns from the server

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun renderLogsToUI(logs: List<LogEntry>) {
        logsContainer.removeAllViews()

        if (logs.isEmpty()) {
            logsContainer.addView(tv("No records found.", 16f, MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, 0)
            })
            return
        }

        // Simple header count
        logsContainer.addView(tv("${logs.size} record(s) found", 13f, MUTED).apply {
            setPadding(0, 0, 0, dp(12))
        })

        for (log in logs) {
            // Colored left-border card
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(SURFACE)
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    bottomMargin = dp(8)
                }
            }

            // Colored bar on the left
            val bar = View(this).apply {
                setBackgroundColor(if (log.status.contains("Verified", ignoreCase = true)) GREEN else RED)
                layoutParams = LinearLayout.LayoutParams(dp(5), ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            col.addView(tv(log.medicineName, 15f, PRIMARY, Typeface.BOLD))
            col.addView(tv(log.status, 13f,
                if (log.status.contains("Verified", ignoreCase = true)) GREEN else RED))
            col.addView(tv(log.timestamp, 11f, MUTED))

            row.addView(bar)
            row.addView(col)
            logsContainer.addView(row)

            // Divider
            logsContainer.addView(rule())
        }
    }
    // ─── Database ─────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun fetchLogsFromServer(uid: Int) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("uid", uid)
            // Send valid date strings to prevent Python's strptime from crashing
            put("sdate", if (sDateStr.isEmpty()) "2000-01-01" else sDateStr)
            put("edate", if (eDateStr.isEmpty()) "2099-12-31" else eDateStr)
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        Log.d("Request body", json.toString())
        val request = Request.Builder()
            .url("http://192.168.137.1:8080/get_log")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    if (!isFinishing) {
                        Toast.makeText(this@ReportsActivity, "Check Connection", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                // .use ensures the response stream is closed properly
                response.use {
                    val responseData = response.body?.string() ?: ""

                    Log.d("Response body", responseData)

                    try {
                        // Check if response is actually an array
                        if (responseData.trim().startsWith("[")) {
                            val jsonArray = JSONArray(responseData)
                            Log.d("JSON_DATA", "Array Length: ${jsonArray.length()}")
                            val newList = mutableListOf<LogEntry>()

                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)

                                val rawDate = obj.optString("date", "")
                                val displayDate = try {
                                    val inFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                                    val outFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                                    outFmt.format(inFmt.parse(rawDate)!!)
                                } catch (e: Exception) {
                                    rawDate
                                }

                                newList.add(LogEntry(
                                    medicineName = "Log #${obj.optInt("logid", i + 1)}",
                                    status = obj.optString("status", "Unknown"),
                                    timestamp = displayDate
                                ))



                            }

                            runOnUiThread {
                                if (!isFinishing) renderLogsToUI(newList)
                            }
                        } else {
                            // Server sent an Error Object {} instead of a List []
                            runOnUiThread {
                                if (!isFinishing) {
                                    logsContainer.removeAllViews()
                                    logsContainer.addView(tv("No data or server error.", 14f, MUTED))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            if (!isFinishing) Toast.makeText(this@ReportsActivity, "Parsing Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }
    // The format your server/manual string uses
    private val ISO_FORMAT = "yyyy-MM-dd HH:mm:ss"
    // The format for grouping keys
    private val KEY_FORMAT = "yyyy-MM-dd"
    // The format for the UI header
    private val DISPLAY_FORMAT = "EEE, d MMM"
    private fun groupByDay(logs: List<LogEntry>): LinkedHashMap<String, MutableList<LogEntry>> {
        val dbFmt = SimpleDateFormat(ISO_FORMAT, Locale.getDefault())
        val keyFmt = SimpleDateFormat(KEY_FORMAT, Locale.getDefault())
        val dispFmt = SimpleDateFormat(DISPLAY_FORMAT, Locale.getDefault())

        val today = keyFmt.format(Date())
        val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }.let { keyFmt.format(it.time) }

        val map = LinkedHashMap<String, MutableList<LogEntry>>()

        for (log in logs) {
            // Try to parse, if it fails, use current date as a fallback to avoid losing data
            val date = try {
                dbFmt.parse(log.timestamp) ?: Date()
            } catch (e: Exception) {
                Date()
            }

            val key = keyFmt.format(date)
            val label = when (key) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> dispFmt.format(date)
            }
            map.getOrPut(label) { mutableListOf() }.add(log)
        }
        return map

    }

    // ─── UI Helpers ───────────────────────────────────────────────────────────

    private fun dateBtn(label: String, onDateSet: (raw: String, formatted: String) -> Unit) =
        Button(this).apply {
            text = label
            textSize = 12f
            setOnClickListener {
                val c = Calendar.getInstance()
                DatePickerDialog(this@ReportsActivity, { _, y, m, d ->
                    val raw = String.format("%04d-%02d-%02d", y, m + 1, d)
                    val cal = Calendar.getInstance().apply { set(y, m, d) }
                    val formatted = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(cal.time)
                    onDateSet(raw, formatted)
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

    private fun entryCard(e: LogEntry): CardView {
        val Verified = e.status.equals("Verified", true)
        val color    = if (Verified) GREEN else RED
        val timeFmt  = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dbFmt    = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeStr  = runCatching { timeFmt.format(dbFmt.parse(e.timestamp)!!) }.getOrDefault(e.timestamp)

        return CardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(View(context).apply {
                setBackgroundColor(color)
                layoutParams = LinearLayout.LayoutParams(dp(5), dp(60))
            })
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            col.addView(tv(e.medicineName, 15f, PRIMARY, Typeface.BOLD))
            col.addView(tv(if (Verified) "✓ Taken" else "✗ Missed", 13f, color))
            row.addView(col)
            row.addView(tv(timeStr, 13f, MUTED).apply { setPadding(0, 0, dp(16), 0) })
            addView(row)
        }
    }

    private fun pillRow(taken: Int, missed: Int) = LinearLayout(this).apply {
        @SuppressLint("NewApi")
        fun pill(label: String, bg: Int) = TextView(context).apply {
            text = label; textSize = 11f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedBg(bg, dp(20))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(6) }
        }
        setPadding(0, 0, 0, dp(8))
        addView(pill("$taken Taken", GREEN))
        addView(pill("$missed Missed", RED))
    }

    private fun roundedBg(color: Int, cornerPx: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = cornerPx.toFloat()
    }

    private fun tv(t: String, s: Float, c: Int, st: Int = Typeface.NORMAL) =
        TextView(this).apply {
            text = t; textSize = s; setTextColor(c); typeface = Typeface.defaultFromStyle(st)
        }

    private fun rule() = View(this).apply {
        setBackgroundColor(BORDER)
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT
}
