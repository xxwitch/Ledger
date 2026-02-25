package com.example.ledger.repository;

/**
 * @author 霜月
 * @create 2025/12/21 16:39
 */

import com.example.ledger.entity.LedgerEditHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LedgerEditHistoryRepository extends JpaRepository<LedgerEditHistory, Long> {

    @Query("SELECT h FROM LedgerEditHistory h WHERE h.dataId = :dataId AND h.deleted = false ORDER BY h.editTime DESC")
    List<LedgerEditHistory> findByDataId(@Param("dataId") Long dataId);

    @Query("SELECT h FROM LedgerEditHistory h WHERE h.dataId IN :dataIds AND h.deleted = false ORDER BY h.editTime DESC")
    List<LedgerEditHistory> findByDataIdIn(@Param("dataIds") List<Long> dataIds);

    @Query("SELECT h FROM LedgerEditHistory h WHERE h.editedBy = :userId AND h.deleted = false ORDER BY h.editTime DESC")
    Page<LedgerEditHistory> findByEditedBy(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT h FROM LedgerEditHistory h WHERE h.editTime BETWEEN :startTime AND :endTime AND h.deleted = false ORDER BY h.editTime DESC")
    Page<LedgerEditHistory> findByEditTimeBetween(@Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime,
                                                  Pageable pageable);

    @Query("SELECT h FROM LedgerEditHistory h WHERE h.fieldName = :fieldName AND h.deleted = false ORDER BY h.editTime DESC")
    Page<LedgerEditHistory> findByFieldName(@Param("fieldName") String fieldName, Pageable pageable);
}