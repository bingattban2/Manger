package com.manger.app.domain

import java.text.Collator
import java.util.Locale

  
object EmployeeOrder {
    private val arabic = Locale.forLanguageTag("ar")

    fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ")

      
    fun key(name: String): String = clean(name).lowercase(arabic)
        .replace("ـ", "")
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("[إأآٱ]"), "ا")

    private fun comparator(): Comparator<String> {
        val collator = Collator.getInstance(arabic).apply { strength = Collator.PRIMARY }
        return Comparator { a, b -> collator.compare(key(a), key(b)) }
    }

    fun employees(list: List<Employee>): List<Employee> {
        val names = comparator()
        return list.sortedWith { a, b ->
            val comparison = names.compare(a.name, b.name)
            if (comparison != 0) comparison else a.id.compareTo(b.id)
        }
    }

}
