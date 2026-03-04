package terminalbuffer

object CharWidth {

    fun displayWidth(ch: Char): Int {
        val code = ch.code
        return when {
            code < 0x20 -> 0
            isWide(code) -> 2
            else -> 1
        }
    }

    fun displayWidth(text: String): Int = text.sumOf { displayWidth(it) }

    private fun isWide(code: Int): Boolean =
        code in 0x1100..0x115F ||
                code in 0x2E80..0x303E ||
                code in 0x3041..0x33BF ||
                code in 0x3400..0x4DBF ||
                code in 0x4E00..0x9FFF ||
                code in 0xA000..0xA4CF ||
                code in 0xAC00..0xD7AF ||
                code in 0xF900..0xFAFF ||
                code in 0xFE30..0xFE6F ||
                code in 0xFF01..0xFF60 ||
                code in 0xFFE0..0xFFE6
}