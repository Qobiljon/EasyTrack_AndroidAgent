package kr.ac.inha.nsl

object Tools {
    fun isNumber(str: String?): Boolean {
        if (str == null) return false
        try {
            str.toLong()
        } catch (nfe: NumberFormatException) {
            return false
        }
        return true
    }
}