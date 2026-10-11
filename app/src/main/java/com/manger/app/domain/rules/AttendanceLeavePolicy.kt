package com.manger.app.domain

object AttendanceLeavePolicy {
    const val LEAVE_CODE = "LEAVE"

    fun maySelectStatus(isAdmin: Boolean, code: String): Boolean =
        isAdmin || code != LEAVE_CODE

    fun requireAllowed(isAdmin: Boolean, previousEffectiveStatus: String?, requestedStatus: String) {
        if (isAdmin) return
        require(requestedStatus != LEAVE_CODE) {
            "إضافة حالة إجازة متاحة للإدارة العامة فقط"
        }
        require(previousEffectiveStatus != LEAVE_CODE) {
            "الفرد في حالة إجازة؛ لا يمكن للمشرف إلغاء الإجازة أو تعديلها. يرجى مراجعة الإدارة العامة"
        }
    }
}
