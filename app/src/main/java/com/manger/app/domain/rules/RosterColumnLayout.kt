package com.manger.app.domain

import java.util.Base64
import java.util.UUID

  
data class RosterColumn(val key: String, val title: String, val visible: Boolean = true) {
    val custom: Boolean get() = key.startsWith("custom_")
}

object RosterColumnLayout {
    val defaults = listOf(
        RosterColumn("ordinal", "الرقم"), RosterColumn("roster", "الرقم (ع)"),
        RosterColumn("name", "الاسم"), RosterColumn("site", "الموقع")
    )
    val labels = defaults.associate { it.key to it.title }
    private val id = Regex("custom_[a-z0-9]{12}")
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun create(): RosterColumn = RosterColumn("custom_" + UUID.randomUUID().toString().replace("-", "").take(12), "جديد")
    fun validate(value: List<RosterColumn>): List<RosterColumn> {
        require(value.size in 1..20) { "الحد الأقصى 20 عموداً ثابتاً" }
        require(value.map { it.key }.distinct().size == value.size) { "تكرار معرّف عمود" }
        require(value.all { it.key in labels || id.matches(it.key) }) { "معرّف عمود غير صالح" }
        require(value.any { it.key == "name" && it.visible }) { "عمود الاسم إلزامي" }
        require(value.all { it.title.trim().length in 1..42 && !it.title.contains('\n') }) { "أدخل اسماً صالحاً لكل عمود" }
        require(value.map { it.title.trim() }.distinct().size == value.size) { "يجب أن يكون اسم كل عمود مختلفاً" }
        return value.map { it.copy(title=it.title.trim()) }
    }
    fun sanitize(value: List<RosterColumn>): List<RosterColumn> = runCatching { validate(value) }.getOrElse { defaults }
      
    fun decode(stored: String?): List<RosterColumn> {
        if (stored.isNullOrBlank()) return defaults
        if (!stored.startsWith("v2|")) {
            val ordered = stored.split(',').distinct().filter { it in labels }
            return sanitize(ordered.map { RosterColumn(it, labels.getValue(it)) })
        }
        return runCatching {
            val decoded = stored.removePrefix("v2|").split('|').filter { it.isNotEmpty() }.map { token ->
                val s=token.split(':', limit=3)
                require(s.size==3 && s[1] in listOf("0", "1"))
                RosterColumn(s[0],String(decoder.decode(s[2]),Charsets.UTF_8),s[1]=="1")
            }
            validate(decoded)
        }.getOrElse { defaults }
    }
    fun encode(value: List<RosterColumn>) = "v2|" + validate(value).joinToString("|") {
        "${it.key}:${if (it.visible) 1 else 0}:${b64.encodeToString(it.title.toByteArray(Charsets.UTF_8))}"
    }
    fun label(column: RosterColumn): String = column.title
    fun value(column: RosterColumn, employee: Employee, ordinal: Int, extras: Map<Pair<Long,String>,String> = emptyMap()): String = when (column.key) {
        "ordinal" -> ordinal.toString().padStart(3, '0')
        "roster" -> employee.rosterNumber
        "name" -> employee.name
        "site" -> employee.site
        else -> extras[employee.id to column.key].orEmpty()
    }
}
