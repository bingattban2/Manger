package com.manger.app.domain

import java.text.Collator
import java.util.Locale

  
object MonthSiteFilter {
      
    fun sites(data: MonthData): List<String> {
        val arabic = Collator.getInstance(Locale.forLanguageTag("ar")).apply {
            strength = Collator.PRIMARY
        }
        return data.employees.map { it.site.trim() }.filter { it.isNotBlank() }.distinct()
            .sortedWith { a, b ->
                when {
                    a.isEmpty() && b.isNotEmpty() -> 1
                    b.isEmpty() && a.isNotEmpty() -> -1
                    else -> arabic.compare(a, b)
                }
            }
    }

    fun select(data: MonthData, site: String?): MonthData {
        if (site == null) return data
        val employees = data.employees.filter { it.site.trim() == site }
        val ids = employees.mapTo(HashSet(employees.size)) { it.id }
        val marks = data.marks.filterKeys { (employeeId, _) -> employeeId in ids }
        return data.copy(employees = employees, marks = marks,
            columnValues = data.columnValues.filterKeys { it.first in ids })
    }

    fun label(site: String?): String = when (site) {
        null -> "جميع المواقع"
        "" -> "بدون موقع"
        else -> site
    }

      
    fun filePart(site: String?): String = if (site == null) "جميع_المواقع" else {
        label(site).map { ch ->
            if (ch.code < 32 || ch == '/' || ch == '\\' || ch == ':' || ch == '*' ||
                ch == '?' || ch == '"' || ch == '<' || ch == '>' || ch == '|') '_' else ch
        }.joinToString("").replace(Regex("\\s+"), "_").take(50)
    }
}
