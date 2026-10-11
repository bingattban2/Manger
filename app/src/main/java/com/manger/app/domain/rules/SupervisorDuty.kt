package com.manger.app.domain

  
enum class DutyReason { AUTHORIZED, NO_EMPLOYEE_LINK, EMPLOYEE_INACTIVE, NO_TODAY_MARK, NOT_PRESENT }

data class SupervisorDutyDecision(val reason: DutyReason, val recordedStatus: String? = null) {
    val allowed: Boolean get() = reason == DutyReason.AUTHORIZED
    val message: String get() = when (reason) {
        DutyReason.AUTHORIZED -> "تم إثبات حضورك اليوم، ويمكنك إدارة موقعك."
        DutyReason.NO_EMPLOYEE_LINK -> "لم يُربط حسابك بعد برقمك الوظيفي في الكشف العام. يرجى مراجعة الإدارة."
        DutyReason.EMPLOYEE_INACTIVE -> "بياناتك الوظيفية لا تسمح بإدارة الموقع. يرجى مراجعة الإدارة."
        DutyReason.NO_TODAY_MARK -> "لم يتم تحضيرك بعد. يرجى إثبات حضورك أولاً عن طريق الإدارة لتتمكن من إدارة الموقع."
        DutyReason.NOT_PRESENT -> "حسابك مقفل اليوم. حالتك الحالية: ${recordedStatus ?: "غير محددة"}، ولا تملك صلاحية إدارة الموقع."
    }
}

  
object SupervisorDutyPolicy {
    fun evaluate(linked: Boolean, activeEmployee: Boolean, todayStatus: String?, statusLabel: String? = null): SupervisorDutyDecision = when {
        !linked -> SupervisorDutyDecision(DutyReason.NO_EMPLOYEE_LINK)
        !activeEmployee -> SupervisorDutyDecision(DutyReason.EMPLOYEE_INACTIVE)
        todayStatus == null -> SupervisorDutyDecision(DutyReason.NO_TODAY_MARK)
        todayStatus == "PRESENT" -> SupervisorDutyDecision(DutyReason.AUTHORIZED)
        else -> SupervisorDutyDecision(DutyReason.NOT_PRESENT, statusLabel ?: todayStatus)
    }
}

class SupervisorDutyDeniedException(message: String) : IllegalStateException(message)
