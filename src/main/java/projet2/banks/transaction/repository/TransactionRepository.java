package projet2.banks.transaction.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import projet2.banks.transaction.entity.Transaction;
import projet2.banks.transaction.entity.TransactionSagaState;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

        @Query("""
                select t
                from Transaction t
                where (t.participantSenderId = :participantId or t.participantReceiverId = :participantId)
                    and t.createdAt between :startDate and :endDate
                """)
        List<Transaction> findByParticipantIdAndCreatedAtBetween(
                        @Param("participantId") String participantId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        List<Transaction> findByStatusInAndCreatedAtBefore(
                        List<TransactionSagaState> statuses,
                        LocalDateTime cutoff);
}
