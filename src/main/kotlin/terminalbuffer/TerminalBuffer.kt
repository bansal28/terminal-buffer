package terminalbuffer

class TerminalBuffer(
    width: Int,
    height: Int,
    private val maxScrollback: Int = 1000
) {
    var width: Int = width
        private set
    var height: Int = height
        private set

    private var screen: Array<Array<Cell>> = makeEmptyGrid(width, height)
    private val scrollback: ArrayDeque<Array<Cell>> = ArrayDeque()

    var currentAttributes: CellAttributes = CellAttributes.DEFAULT

    var cursorCol: Int = 0
        private set
    var cursorRow: Int = 0
        private set

    val scrollbackSize: Int get() = scrollback.size



    fun setForeground(color: Color) {
        currentAttributes = currentAttributes.copy(foreground = color)
    }

    fun setBackground(color: Color) {
        currentAttributes = currentAttributes.copy(background = color)
    }

    fun setStyles(vararg flags: StyleFlag) {
        currentAttributes = currentAttributes.copy(styles = flags.toSet())
    }

    fun addStyle(flag: StyleFlag) {
        currentAttributes = currentAttributes.copy(styles = currentAttributes.styles + flag)
    }

    fun removeStyle(flag: StyleFlag) {
        currentAttributes = currentAttributes.copy(styles = currentAttributes.styles - flag)
    }

    fun resetAttributes() {
        currentAttributes = CellAttributes.DEFAULT
    }



    fun setCursor(col: Int, row: Int) {
        cursorCol = col.coerceIn(0, width - 1)
        cursorRow = row.coerceIn(0, height - 1)
    }

    fun moveCursorUp(n: Int = 1) = setCursor(cursorCol, cursorRow - n)
    fun moveCursorDown(n: Int = 1) = setCursor(cursorCol, cursorRow + n)
    fun moveCursorLeft(n: Int = 1) = setCursor(cursorCol - n, cursorRow)
    fun moveCursorRight(n: Int = 1) = setCursor(cursorCol + n, cursorRow)



    fun write(text: String) {
        val row = cursorRow
        var col = cursorCol
        for (ch in text) {
            val w = CharWidth.displayWidth(ch)
            if (w == 0) continue
            if (col + w > width) break

            clearWideCharGhost(col, row)

            if (w == 2) {
                screen[row][col] = Cell(ch, currentAttributes, isWideChar = true)
                clearWideCharGhost(col + 1, row)
                screen[row][col + 1] = Cell.wideContinuation(currentAttributes)
            } else {
                screen[row][col] = Cell(ch, currentAttributes)
            }
            col += w
        }
        cursorCol = col.coerceAtMost(width - 1)
    }


    fun insert(text: String) {
        if (text.isEmpty()) return

        val newCells = mutableListOf<Cell>()
        for (ch in text) {
            val w = CharWidth.displayWidth(ch)
            if (w == 0) continue
            if (w == 2) {
                newCells.add(Cell(ch, currentAttributes, isWideChar = true))
                newCells.add(Cell.wideContinuation(currentAttributes))
            } else {
                newCells.add(Cell(ch, currentAttributes))
            }
        }
        if (newCells.isEmpty()) return

        val col = cursorCol
        val row = cursorRow

        val tail = screen[row].drop(col).toMutableList()
        while (tail.isNotEmpty() && tail.last() == Cell.EMPTY) tail.removeAt(tail.lastIndex)

        val combined = newCells + tail

        var r = row
        var c = col
        for (cell in combined) {
            if (c >= width) {
                r++
                c = 0
                if (r >= height) break
            }
            screen[r][c] = cell
            c++
        }
        while (c < width && r < height) {
            screen[r][c] = Cell.EMPTY
            c++
        }

        var newCol = col + newCells.size
        var newRow = row
        while (newCol >= width) {
            newCol -= width
            newRow++
        }
        setCursor(newCol.coerceAtMost(width - 1), newRow.coerceAtMost(height - 1))
    }



    fun fillLine(ch: Char? = null) {
        val row = cursorRow
        val fillChar = ch ?: ' '
        val w = CharWidth.displayWidth(fillChar)
        var col = 0
        while (col < width) {
            if (w == 2 && col + 1 < width) {
                screen[row][col] = Cell(fillChar, currentAttributes, isWideChar = true)
                screen[row][col + 1] = Cell.wideContinuation(currentAttributes)
                col += 2
            } else if (w == 2) {
                screen[row][col] = Cell(' ', currentAttributes)
                col++
            } else {
                screen[row][col] = Cell(fillChar, currentAttributes)
                col++
            }
        }
    }



    fun insertLineAtBottom() {
        addToScrollback(screen[0].copyOf())
        for (r in 0 until height - 1) {
            screen[r] = screen[r + 1]
        }
        screen[height - 1] = emptyLine(width)
    }

    fun clearScreen() {
        screen = makeEmptyGrid(width, height)
        setCursor(0, 0)
    }

    fun clearAll() {
        scrollback.clear()
        clearScreen()
    }


    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0 && newHeight > 0) { "Dimensions must be positive" }

        val resizedLines = screen.map { line -> resizeLine(line, newWidth) }

        val linesToMove = resizedLines.size - newHeight
        if (linesToMove > 0) {
            for (i in 0 until linesToMove) {
                addToScrollback(resizedLines[i])
            }
            screen = resizedLines.drop(linesToMove).toTypedArray()
        } else {
            val extra = Array(-linesToMove) { emptyLine(newWidth) }
            screen = (resizedLines + extra).toTypedArray()
        }

        for (i in scrollback.indices) {
            scrollback[i] = resizeLine(scrollback[i], newWidth)
        }

        width = newWidth
        height = newHeight
        setCursor(cursorCol, cursorRow)
    }

    fun getCell(col: Int, row: Int): Cell {
        requireScreenBounds(col, row)
        return screen[row][col]
    }

    fun getChar(col: Int, row: Int): Char = getCell(col, row).character

    fun getAttributes(col: Int, row: Int): CellAttributes = getCell(col, row).attributes

    fun getCellAbsolute(col: Int, absoluteRow: Int): Cell {
        require(col in 0 until width) { "Column $col out of bounds [0, $width)" }
        require(absoluteRow in 0 until scrollbackSize + height) {
            "Absolute row $absoluteRow out of bounds [0, ${scrollbackSize + height})"
        }
        return if (absoluteRow < scrollbackSize) {
            scrollback[absoluteRow].getOrElse(col) { Cell.EMPTY }
        } else {
            screen[absoluteRow - scrollbackSize][col]
        }
    }

    fun getCharAbsolute(col: Int, absoluteRow: Int): Char =
        getCellAbsolute(col, absoluteRow).character

    fun getAttributesAbsolute(col: Int, absoluteRow: Int): CellAttributes =
        getCellAbsolute(col, absoluteRow).attributes

    fun getLine(row: Int): String {
        require(row in 0 until height) { "Row $row out of screen bounds [0, $height)" }
        return lineToString(screen[row])
    }

    fun getScrollbackLine(row: Int): String {
        require(row in 0 until scrollbackSize) {
            "Scrollback row $row out of bounds [0, $scrollbackSize)"
        }
        return lineToString(scrollback[row])
    }

    fun getLineAbsolute(absoluteRow: Int): String {
        require(absoluteRow in 0 until scrollbackSize + height) {
            "Absolute row $absoluteRow out of bounds [0, ${scrollbackSize + height})"
        }
        return if (absoluteRow < scrollbackSize) {
            getScrollbackLine(absoluteRow)
        } else {
            getLine(absoluteRow - scrollbackSize)
        }
    }

    fun getScreenContent(): String =
        (0 until height).joinToString("\n") { getLine(it) }

    fun getFullContent(): String =
        (0 until scrollbackSize + height).joinToString("\n") { getLineAbsolute(it) }

    private fun resizeLine(line: Array<Cell>, newWidth: Int): Array<Cell> {
        return Array(newWidth) { col ->
            if (col < line.size) line[col] else Cell.EMPTY
        }
    }

    private fun clearWideCharGhost(col: Int, row: Int) {
        if (col < 0 || col >= width || row < 0 || row >= height) return
        val cell = screen[row][col]
        if (cell.isWideContinuation && col > 0) {
            screen[row][col - 1] = Cell.EMPTY
        }
        if (cell.isWideChar && col + 1 < width) {
            screen[row][col + 1] = Cell.EMPTY
        }
    }
    private fun addToScrollback(line: Array<Cell>) {
        scrollback.addLast(line)
        while (scrollback.size > maxScrollback) {
            scrollback.removeFirst()
        }
    }
    private fun requireScreenBounds(col: Int, row: Int) {
        require(col in 0 until width) { "Column $col out of bounds [0, $width)" }
        require(row in 0 until height) { "Row $row out of bounds [0, $height)" }
    }

    private fun lineToString(line: Array<Cell>): String {
        val sb = StringBuilder()
        for (cell in line) {
            if (cell.isWideContinuation) continue
            sb.append(cell.character)
        }
        return sb.toString().trimEnd()
    }

    companion object {
        fun emptyLine(width: Int) = Array(width) { Cell.EMPTY }
        fun makeEmptyGrid(width: Int, height: Int) =
            Array(height) { emptyLine(width) }
    }
}