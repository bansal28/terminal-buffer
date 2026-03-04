# Terminal Text Buffer

A Kotlin implementation of the core data structure that terminal emulators use to store and manipulate displayed text.

## Building & Running Tests

**Prerequisites**: JDK 17+
```bash
./gradlew build
./gradlew test
```

## Project Structure
```
src/main/kotlin/terminalbuffer/
├── Cell.kt              # Single character cell in the grid
├── CellAttributes.kt    # Foreground, background, style flags bundle
├── CharWidth.kt         # Unicode display width detection (wide chars)
├── Color.kt             # 16 standard terminal colors + default
├── StyleFlag.kt         # Bold, italic, underline flags
└── TerminalBuffer.kt    # Core buffer: screen + scrollback + cursor + editing

src/test/kotlin/terminalbuffer/
└── TerminalBufferTest.kt  # Comprehensive JUnit 5 test suite (~80 tests)
```

## Solution Explanation

### Architecture

The buffer is centred on a grid of `Cell` objects, where each cell stores a character, its visual attributes (foreground/background colour, style flags), and optional wide-character metadata. The design uses Kotlin data classes for `Cell`, `CellAttributes`, and enums for `Color`/`StyleFlag`, giving us free `equals`/`hashCode`/`copy` which simplifies both attribute propagation and test assertions.

### Internal Storage

- **Screen**: `Array<Array<Cell>>` (height × width) — fixed-size grid with O(1) random access by (col, row). Resized only on explicit `resize()` calls.
- **Scrollback**: `ArrayDeque<Array<Cell>>` — O(1) append when lines scroll off-screen, O(1) eviction from the front when maximum size is exceeded.

### Coordinate System

The buffer exposes two coordinate spaces:

- **Screen coordinates**: `(col, row)` where row is in `[0, height)` — used by cursor and editing operations.
- **Absolute coordinates**: row 0 is the oldest scrollback line, screen starts at row `scrollbackSize` — used by content-access methods that span both regions.

### Key Design Decisions

**Immutable cells**: `Cell` and `CellAttributes` are data classes. Mutations always go through the buffer API, never through aliased cell references. This avoids subtle bugs where callers could silently mutate grid content.

**Write vs Insert semantics**: Write (overwrite mode) replaces cells in-place on the current line; characters that exceed the right margin are discarded. Insert shifts existing content right and wraps overflow to subsequent lines. Lines that cascade off the bottom of the screen are discarded — they do not enter scrollback, which only happens via `insertLineAtBottom()`.

**Wide-character handling** (bonus): A double-width character writes two cells — a leading cell (`isWideChar = true`) and a continuation cell. When either half is overwritten, the orphaned partner is cleared to a space, preventing visual corruption from half-cells.

**Resize strategy** (bonus): Width changes truncate or pad each line. Height decreases push excess top lines into scrollback; increases add empty lines at the bottom. Line rewrapping on width changes was intentionally omitted — real terminal emulators have complex rewrap logic involving soft line break tracking, and that complexity was not justified here.

**Scrollback eviction**: Uses a simple FIFO policy — oldest lines are dropped when `maxScrollback` is exceeded. `ArrayDeque` makes this O(1).

**Cursor clamping**: Every cursor mutation clamps to `[0, width-1] × [0, height-1]`. There is no "pending wrap" state (which real terminals use when the cursor reaches the right margin). This is a deliberate simplification.

### Trade-offs

- **Data-class cells**: Simple, immutable, and testable — but allocates a new object per cell mutation.
- **No line rewrap on resize**: Straightforward and predictable — but content may be truncated on width shrink.
- **No wrap-pending state**: Simpler cursor logic — but `write` at the last column doesn't auto-advance to the next line.
- **ArrayDeque for scrollback**: O(1) add/evict — but scrollback lines may have heterogeneous widths after resize.

### Possible Improvements

- **Pending-wrap state**: Real terminals set a flag when the cursor reaches the right margin. The next printable character then wraps to column 0 of the next line, needed for correct VT100 emulation.
- **Line rewrapping on resize**: Tracking soft vs hard line breaks would allow content-preserving width changes.
- **Tab-stop support**: A `\t` character should advance the cursor to the next tab stop (typically every 8 columns).
- **ANSI escape sequence parser**: Layering an escape-sequence state machine on top of this buffer would make it a usable terminal emulator backend.
- **True-colour support**: Extending `Color` from 16 values to 24-bit RGB.
- **Object pooling for cells**: For high-throughput scenarios, reusing `Cell` instances with common attributes would reduce GC pressure.
- **Surrogate pair handling**: The current implementation works at the `Char` level. Full Unicode requires handling code points beyond U+FFFF via surrogate pairs.
