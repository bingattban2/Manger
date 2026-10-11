package com.manger.app.domain

  
object RosterNumbers {
    fun searchKey(input: String): String = buildString {
        for (ch in input.trim()) append(when(ch) {
            in '٠'..'٩' -> '0' + (ch - '٠')
            in '۰'..'۹' -> '0' + (ch - '۰')
            else -> ch.lowercaseChar()
        })
    }
    fun clean(input: String): String {
        val text = searchKey(input)
        require(text.length in 1..20 && text.all { it in '0'..'9' }) {
            "الرقم (ع) مطلوب ويجب أن يتكون من أرقام فقط (1 إلى 20 رقماً)"
        }
        return text
    }
}
