package game.ttt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import game.ttt.entity.BoardInfo;

public interface GameRepository extends JpaRepository<BoardInfo, String> {

}
