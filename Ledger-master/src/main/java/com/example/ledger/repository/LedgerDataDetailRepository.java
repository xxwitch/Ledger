package com.example.ledger.repository;

/**
 * @author 霜月
 * @create 2025/12/20 22:21
 */

import com.example.ledger.entity.LedgerDataDetail;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerDataDetailRepository extends JpaRepository<LedgerDataDetail, Long> {
    List<LedgerDataDetail> findByDataId(Long dataId);
    List<LedgerDataDetail> findByDataIdIn(List<Long> dataIds);
    List<LedgerDataDetail> findByFieldNameAndFieldValue(String fieldName, String fieldValue);

    /**
     * 根据数据ID列表删除明细数据（用于覆盖更新）
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM LedgerDataDetail d WHERE d.dataId IN :dataIds")
    void deleteByDataIdIn(@Param("dataIds") List<Long> dataIds);

    /**
     * 批量逻辑删除明细数据
     */
    @Modifying
    @Transactional
    @Query("UPDATE LedgerDataDetail d SET d.isEmpty = true, d.isValid = false WHERE d.dataId IN :dataIds")
    int markDetailsAsDeleted(@Param("dataIds") List<Long> dataIds);
}