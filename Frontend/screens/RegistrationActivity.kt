package deo.raghav.medaware.screens

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import deo.raghav.medaware.R
import deo.raghav.medaware.networking.HTTPManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class RegistrationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registration)

        // edge-to-edge handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // views
        val emailEt = findViewById<EditText>(R.id.etEmail)
        val passEt = findViewById<EditText>(R.id.etPassword)
        val ageEt = findViewById<EditText>(R.id.etAge)
        val heightEt = findViewById<EditText>(R.id.etHeight)
        val weightEt = findViewById<EditText>(R.id.etWeight)
        val genderRg = findViewById<RadioGroup>(R.id.rgGender)
        val registerBtn = findViewById<Button>(R.id.btnRegister)
        val tvBackToLogin = findViewById<TextView>(R.id.tvBackToLogin)

        tvBackToLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        registerBtn.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val pass = passEt.text.toString().trim()
            val ageStr = ageEt.text.toString().trim()
            val hStr = heightEt.text.toString().trim()
            val wStr = weightEt.text.toString().trim()

            val selectedId = genderRg.checkedRadioButtonId
            val gender = when (selectedId) {
                R.id.rbMale -> "M"
                R.id.rbFemale -> "F"
                else -> ""
            }

            // basic validation
            if (email.isEmpty() || pass.isEmpty() || ageStr.isEmpty()
                || hStr.isEmpty() || wStr.isEmpty() || gender.isEmpty()
            ) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val age = ageStr.toIntOrNull() ?: 0
            val height = hStr.toDoubleOrNull() ?: 0.0
            val weight = wStr.toDoubleOrNull() ?: 0.0

            // build JSON body (keys must match backend)
            val json = JSONObject().apply {
                put("email", email)
                put("password", pass)
                put("age", age)
                put("gender", gender)
                put("height", height)
                put("weight", weight)
            }

            // call your HTTPManager
            lifecycleScope.launch {
                Log.d("Registration", "Starting registration request...")
                val responseJson = HTTPManager.POST("/register", json)

                if (responseJson != null) {
                    val successMsg = responseJson.optString("success")
                    val errorMsg = responseJson.optString("app_error")

                    if (successMsg.isNotEmpty()) {
                        Toast.makeText(this@RegistrationActivity, successMsg, Toast.LENGTH_SHORT).show()
                        
                        // Your register route doesn't return UID, only Login does.
                        // So we just go to login page.
                        val intent = Intent(this@RegistrationActivity, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else if (errorMsg.isNotEmpty()) {
                        Toast.makeText(this@RegistrationActivity, errorMsg, Toast.LENGTH_LONG).show()
                    } else {
                         // Fallback for success
                         Toast.makeText(this@RegistrationActivity, "Registration successful", Toast.LENGTH_SHORT).show()
                         val intent = Intent(this@RegistrationActivity, LoginActivity::class.java)
                         startActivity(intent)
                         finish()
                    }
                } else {
                    Log.e("Registration", "Registration failed: responseJson is null")
                    Toast.makeText(
                        this@RegistrationActivity,
                        "Registration failed. Please check if the server is running.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
