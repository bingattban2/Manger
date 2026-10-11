package com.manger.app.domain

object SupervisorPasswordPolicy {
    fun isValid(value: String): Boolean =
        value.length in 6..72 &&
        value.count { it in '0'..'9' } >= 3 &&
        value.any { !it.isLetterOrDigit() && !it.isWhitespace() } &&
        value.none { it.isWhitespace() }

    const val guidance = "كلمة المرور: 6 أحرف على الأقل، تتضمن 3 أرقام ورمزاً خاصاً، دون مسافات"
}
