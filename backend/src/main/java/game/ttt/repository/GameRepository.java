package game.ttt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import game.ttt.entity.BoardInfo;

@Repository
public interface GameRepository extends JpaRepository<BoardInfo, Long> {

}
