package com.manger.app.data.remote

import com.manger.app.data.local.SyncPendingEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

// SERVER: Production transport requires a user JWT issued by Supabase Auth; never use a secret key on Android.
data class RemoteEndpoint(val projectUrl: String, val publishableKey: String) {
    init {
        require(projectUrl.startsWith("https://") && projectUrl.substringAfter("https://").isNotBlank())
        require(publishableKey.isNotBlank())
    }
}

data class RemoteChange(
    val cursor: Long, val siteId: String?, val entity: String, val recordId: String,
    val action: String, val payload: String
)

class RemoteConflictException(message: String) : IllegalStateException(message)
class RemotePermissionException(message: String) : IllegalStateException(message)

interface RemoteDeltaGateway {
    suspend fun pull(after: Long, limit: Int = 200): List<RemoteChange>
    suspend fun pushAttendance(event: SyncPendingEvent): Boolean
    suspend fun pushAdmin(event: SyncPendingEvent): Boolean
    suspend fun columnDefinitions(): JSONArray
}

// SERVER: The gateway has no access to local demo passwords or service_role credentials.
class SupabaseRestGateway(
    private val endpoint: RemoteEndpoint,
    private val accessToken: () -> String?
) : RemoteDeltaGateway {
    private fun connection(path: String, method: String): HttpURLConnection {
        val token = accessToken()?.takeIf { it.isNotBlank() }
            ?: throw RemotePermissionException("يلزم الدخول بحساب مصادق عليه من Supabase")
        val base = endpoint.projectUrl.trimEnd('/')
        val c = URL("$base$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 25_000
        c.setRequestProperty("apikey", endpoint.publishableKey)
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        return c
    }
    private fun request(path: String, method: String = "GET", body: JSONObject? = null): String {
        val c = connection(path,method)
        try {
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = c.responseCode
            val result = (if (status in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status == 409 || status == 412) throw RemoteConflictException("تعارض تعديل مع نسخة أحدث على السيرفر")
            if (status == 401 || status == 403) throw RemotePermissionException("رفض السيرفر الصلاحية أو انتهت الجلسة")
            if (status !in 200..299) throw IllegalStateException("خطأ الاتصال بالسيرفر ($status): ${result.take(200)}")
            return result
        } finally { c.disconnect() }
    }
    override suspend fun pull(after: Long, limit: Int): List<RemoteChange> = withContext(Dispatchers.IO) {
        require(after >= 0)
        val page = limit.coerceIn(1,500)
        // SERVER: RLS limits records to the caller's authorized sites and global schema changes.
        val arr = JSONArray(request("/rest/v1/manger_change_log?select=id,site_id,entity,record_id,action,payload&id=gt.$after&order=id.asc&limit=$page"))
        (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            RemoteChange(e.getLong("id"),e.optString("site_id").takeIf { it.isNotBlank() && it != "null" },
                e.getString("entity"), e.getString("record_id"),e.getString("action"),
                e.optJSONObject("payload")?.toString() ?: "{}")
        }
    }
    override suspend fun pushAttendance(event: SyncPendingEvent): Boolean = withContext(Dispatchers.IO) {
        require(event.entity == "attendance") { "المشرف يرسل تغييرات التحضير فقط" }
        val payload = JSONObject(event.payload)
        val number = payload.optString("roster_number")
        require(number.isNotBlank() && !number.startsWith("local:")) {
            "يلزم رقم (ع) رسمي قبل إرسال التحضير"
        }
        val id = UUID.fromString(event.eventId)
        val body = JSONObject()
            .put("p_event_id",id.toString())
            .put("p_roster_number",number)
            .put("p_day",payload.getString("date"))
            .put("p_status",payload.getString("status"))
            .put("p_base_revision",payload.optLong("base_revision").takeIf { payload.has("base_revision") } ?: JSONObject.NULL)
        // SERVER: The client must verify the exact event ID in the committed RPC response before ACK.
        val response = JSONObject(request("/rest/v1/rpc/manger_apply_attendance_event","POST",body))
        response.optBoolean("accepted",false) && response.optString("event_id")==event.eventId
    }
    override suspend fun pushAdmin(event: SyncPendingEvent): Boolean = withContext(Dispatchers.IO) {
        require(event.role == "ADMIN") { "لا يمكن رفع تعديلات الإدارة من حساب مشرف" }
        require(event.entity in setOf("sites","employees","roster_layout","roster_custom_value",
            "attendance_window","permissions","statuses")) {
            "هذه العملية تحتاج إلى واجهة خادم مخصصة: ${event.entity}"
        }
        val id = UUID.fromString(event.eventId)
        val body = JSONObject()
            .put("p_event_id",id.toString()).put("p_entity",event.entity)
            .put("p_operation",event.operation).put("p_record_key",event.recordKey)
            .put("p_site_name",event.site ?: JSONObject.NULL)
            .put("p_payload",JSONObject(event.payload))
            .put("p_base_revision",JSONObject.NULL)
        val response = JSONObject(request("/rest/v1/rpc/manger_apply_admin_event","POST",body))
        response.optBoolean("accepted",false) && response.optString("event_id")==event.eventId
    }
    override suspend fun columnDefinitions(): JSONArray = withContext(Dispatchers.IO) {
        JSONArray(request("/rest/v1/manger_column_definitions?select=*&order=position.asc"))
    }
}
