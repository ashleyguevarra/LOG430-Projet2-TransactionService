package projet2.banks.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import projet2.banks.transaction.entity.Transaction;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findByParticipantIdAndCreatedAtBetween(
            String participantId, LocalDateTime startDate, LocalDateTime endDate);
}
