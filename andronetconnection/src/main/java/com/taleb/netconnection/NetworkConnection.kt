package com.taleb.netconnection

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.StrictMode
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit


class NetworkConnection(private val delegate: INetworkConnection? = null, private val context: Context) {

    private var okHttpBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
    private var client: OkHttpClient

    init {
        okHttpBuilder.connectTimeout(15, TimeUnit.SECONDS)
        client = okHttpBuilder.build()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
    }

    fun post(parameters: HashMap<String, String>, url: String, requestCode: Int) {
        //todo (add post type (json or multipart) in the future if needed)
        if (!verifyAvailableNetwork(context)) {
            delegate?.onFailure(requestCode, com.taleb.netconnection.Error(NETWORK_NOT_REACHABLE_ERROR_CODE,context.getString(R.string.verify_you_network_connectivity)))
            return
        }
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        for ((key, value) in parameters) {
            requestBody.addFormDataPart(key, value)
        }
        val request = Request.Builder()
            .url(url)
            .post(requestBody.build())
            .build()
        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                //todo handle error
                val mainHandler = Handler(context.mainLooper)
                mainHandler.post {
                    kotlin.run {
                        e.printStackTrace()
                        delegate?.onFailure(requestCode, com.taleb.netconnection.Error(API_CALL_FAIL_ERROR_CODE,context.getString(R.string.server_connection_error)))
                    }
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                val mainHandler = Handler(context.mainLooper)
                mainHandler.post {
                    kotlin.run {
                        handleResponse(response, requestCode)
                    }
                }
            }
        })

    }

    private fun verifyAvailableNetwork(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    private fun handleResponse(response: Response, requestCode: Int) {
        try {
            when (response.code()) {
                200 -> {
                    val bodyString = response.body()?.string() ?: "{}"
                    val json = JSONObject(bodyString)
                    delegate?.onSuccess(requestCode, json)
                }
                201 -> {
                    val json = JSONObject(response.body()?.string() ?: "{}")
                    delegate?.onSuccess(requestCode, json)
                }
                401 -> {
                    //todo (handle 401 response message to show for user)
                    delegate?.onFailure(requestCode, com.taleb.netconnection.Error(401,response.message()))
                }
                403 -> {
                    //todo (handle 401 response message to show for user)
                    delegate?.onFailure(requestCode, com.taleb.netconnection.Error(403,response.message()))
                }
                404 -> {
                    delegate?.onFailure(requestCode, com.taleb.netconnection.Error(404,context.getString(R.string.server_connection_error)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            delegate?.onFailure(requestCode, com.taleb.netconnection.Error(NONE_DEFINED_EXCEPTION_ERROR_CODE,context.getString(R.string.exception_in_gathering_info)))
        }
    }

    interface INetworkConnection {
        fun onSuccess(requestCode: Int, json: JSONObject)
        fun onFailure(requestCode: Int, error: Error)
    }

    companion object{
        const val NETWORK_NOT_REACHABLE_ERROR_CODE = -2
        const val NONE_DEFINED_EXCEPTION_ERROR_CODE = -1
        const val API_CALL_FAIL_ERROR_CODE = -3
    }
}