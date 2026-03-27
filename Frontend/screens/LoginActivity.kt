package deo.raghav.medaware.screens

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import deo.raghav.medaware.R
import deo.raghav.medaware.networking.HTTPManager
import deo.raghav.medaware.utility.UserPrefs
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already logged in before setting up UI
        if (UserPrefs(this).isLoggedIn()) {
            startHomeActivity()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        val emailEt = findViewById<EditText>(R.id.username_input)
        val passEt  = findViewById<EditText>(R.id.password_input)
        val loginBtn = findViewById<Button>(R.id.loginButton)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        loginBtn.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val password = passEt.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            lifecycleScope.launch {
                val responseJson = HTTPManager.POST("/login", json)

                if (responseJson != null) {
                    val successMsg = responseJson.optString("success")
                    val errorMsg = responseJson.optString("app_error")

                    if (successMsg.isNotEmpty()) {
                        val uid = responseJson.optInt("uid", -1)
                        if (uid != -1) {
                            UserPrefs(this@LoginActivity).saveUid(uid)
                            Toast.makeText(this@LoginActivity, successMsg, Toast.LENGTH_SHORT).show()
                            startHomeActivity()
                        }
                    } else if (errorMsg.isNotEmpty()) {
                        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                    } else {
                        // Fallback if keys are missing
                        val uid = responseJson.optInt("uid", -1)
                        if (uid != -1) {
                            UserPrefs(this@LoginActivity).saveUid(uid)
                            startHomeActivity()
                        }
                    }
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Login failed: Check credentials or server",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        tvGoToRegister.setOnClickListener {
            val intent = Intent(this@LoginActivity, RegistrationActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
