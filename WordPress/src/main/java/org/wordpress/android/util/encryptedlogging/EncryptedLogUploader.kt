package org.wordpress.android.util.encryptedlogging

import android.content.Context
import com.android.volley.NetworkResponse
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.Response.ErrorListener
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import org.wordpress.android.BuildConfig
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T

private const val AUTHORIZATION_HEADER = "HTTP_AUTHORIZATION"
private const val CONTENT_TYPE_HEADER = "Content-Type"
private const val CONTENT_TYPE_JSON = "application/json"
private const val UUID_HEADER = "log-uuid"
private const val UPLOAD_URL = "https://public-api.wordpress.com/rest/v1.1/encrypted-logging"
private const val TEST_UUID = "" // omitted
private const val TEST_FILE = """ // omitted
"""

class EncryptedLogUploader {
    fun test(context: Context) {
        val queue = Volley.newRequestQueue(context)
        val request = EncryptedLogUploadRequest(Response.Listener {
            AppLog.i(T.API, "Test logs uploaded")
        }, ErrorListener {
            AppLog.i(T.API, "Failed to upload test logs")
        })
        queue.add(request)
    }
}

class EncryptedLogUploadRequest(
    private val successListener: Response.Listener<NetworkResponse>,
    errorListener: ErrorListener
) : Request<NetworkResponse>(Method.POST, UPLOAD_URL, errorListener) {
    override fun getHeaders(): Map<String, String> {
        return mapOf(
                CONTENT_TYPE_HEADER to CONTENT_TYPE_JSON,
                AUTHORIZATION_HEADER to BuildConfig.OAUTH_APP_SECRET,
                UUID_HEADER to TEST_UUID
        )
    }

    override fun getBody(): ByteArray {
        return TEST_FILE.toByteArray()
    }

    override fun parseNetworkResponse(response: NetworkResponse?): Response<NetworkResponse> {
        return try {
            Response.success(response, HttpHeaderParser.parseCacheHeaders(response))
        } catch (e: Exception) {
            try {
                val json = JSONObject(response.toString())
                val errorMessage = json.getString("message")
                Response.error(VolleyError(errorMessage))
            } catch (jsonParsingError: Throwable) {
                Response.error(ParseError(jsonParsingError))
            }
        }
    }

    override fun deliverResponse(response: NetworkResponse) {
        successListener.onResponse(response)
    }
}
