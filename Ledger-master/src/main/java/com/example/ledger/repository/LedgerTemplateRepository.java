package com.example.ledger.repository;

/**
 * @author 霜月
 * @create 2025/12/20 22:19
 */

import com.example.ledger.entity.LedgerTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerTemplateRepository extends JpaRepository<LedgerTemplate, Long> {

    // 根据单位名称查找模板（有删除标志）
    Optional<LedgerTemplate> findByUnitName(String unitName);

    // 根据单位名称查找未删除的模板
    @Query("SELECT t FROM LedgerTemplate t WHERE t.unitName = :unitName AND t.deleted = false")
    Optional<LedgerTemplate> findByUnitNameAndDeletedFalse(@Param("unitName") String unitName);

    // 根据ID查找未删除的模板
    @Query("SELECT t FROM LedgerTemplate t WHERE t.id = :id AND t.deleted = false")
    Optional<LedgerTemplate> findByIdAndDeletedFalse(@Param("id") Long id);

    // 查找所有未删除的模板
    @Query("SELECT t FROM LedgerTemplate t WHERE t.deleted = false")
    Page<LedgerTemplate> findByDeletedFalse(Pageable pageable);

    @Query("SELECT t FROM LedgerTemplate t WHERE t.deleted = false")
    List<LedgerTemplate> findAllActive();

    // 根据单位名称模糊查询未删除的模板
    @Query("SELECT t FROM LedgerTemplate t WHERE t.unitName LIKE %:unitName% AND t.deleted = false")
    Page<LedgerTemplate> findByUnitNameContainingAndDeletedFalse(@Param("unitName") String unitName, Pageable pageable);

    // 根据状态查找未删除的模板
    @Query("SELECT t FROM LedgerTemplate t WHERE t.status = :status AND t.deleted = false")
    Page<LedgerTemplate> findByStatusAndDeletedFalse(@Param("status") Integer status, Pageable pageable);

    // 根据单位名称和状态查找未删除的模板
    @Query("SELECT t FROM LedgerTemplate t WHERE t.unitName LIKE %:unitName% AND t.status = :status AND t.deleted = false")
    Page<LedgerTemplate> findByUnitNameContainingAndStatusAndDeletedFalse(
            @Param("unitName") String unitName,
            @Param("status") Integer status,
            Pageable pageable);

    // 检查单位名称是否存在（未删除）
    @Query("SELECT COUNT(t) > 0 FROM LedgerTemplate t WHERE t.unitName = :unitName AND t.deleted = false")
    boolean existsByUnitNameAndDeletedFalse(@Param("unitName") String unitName);
}