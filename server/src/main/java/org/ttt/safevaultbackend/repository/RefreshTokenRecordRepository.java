package org.ttt.safevaultbackend.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ttt.safevaultbackend.entity.RefreshTokenRecord;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRecordRepository extends ListCrudRepository<RefreshTokenRecord, Long> {

    Optional<RefreshTokenRecord> findByJti(String jti);

    @Modifying
    @Query("UPDATE RefreshTokenRecord r SET r.rotated = true WHERE r.jti = :jti")
    void markRotated(@Param("jti") String jti);

    @Modifying
    @Query("UPDATE RefreshTokenRecord r SET r.revoked = true WHERE r.family = :family")
    int revokeFamily(@Param("family") String family);

    @Query("SELECT r FROM RefreshTokenRecord r WHERE r.family = :family AND r.revoked = false")
    java.util.List<RefreshTokenRecord> findActiveByFamily(@Param("family") String family);

    @Modifying
    @Query("DELETE FROM RefreshTokenRecord r WHERE r.expiresAt < :date")
    int deleteExpiredRecords(@Param("date") LocalDateTime date);
}
