package terminalbuffer

data class Cell(
    val character: Char = ' ',
    val attributes: CellAttributes = CellAttributes.DEFAULT,
    val isWideChar: Boolean = false,
    val isWideContinuation: Boolean = false
) {
    companion object {
        val EMPTY = Cell()
        fun wideContinuation(attributes: CellAttributes) =
            Cell(character = '\u0000', attributes = attributes, isWideContinuation = true)
    }
}