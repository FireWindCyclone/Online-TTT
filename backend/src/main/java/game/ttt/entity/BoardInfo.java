package game.ttt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "BOARDS")
public class BoardInfo {

    @Id
    @Column(name = "ID", length = 4)
    private String id;

    @Column(name = "BOARD", nullable = false, columnDefinition = "CHAR(9)")
    private String board = "*********";

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version = 0L;

    // For Hibernate
    protected BoardInfo() {
    }

    public BoardInfo(String gameId) {
        this.id = gameId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBoard() {
        return board;
    }

    public void setBoard(String board) {
        this.board = board;
    }

}
