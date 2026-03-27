package deo.raghav.medaware.utility

import android.content.Context

class UserPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_UID = "uid"
    }

    // Save UID
    fun saveUid(uid: Int) {
        prefs.edit().putInt(KEY_UID, uid).apply()
    }

    // Get UID
    fun getUid(): Int {
        return prefs.getInt(KEY_UID, -1)
    }

    // Check if user logged in
    fun isLoggedIn(): Boolean {
        return getUid() != -1
    }

    // Logout user
    fun logout() {
        prefs.edit().clear().apply()
    }
}

