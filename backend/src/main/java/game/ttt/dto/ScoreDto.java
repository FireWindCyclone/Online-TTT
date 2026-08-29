package game.ttt.dto;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record ScoreDto(int score) {
    private static final int[] WIN_SCORES = {
            0b111000000, // r3
            0b000111000, // r2
            0b000000111, // r1
            0b100100100, // c3
            0b010010010, // c2
            0b001001001, // c1
            0b100010001, // d2
            0b001010100, // d1
    };

    public ScoreDto update(PosDto pos) {
        return new ScoreDto(score | (1 << pos.index()));
    }

    public Optional<ScoreDto> match() {
        for (int s : WIN_SCORES) {
            if ((s & score) == s) {
                return Optional.of(new ScoreDto(s));
            }
        }
        return Optional.empty();
    }

    public boolean isFull() {
        return score == 0b111111111;
    }

    public static ScoreDto combine(ScoreDto s1, ScoreDto s2) {
        return new ScoreDto(s1.score() + s2.score());
    }

    public List<Integer> list() {
        return IntStream.range(0, 9).filter(i -> ((score >>> i) & 1) == 1).boxed().collect(Collectors.toList());
    }

}
