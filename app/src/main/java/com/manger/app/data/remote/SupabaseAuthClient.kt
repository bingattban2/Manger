package com.manger.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// SERVER: Username authentication requires server provisioning using a domain controlled by the organization.
data class RemoteIdentity(val authId: String, val accessToken: String, val refreshToken: String, val expiresIn: Long)

class SupabaseAuthClient(private val endpoint: RemoteEndpoint, private val identityDomain: String) {
    init { require(identityDomain.contains('.') && !identityDomain.contains('/')) }
    suspend fun signIn(username: String, password: String): RemoteIdentity = withContext(Dispatchers.IO) {
        val user = username.trim().lowercase(java.util.Locale.ROOT)
        require(user.matches(Regex("[a-z0-9_.-]{3,60}"))) { "اسم المستخدم غير صالح" }
        require(password.isNotBlank())
        val email="$user@$identityDomain"
        val connection=URL(endpoint.projectUrl.trimEnd('/') + "/auth/v1/token?grant_type=password")
            .openConnection() as HttpURLConnection
        connection.requestMethod="POST";connection.doOutput=true
        connection.connectTimeout=15_000;connection.readTimeout=20_000
        connection.setRequestProperty("apikey",endpoint.publishableKey)
        connection.setRequestProperty("Content-Type","application/json")
        try {
            val payload=JSONObject().put("email",email).put("password",password).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code=connection.responseCode
            val content=(if(code in 200..299)connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use {it.readText()}.orEmpty()
            if(code !in 200..299) throw RemotePermissionException("لم يتم قبول بيانات الدخول إلى السيرفر ($code)")
            val root=JSONObject(content)
            val id=root.getJSONObject("user").getString("id")
            RemoteIdentity(id,root.getString("access_token"),root.getString("refresh_token"),
                root.optLong("expires_in"))
        } finally { connection.disconnect() }
    }
}
