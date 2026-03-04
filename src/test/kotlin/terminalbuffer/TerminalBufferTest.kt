package terminalbuffer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TerminalBufferTest {

    private lateinit var buf: TerminalBuffer

    @BeforeEach
    fun setup() {
        buf = TerminalBuffer(width = 10, height = 5, maxScrollback = 20)
    }

    @Nested
    inner class Initialization {

        @Test
        fun `new buffer has correct dimensions`() {
            assertEquals(10, buf.width)
            assertEquals(5, buf.height)
        }

        @Test
        fun `cursor starts at origin`() {
            assertEquals(0, buf.cursorCol)
            assertEquals(0, buf.cursorRow)
        }

        @Test
        fun `screen is initially empty`() {
            for (r in 0 until buf.height) {
                for (c in 0 until buf.width) {
                    assertEquals(' ', buf.getChar(c, r))
                }
            }
        }

        @Test
        fun `scrollback is initially empty`() {
            assertEquals(0, buf.scrollbackSize)
        }

        @Test
        fun `default attributes are applied`() {
            assertEquals(CellAttributes.DEFAULT, buf.currentAttributes)
        }

        @Test
        fun `screen content string is empty lines`() {
            val lines = buf.getScreenContent().split("\n")
            assertEquals(5, lines.size)
            lines.forEach { assertEquals("", it) }
        }
    }

    @Nested
    inner class Attributes {

        @Test
        fun `set foreground color`() {
            buf.setForeground(Color.RED)
            assertEquals(Color.RED, buf.currentAttributes.foreground)
            assertEquals(Color.DEFAULT, buf.currentAttributes.background)
        }

        @Test
        fun `set background color`() {
            buf.setBackground(Color.BLUE)
            assertEquals(Color.BLUE, buf.currentAttributes.background)
        }

        @Test
        fun `set multiple styles`() {
            buf.setStyles(StyleFlag.BOLD, StyleFlag.UNDERLINE)
            assertTrue(buf.currentAttributes.styles.contains(StyleFlag.BOLD))
            assertTrue(buf.currentAttributes.styles.contains(StyleFlag.UNDERLINE))
            assertFalse(buf.currentAttributes.styles.contains(StyleFlag.ITALIC))
        }

        @Test
        fun `add and remove individual styles`() {
            buf.addStyle(StyleFlag.BOLD)
            buf.addStyle(StyleFlag.ITALIC)
            assertTrue(buf.currentAttributes.styles.containsAll(listOf(StyleFlag.BOLD, StyleFlag.ITALIC)))

            buf.removeStyle(StyleFlag.BOLD)
            assertFalse(buf.currentAttributes.styles.contains(StyleFlag.BOLD))
            assertTrue(buf.currentAttributes.styles.contains(StyleFlag.ITALIC))
        }

        @Test
        fun `reset attributes returns to default`() {
            buf.setForeground(Color.CYAN)
            buf.setBackground(Color.MAGENTA)
            buf.setStyles(StyleFlag.BOLD)
            buf.resetAttributes()
            assertEquals(CellAttributes.DEFAULT, buf.currentAttributes)
        }

        @Test
        fun `written cells inherit current attributes`() {
            buf.setForeground(Color.GREEN)
            buf.addStyle(StyleFlag.BOLD)
            buf.write("Hi")
            val attrs = buf.getAttributes(0, 0)
            assertEquals(Color.GREEN, attrs.foreground)
            assertTrue(attrs.styles.contains(StyleFlag.BOLD))
        }
    }

    @Nested
    inner class CursorMovement {

        @Test
        fun `set cursor within bounds`() {
            buf.setCursor(5, 3)
            assertEquals(5, buf.cursorCol)
            assertEquals(3, buf.cursorRow)
        }

        @Test
        fun `cursor clamped to right edge`() {
            buf.setCursor(100, 0)
            assertEquals(9, buf.cursorCol)
        }

        @Test
        fun `cursor clamped to bottom edge`() {
            buf.setCursor(0, 100)
            assertEquals(4, buf.cursorRow)
        }

        @Test
        fun `cursor clamped to left edge`() {
            buf.setCursor(-5, 0)
            assertEquals(0, buf.cursorCol)
        }

        @Test
        fun `cursor clamped to top edge`() {
            buf.setCursor(0, -5)
            assertEquals(0, buf.cursorRow)
        }

        @Test
        fun `move cursor up`() {
            buf.setCursor(5, 3)
            buf.moveCursorUp(2)
            assertEquals(5, buf.cursorCol)
            assertEquals(1, buf.cursorRow)
        }

        @Test
        fun `move cursor down`() {
            buf.setCursor(0, 1)
            buf.moveCursorDown(2)
            assertEquals(3, buf.cursorRow)
        }

        @Test
        fun `move cursor left`() {
            buf.setCursor(5, 0)
            buf.moveCursorLeft(3)
            assertEquals(2, buf.cursorCol)
        }

        @Test
        fun `move cursor right`() {
            buf.setCursor(3, 0)
            buf.moveCursorRight(4)
            assertEquals(7, buf.cursorCol)
        }

        @Test
        fun `move up beyond top clamps to row 0`() {
            buf.setCursor(0, 1)
            buf.moveCursorUp(10)
            assertEquals(0, buf.cursorRow)
        }

        @Test
        fun `move down beyond bottom clamps to last row`() {
            buf.setCursor(0, 3)
            buf.moveCursorDown(50)
            assertEquals(4, buf.cursorRow)
        }

        @Test
        fun `move left beyond start clamps to column 0`() {
            buf.setCursor(2, 0)
            buf.moveCursorLeft(10)
            assertEquals(0, buf.cursorCol)
        }

        @Test
        fun `move right beyond end clamps to last column`() {
            buf.setCursor(7, 0)
            buf.moveCursorRight(20)
            assertEquals(9, buf.cursorCol)
        }

        @Test
        fun `move by default 1 step`() {
            buf.setCursor(5, 3)
            buf.moveCursorUp()
            assertEquals(2, buf.cursorRow)
            buf.moveCursorDown()
            assertEquals(3, buf.cursorRow)
            buf.moveCursorLeft()
            assertEquals(4, buf.cursorCol)
            buf.moveCursorRight()
            assertEquals(5, buf.cursorCol)
        }
    }

    @Nested
    inner class Write {

        @Test
        fun `write short text at origin`() {
            buf.write("Hello")
            assertEquals("Hello", buf.getLine(0))
            assertEquals(5, buf.cursorCol)
            assertEquals(0, buf.cursorRow)
        }

        @Test
        fun `write overwrites existing content`() {
            buf.write("ABCDE")
            buf.setCursor(1, 0)
            buf.write("xx")
            assertEquals("AxxDE", buf.getLine(0))
        }

        @Test
        fun `write truncates at right margin`() {
            buf.setCursor(7, 0)
            buf.write("ABCDEF")
            assertEquals("ABC", buf.getLine(0).trim())
            assertEquals(9, buf.cursorCol)
        }

        @Test
        fun `write full width of buffer`() {
            buf.write("0123456789")
            assertEquals("0123456789", buf.getLine(0))
            assertEquals(9, buf.cursorCol)
        }

        @Test
        fun `write empty string does nothing`() {
            buf.setCursor(3, 2)
            buf.write("")
            assertEquals(3, buf.cursorCol)
            assertEquals(2, buf.cursorRow)
            assertEquals("", buf.getLine(2))
        }

        @Test
        fun `write at different rows`() {
            buf.setCursor(0, 0)
            buf.write("Row0")
            buf.setCursor(0, 2)
            buf.write("Row2")
            assertEquals("Row0", buf.getLine(0))
            assertEquals("", buf.getLine(1))
            assertEquals("Row2", buf.getLine(2))
        }

        @Test
        fun `write preserves attributes per cell`() {
            buf.setForeground(Color.RED)
            buf.write("AB")
            buf.setForeground(Color.BLUE)
            buf.write("CD")

            assertEquals(Color.RED, buf.getAttributes(0, 0).foreground)
            assertEquals(Color.RED, buf.getAttributes(1, 0).foreground)
            assertEquals(Color.BLUE, buf.getAttributes(2, 0).foreground)
            assertEquals(Color.BLUE, buf.getAttributes(3, 0).foreground)
        }
    }

    @Nested
    inner class Insert {

        @Test
        fun `insert on empty line`() {
            buf.insert("Hi")
            assertEquals("Hi", buf.getLine(0))
            assertEquals(2, buf.cursorCol)
        }

        @Test
        fun `insert shifts existing content right`() {
            buf.write("World")
            buf.setCursor(0, 0)
            buf.insert("Hello ")
            assertEquals("Hello Worl", buf.getLine(0))
            assertEquals("d", buf.getLine(1))
        }

        @Test
        fun `insert wraps content to next line`() {
            buf.write("ABCDEFGHIJ")
            buf.setCursor(5, 0)
            buf.insert("xx")
            assertEquals("ABCDExxFGH", buf.getLine(0))
            assertEquals("IJ", buf.getLine(1))
        }

        @Test
        fun `insert empty string does nothing`() {
            buf.write("Test")
            buf.setCursor(2, 0)
            buf.insert("")
            assertEquals("Test", buf.getLine(0))
            assertEquals(2, buf.cursorCol)
        }

        @Test
        fun `cursor advances past inserted text`() {
            buf.insert("ABC")
            assertEquals(3, buf.cursorCol)
            assertEquals(0, buf.cursorRow)
        }

        @Test
        fun `insert wrapping advances cursor to next row`() {
            buf.setCursor(8, 0)
            buf.insert("ABCD")
            assertEquals(2, buf.cursorCol)
            assertEquals(1, buf.cursorRow)
        }
    }

    @Nested
    inner class FillLine {

        @Test
        fun `fill line with character`() {
            buf.setCursor(0, 2)
            buf.fillLine('=')
            assertEquals("==========", buf.getLine(2))
        }

        @Test
        fun `fill line with null clears line`() {
            buf.write("Something")
            buf.setCursor(0, 0)
            buf.fillLine(null)
            assertEquals("", buf.getLine(0))
        }

        @Test
        fun `fill line uses current attributes`() {
            buf.setForeground(Color.YELLOW)
            buf.setCursor(0, 1)
            buf.fillLine('*')
            for (c in 0 until buf.width) {
                assertEquals(Color.YELLOW, buf.getAttributes(c, 1).foreground)
            }
        }

        @Test
        fun `fill line does not move cursor`() {
            buf.setCursor(3, 2)
            buf.fillLine('#')
            assertEquals(3, buf.cursorCol)
            assertEquals(2, buf.cursorRow)
        }

        @Test
        fun `fill only affects current row`() {
            buf.setCursor(0, 0)
            buf.write("Keep")
            buf.setCursor(0, 1)
            buf.fillLine('X')
            assertEquals("Keep", buf.getLine(0))
            assertEquals("XXXXXXXXXX", buf.getLine(1))
        }
    }

    @Nested
    inner class InsertLineAtBottom {

        @Test
        fun `insert line pushes top to scrollback`() {
            buf.write("Line0")
            buf.insertLineAtBottom()
            assertEquals(1, buf.scrollbackSize)
            assertEquals("Line0", buf.getScrollbackLine(0))
        }

        @Test
        fun `new bottom line is empty`() {
            buf.insertLineAtBottom()
            assertEquals("", buf.getLine(buf.height - 1))
        }

        @Test
        fun `screen content shifts up`() {
            buf.setCursor(0, 0); buf.write("A")
            buf.setCursor(0, 1); buf.write("B")
            buf.setCursor(0, 2); buf.write("C")
            buf.insertLineAtBottom()
            assertEquals("B", buf.getLine(0))
            assertEquals("C", buf.getLine(1))
        }

        @Test
        fun `multiple inserts build scrollback`() {
            for (i in 0 until 8) {
                buf.setCursor(0, 0)
                buf.write("Line$i")
                buf.insertLineAtBottom()
            }
            assertTrue(buf.scrollbackSize > 0)
            assertEquals("Line0", buf.getScrollbackLine(0))
        }

        @Test
        fun `scrollback respects maximum size`() {
            val small = TerminalBuffer(10, 3, maxScrollback = 5)
            for (i in 0 until 20) {
                small.setCursor(0, 0)
                small.write("L$i")
                small.insertLineAtBottom()
            }
            assertEquals(5, small.scrollbackSize)
            assertEquals("L15", small.getScrollbackLine(0))
        }
    }

    @Nested
    inner class ClearOperations {

        @Test
        fun `clear screen empties all cells`() {
            buf.write("Hello")
            buf.setCursor(0, 2)
            buf.write("World")
            buf.clearScreen()
            for (r in 0 until buf.height) {
                assertEquals("", buf.getLine(r))
            }
        }

        @Test
        fun `clear screen resets cursor to origin`() {
            buf.setCursor(5, 3)
            buf.clearScreen()
            assertEquals(0, buf.cursorCol)
            assertEquals(0, buf.cursorRow)
        }

        @Test
        fun `clear screen preserves scrollback`() {
            buf.write("Top")
            buf.insertLineAtBottom()
            assertEquals(1, buf.scrollbackSize)
            buf.clearScreen()
            assertEquals(1, buf.scrollbackSize)
            assertEquals("Top", buf.getScrollbackLine(0))
        }

        @Test
        fun `clear all empties screen and scrollback`() {
            buf.write("Something")
            buf.insertLineAtBottom()
            buf.clearAll()
            assertEquals(0, buf.scrollbackSize)
            assertEquals("", buf.getLine(0))
            assertEquals(0, buf.cursorCol)
            assertEquals(0, buf.cursorRow)
        }
    }

    @Nested
    inner class ContentAccess {

        @Test
        fun `getAttributesAbsolute spans scrollback and screen`() {
            buf.setForeground(Color.RED)
            buf.write("Old")
            buf.insertLineAtBottom()
            buf.setCursor(0, 0)
            buf.setForeground(Color.BLUE)
            buf.write("New")

            assertEquals(Color.RED, buf.getAttributesAbsolute(0, 0).foreground)
            assertEquals(Color.BLUE, buf.getAttributesAbsolute(0, 1).foreground)
        }
        @Test
        fun `getChar returns written character`() {
            buf.write("Test")
            assertEquals('T', buf.getChar(0, 0))
            assertEquals('e', buf.getChar(1, 0))
            assertEquals('s', buf.getChar(2, 0))
            assertEquals('t', buf.getChar(3, 0))
        }

        @Test
        fun `getChar returns space for empty cell`() {
            assertEquals(' ', buf.getChar(5, 3))
        }

        @Test
        fun `out of bounds throws exception`() {
            assertThrows<IllegalArgumentException> { buf.getChar(-1, 0) }
            assertThrows<IllegalArgumentException> { buf.getChar(0, -1) }
            assertThrows<IllegalArgumentException> { buf.getChar(10, 0) }
            assertThrows<IllegalArgumentException> { buf.getChar(0, 5) }
        }

        @Test
        fun `absolute access spans scrollback and screen`() {
            buf.write("Scrolled")
            buf.insertLineAtBottom()
            buf.setCursor(0, 0)
            buf.write("Visible")

            assertEquals('S', buf.getCharAbsolute(0, 0))
            assertEquals('V', buf.getCharAbsolute(0, 1))
        }

        @Test
        fun `getLine returns trimmed string`() {
            buf.write("Hi")
            assertEquals("Hi", buf.getLine(0))
        }

        @Test
        fun `getScreenContent returns all lines`() {
            buf.setCursor(0, 0); buf.write("AAA")
            buf.setCursor(0, 2); buf.write("CCC")
            buf.setCursor(0, 4); buf.write("EEE")
            val content = buf.getScreenContent()
            val lines = content.split("\n")
            assertEquals("AAA", lines[0])
            assertEquals("", lines[1])
            assertEquals("CCC", lines[2])
            assertEquals("", lines[3])
            assertEquals("EEE", lines[4])
        }

        @Test
        fun `getFullContent includes scrollback`() {
            buf.write("OldLine")
            buf.insertLineAtBottom()
            buf.setCursor(0, 0)
            buf.write("NewLine")

            val content = buf.getFullContent()
            val lines = content.split("\n")
            assertEquals("OldLine", lines[0])
            assertEquals("NewLine", lines[1])
        }

        @Test
        fun `absolute access out of bounds throws`() {
            assertThrows<IllegalArgumentException> {
                buf.getCharAbsolute(0, -1)
            }
            assertThrows<IllegalArgumentException> {
                buf.getCharAbsolute(0, buf.scrollbackSize + buf.height)
            }
        }

        @Test
        fun `getLineAbsolute works for scrollback and screen`() {
            buf.write("Scrolled")
            buf.insertLineAtBottom()
            buf.setCursor(0, 0)
            buf.write("OnScreen")

            assertEquals("Scrolled", buf.getLineAbsolute(0))
            assertEquals("OnScreen", buf.getLineAbsolute(1))
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `1x1 buffer works`() {
            val tiny = TerminalBuffer(1, 1)
            tiny.write("X")
            assertEquals("X", tiny.getLine(0))
            assertEquals(0, tiny.cursorCol)
        }

        @Test
        fun `write after cursor move`() {
            buf.setCursor(3, 2)
            buf.write("OK")
            assertEquals('O', buf.getChar(3, 2))
            assertEquals('K', buf.getChar(4, 2))
        }

        @Test
        fun `attributes change mid-write`() {
            buf.setForeground(Color.RED)
            buf.write("AB")
            buf.setForeground(Color.GREEN)
            buf.write("CD")
            assertEquals(Color.RED, buf.getAttributes(1, 0).foreground)
            assertEquals(Color.GREEN, buf.getAttributes(2, 0).foreground)
        }

        @Test
        fun `scrollback eviction works at capacity`() {
            val small = TerminalBuffer(5, 2, maxScrollback = 3)
            for (i in 0 until 10) {
                small.setCursor(0, 0)
                small.write("$i")
                small.insertLineAtBottom()
            }
            assertEquals(3, small.scrollbackSize)
        }

        @Test
        fun `full content after many operations`() {
            buf.write("First")
            buf.insertLineAtBottom()
            buf.setCursor(0, 0)
            buf.write("Second")
            buf.insertLineAtBottom()
            buf.setCursor(0, 0)
            buf.write("Third")

            val full = buf.getFullContent()
            assertTrue(full.contains("First"))
            assertTrue(full.contains("Second"))
            assertTrue(full.contains("Third"))
        }

        @Test
        fun `clear then write works cleanly`() {
            buf.write("Old")
            buf.clearScreen()
            buf.write("New")
            assertEquals("New", buf.getLine(0))
        }

        @Test
        fun `zero scrollback configuration`() {
            val noScroll = TerminalBuffer(10, 3, maxScrollback = 0)
            noScroll.write("Top")
            noScroll.insertLineAtBottom()
            assertEquals(0, noScroll.scrollbackSize)
        }

        @Test
        fun `getScrollbackLine out of bounds throws`() {
            assertThrows<IllegalArgumentException> {
                buf.getScrollbackLine(0)
            }
        }
    }

    @Nested
    inner class Scenarios {

        @Test
        fun `simulate simple shell output`() {
            buf.setForeground(Color.GREEN)
            buf.write("$ ")
            buf.resetAttributes()
            buf.write("ls")
            buf.setCursor(0, 1)
            buf.write("file1.txt")
            buf.setCursor(0, 2)
            buf.write("file2.txt")
            buf.setCursor(0, 3)
            buf.setForeground(Color.GREEN)
            buf.write("$ ")

            assertEquals("$ ls", buf.getLine(0))
            assertEquals("file1.txt", buf.getLine(1))
            assertEquals("file2.txt", buf.getLine(2))
            assertEquals("$", buf.getLine(3))
            assertEquals(Color.GREEN, buf.getAttributes(0, 0).foreground)
            assertEquals(Color.DEFAULT, buf.getAttributes(2, 0).foreground)
        }

        @Test
        fun `scrollback builds as screen fills`() {
            for (i in 0 until 10) {
                buf.setCursor(0, buf.height - 1)
                buf.write("Line$i")
                if (i < 9) buf.insertLineAtBottom()
            }
            assertTrue(buf.scrollbackSize > 0)
            val full = buf.getFullContent()
            for (i in 0 until 10) {
                assertTrue(full.contains("Line$i"), "Missing Line$i in full content")
            }
        }
    }

    @Nested
    inner class WideCharacters {

        @Test
        fun `wide char occupies two cells`() {
            buf.write("\u4E16") // 世 — CJK, width 2
            val cell0 = buf.getCell(0, 0)
            val cell1 = buf.getCell(1, 0)
            assertEquals('\u4E16', cell0.character)
            assertTrue(cell0.isWideChar)
            assertTrue(cell1.isWideContinuation)
        }

        @Test
        fun `cursor advances by 2 for wide char`() {
            buf.write("\u4E16")
            assertEquals(2, buf.cursorCol)
        }

        @Test
        fun `wide char that does not fit is not written`() {
            buf.setCursor(9, 0)
            buf.write("\u4E16")
            assertEquals(' ', buf.getChar(9, 0))
        }

        @Test
        fun `overwriting leading cell of wide char clears continuation`() {
            buf.write("\u4E16")
            buf.setCursor(0, 0)
            buf.write("A")
            assertEquals('A', buf.getChar(0, 0))
            assertEquals(' ', buf.getChar(1, 0))
        }

        @Test
        fun `overwriting continuation cell of wide char clears leading`() {
            buf.write("\u4E16")
            buf.setCursor(1, 0)
            buf.write("B")
            assertEquals(' ', buf.getChar(0, 0))
            assertEquals('B', buf.getChar(1, 0))
        }

        @Test
        fun `mixed narrow and wide chars`() {
            buf.write("A\u4E16B")
            assertEquals('A', buf.getChar(0, 0))
            assertEquals('\u4E16', buf.getChar(1, 0))
            assertTrue(buf.getCell(2, 0).isWideContinuation)
            assertEquals('B', buf.getChar(3, 0))
            assertEquals(4, buf.cursorCol)
        }

        @Test
        fun `getLine skips continuation cells in output`() {
            buf.write("A\u4E16B")
            assertEquals("A\u4E16B", buf.getLine(0))
        }

        @Test
        fun `fill line with wide char`() {
            buf.fillLine('\u4E16')
            for (col in 0 until 10 step 2) {
                assertEquals('\u4E16', buf.getChar(col, 0))
                assertTrue(buf.getCell(col, 0).isWideChar)
                assertTrue(buf.getCell(col + 1, 0).isWideContinuation)
            }
        }

        @Test
        fun `fill line with wide char on odd width pads last cell`() {
            val oddBuf = TerminalBuffer(width = 11, height = 3)
            oddBuf.fillLine('\u4E16')
            assertEquals(' ', oddBuf.getChar(10, 0))
        }
    }

    @Nested
    inner class Resize {

        @Test
        fun `shrink height pushes lines to scrollback`() {
            buf.setCursor(0, 0); buf.write("L0")
            buf.setCursor(0, 1); buf.write("L1")
            buf.setCursor(0, 2); buf.write("L2")
            buf.setCursor(0, 3); buf.write("L3")
            buf.setCursor(0, 4); buf.write("L4")

            buf.resize(10, 3)
            assertEquals(3, buf.height)
            assertEquals(2, buf.scrollbackSize)
            assertEquals("L0", buf.getScrollbackLine(0))
            assertEquals("L1", buf.getScrollbackLine(1))
            assertEquals("L2", buf.getLine(0))
            assertEquals("L3", buf.getLine(1))
            assertEquals("L4", buf.getLine(2))
        }

        @Test
        fun `grow height adds empty lines at bottom`() {
            buf.write("Data")
            buf.resize(10, 8)
            assertEquals(8, buf.height)
            assertEquals("Data", buf.getLine(0))
            assertEquals("", buf.getLine(7))
        }

        @Test
        fun `shrink width truncates lines`() {
            buf.write("0123456789")
            buf.resize(5, 5)
            assertEquals(5, buf.width)
            assertEquals("01234", buf.getLine(0))
        }

        @Test
        fun `grow width pads lines`() {
            buf.write("ABC")
            buf.resize(20, 5)
            assertEquals(20, buf.width)
            assertEquals("ABC", buf.getLine(0))
            assertEquals(' ', buf.getChar(10, 0))
        }

        @Test
        fun `resize clamps cursor`() {
            buf.setCursor(9, 4)
            buf.resize(5, 3)
            assertEquals(4, buf.cursorCol)
            assertEquals(2, buf.cursorRow)
        }

        @Test
        fun `resize with invalid dimensions throws`() {
            assertThrows<IllegalArgumentException> { buf.resize(0, 5) }
            assertThrows<IllegalArgumentException> { buf.resize(5, 0) }
            assertThrows<IllegalArgumentException> { buf.resize(-1, -1) }
        }

        @Test
        fun `resize also adjusts scrollback line widths`() {
            buf.write("OldLine123")
            buf.insertLineAtBottom()
            buf.resize(5, 5)
            assertEquals("OldLi", buf.getScrollbackLine(0))
        }
    }
}


