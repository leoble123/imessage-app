package com.leo.imessage.games

/**
 * Five in a row, on a board small enough to read on a phone.
 *
 * The classic rules: two players alternate, first to get five of their own in
 * a line - any direction - wins. Nine by nine rather than the traditional
 * fifteen, because the board has to be legible at the width of a message
 * bubble and a longer game is not a better one on a phone.
 */
object FiveInARow {

    const val SIZE = 9
    const val RUN = 5

    enum class Cell { EMPTY, YOU, BOT }

    data class Board(
        val cells: List<Cell> = List(SIZE * SIZE) { Cell.EMPTY },
        val turn: Cell = Cell.YOU,
        val winner: Cell? = null,
        /** Squares making up the winning line, for drawing it. */
        val winningLine: List<Int> = emptyList(),
    ) {
        val full: Boolean get() = cells.none { it == Cell.EMPTY }
        val over: Boolean get() = winner != null || full
        operator fun get(x: Int, y: Int): Cell = cells[y * SIZE + x]
    }

    /** The four directions a run can point. The other four are these reversed. */
    private val DIRECTIONS = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)

    private fun inBounds(x: Int, y: Int) = x in 0 until SIZE && y in 0 until SIZE

    /** The run through [index] in one direction, if it is long enough to win. */
    private fun winningRun(board: Board, index: Int, who: Cell): List<Int>? {
        val x0 = index % SIZE
        val y0 = index / SIZE
        for ((dx, dy) in DIRECTIONS) {
            val line = ArrayList<Int>()
            // Walk backwards to the start of the run, then forwards through it,
            // so a stone played into the middle of four is counted properly.
            var x = x0
            var y = y0
            while (inBounds(x - dx, y - dy) && board[x - dx, y - dy] == who) {
                x -= dx
                y -= dy
            }
            while (inBounds(x, y) && board[x, y] == who) {
                line.add(y * SIZE + x)
                x += dx
                y += dy
            }
            if (line.size >= RUN) return line
        }
        return null
    }

    /** Places a stone, if that square is free and the game is still going. */
    fun play(board: Board, index: Int, who: Cell): Board {
        if (board.over || board.cells[index] != Cell.EMPTY || board.turn != who) return board
        val cells = board.cells.toMutableList()
        cells[index] = who
        val next = board.copy(
            cells = cells,
            turn = if (who == Cell.YOU) Cell.BOT else Cell.YOU,
        )
        val line = winningRun(next, index, who)
        return if (line != null) next.copy(winner = who, winningLine = line) else next
    }

    /**
     * How good a square is for [who], counting the runs it would extend.
     *
     * A run's worth grows sharply with its length and is halved when both ends
     * are blocked, because a four that cannot be extended is worth less than a
     * three that can. Crude next to a real engine, and enough to punish you for
     * not paying attention - which is the whole job here.
     */
    internal fun score(board: Board, index: Int, who: Cell): Long {
        val x0 = index % SIZE
        val y0 = index / SIZE
        var total = 0L
        for ((dx, dy) in DIRECTIONS) {
            var run = 1
            var openEnds = 0
            for (sign in listOf(1, -1)) {
                var x = x0 + dx * sign
                var y = y0 + dy * sign
                while (inBounds(x, y) && board[x, y] == who) {
                    run++
                    x += dx * sign
                    y += dy * sign
                }
                if (inBounds(x, y) && board[x, y] == Cell.EMPTY) openEnds++
            }
            if (run >= RUN) return Long.MAX_VALUE / 4
            if (openEnds == 0) continue
            var worth = 1L
            repeat(run) { worth *= 12 }
            if (openEnds == 1) worth /= 4
            total += worth
        }
        return total
    }

    /**
     * The bot's move: the square that is worth most to it, or to you.
     *
     * Attack and defence are the same calculation from opposite sides, so both
     * are scored and the larger taken - with a nudge towards its own, so that
     * given equal threats it finishes its line rather than blocking yours.
     */
    fun botMove(board: Board): Int? {
        if (board.over) return null
        val empty = board.cells.indices.filter { board.cells[it] == Cell.EMPTY }
        if (empty.isEmpty()) return null
        // An empty board has no gradient to follow, so open in the middle,
        // which is where the most lines cross.
        if (empty.size == board.cells.size) return board.cells.size / 2
        return empty.maxByOrNull { index ->
            val mine = score(board, index, Cell.BOT)
            val yours = score(board, index, Cell.YOU)
            if (mine >= yours) mine + 1 else yours
        }
    }
}
