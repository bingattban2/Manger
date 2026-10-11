package com.manger.app.data.remote

import com.manger.app.domain.ImportedEmployee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

// SERVER: The importer uses a staging job; final commit is atomic and administrator-only.
class ImportJobGateway(
    private val endpoint: RemoteEndpoint,
    private val accessToken: () -> String?
) {
    private fun send(path: String, payload: String, prefer: String = "return=representation"): String {
        val token = accessToken()?.takeIf { it.isNotBlank() } ?: error("لا توجد جلسة مصادقة للسيرفر")
        val c = URL(endpoint.projectUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod="POST"; c.connectTimeout=15_000;c.readTimeout=60_000;c.doOutput=true
        c.setRequestProperty("apikey",endpoint.publishableKey)
        c.setRequestProperty("Authorization","Bearer $token")
        c.setRequestProperty("Content-Type","application/json")
        c.setRequestProperty("Prefer",prefer)
        try {
            c.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            val status=c.responseCode
            val reply=(if(status in 200..299)c.inputStream else c.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "رفض السيرفر العملية ($status): ${reply.take(250)}" }
            return reply
        } finally { c.disconnect() }
    }
    suspend fun uploadAndCommit(ownerAuthId: String, records: List<ImportedEmployee>,
                                onProgress: (Int,Int)->Unit = {_,_->}): JSONObject = withContext(Dispatchers.IO) {
        require(records.isNotEmpty() && records.size <= 20_000)
        require(records.all { it.rosterNumber.isNotBlank() && it.name.isNotBlank() && it.site.isNotBlank() })
        require(records.map { it.rosterNumber }.toSet().size==records.size) { "الرقم (ع) مكرر داخل الكشف" }
        val jobId=UUID.randomUUID().toString()
        send("/rest/v1/manger_import_jobs",JSONObject().put("id",jobId).put("owner",ownerAuthId).toString())
        records.chunked(100).forEachIndexed { batch, items ->
            val arr=JSONArray()
            items.forEachIndexed { index, employee ->
                arr.put(JSONObject().put("job_id",jobId).put("row_no",batch*100+index+1)
                    .put("roster_number",employee.rosterNumber).put("full_name",employee.name)
                    .put("site_name",employee.site))
            }
            send("/rest/v1/manger_import_rows",arr.toString(),"return=minimal")
            onProgress(minOf((batch+1)*100, records.size),records.size)
        }
        JSONObject(send("/rest/v1/rpc/manger_commit_import",JSONObject().put("p_job_id",jobId).toString()))
    }
}
