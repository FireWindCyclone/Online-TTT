package game.ttt.dto;

public record UpdatePosDto(Integer row, Integer col) {
    private static final int MAX_ROWS = 3;
    private static final int MAX_COLS = 3;

    public UpdatePosDto {
        if ((row < 0 || col < 0) || (row >= MAX_ROWS || col >= MAX_COLS)) {
            throw new IllegalArgumentException("Invalid row " + row + " or column " + col);
        }
    }

    public int index() {
        return (row * MAX_COLS) + col;
    }

    @Override
    public String toString() {
        return "(" + row + "," + col + ")";
    }

}
