package projet2.banks.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import projet2.banks.transaction.entity.OutboxEvent;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByPublishedFalse();
}
