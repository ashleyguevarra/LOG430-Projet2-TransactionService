package projet2.banks.transaction.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import projet2.banks.transaction.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findByPublishedFalse();

    long countByPublishedFalse();

    /**
     * Marque un batch d'events comme publies en une seule requete SQL.
     * Evite le N+1 de saveAll() sur des entites detachees (N SELECT merge + N UPDATE).
     */
    @Modifying
    @Transactional
    @Query("UPDATE OutboxEvent e SET e.published = true WHERE e.id IN :ids")
    void markPublishedByIds(@Param("ids") List<String> ids);
}
