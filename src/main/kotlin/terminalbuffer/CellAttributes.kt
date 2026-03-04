package terminalbuffer

data class CellAttributes(
    val foreground: Color = Color.DEFAULT,
    val background: Color = Color.DEFAULT,
    val styles: Set<StyleFlag> = emptySet()
) {
    companion object {
        val DEFAULT = CellAttributes()
    }
}