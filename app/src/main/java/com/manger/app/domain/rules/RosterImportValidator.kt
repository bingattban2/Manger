package com.manger.app.domain

  
object RosterImportValidator {
    fun inspect(records: List<ImportedEmployee>, employees: List<Employee>): ImportCheck {
        require(records.isNotEmpty()) { "ملف الكشف فارغ" }
        val existing = employees.associateBy { it.rosterNumber }.filterKeys { it.isNotBlank() }
        val legacy = employees.filter { it.rosterNumber.isBlank() }
            .map { EmployeeOrder.key(it.name) to it.site.trim() }.toSet()
        val used = HashSet<String>()
        val warnings = mutableListOf<String>()
        var added = 0; var updated = 0; var unchanged = 0
        records.forEachIndexed { index, row ->
            val number = RosterNumbers.clean(row.rosterNumber)
            require(used.add(number)) {
                "تكرر الرقم (ع) $number في الملف عند السطر ${index + 1} • الورقة ${row.sheet}"
            }
            val name = EmployeeOrder.clean(row.name)
            val site = row.site.trim()
            require(name.isNotBlank() && site.isNotBlank() && name.length <= 120 && site.length <= 120) {
                "بيانات الاسم أو الموقع ناقصة أو طويلة للرقم (ع) $number في الورقة ${row.sheet}"
            }
            val old = existing[number]
            if (old == null) {
                added++
                if (warnings.size < 8 && (EmployeeOrder.key(name) to site) in legacy) {
                    warnings.add("$number: الاسم والموقع موجودان بسجل قديم بدون رقم (ع). لن يُدمجا آلياً، راجع السجل قبل الاعتماد.")
                }
            } else if (old.name != name || old.site != site) {
                updated++
                if (warnings.size < 8) warnings.add("$number: ${old.name} / ${old.site} ← $name / $site")
            } else unchanged++
        }
        return ImportCheck(added, updated, unchanged, warnings)
    }
}
