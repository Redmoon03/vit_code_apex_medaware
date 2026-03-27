package deo.raghav.medaware.networking

import android.util.Log
import deo.raghav.medaware.utility.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

object HTTPManager {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .build()
    }

    suspend fun POST(endpoint: String, requestJSON: JSONObject): JSONObject? {
        var result: JSONObject? = null
        withContext(Dispatchers.IO) {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = RequestBody.create(mediaType, requestJSON.toString())
            val request = Request.Builder()
                .url("http://${Constants.HOST}:${Constants.PORT}" + endpoint)
                .post(requestBody)
                .build()
            try {
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonData: String? = response.body?.string()
                    if (jsonData != null) {
                        Log.d("Received response", jsonData)
                        //val jsonObject: JSONObject = JSONObject(jsonData)
                        result = JSONObject(jsonData)
                        response.close()
                    } else {
                        println("Request is successful but response is empty")
                    }
                } else {
                    print("Response code is : ${response.code}")
                }
                response.close()
            } catch (e: Exception) {
                println("Exception occurred : ${e.message}")
            }
        }
        return result
    }
}