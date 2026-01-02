package com.example.ledger.repository;

/**
 * @author 霜月
 * @create 2025/12/20 22:21
 */

import com.example.ledger.entity.LedgerData;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerDataRepository extends JpaRepository<LedgerData, Long>, JpaSpecificationExecutor<LedgerData> {

    // 根据单位名称查找未删除的数据
    @Query("SELECT d FROM LedgerData d WHERE d.unitName = :unitName AND d.deleted = false ORDER BY d.createdTime DESC")
    List<LedgerData> findByUnitNameAndDeletedFalse(@Param("unitName") String unitName);

    // 根据ID列表查找未删除的数据
    @Query("SELECT d FROM LedgerData d WHERE d.id IN :ids AND d.deleted = false")
    List<LedgerData> findByIdInAndDeletedFalse(@Param("ids") List<Long> ids);

    // 根据模板ID查找未删除的数据
    @Query("SELECT d FROM LedgerData d WHERE d.templateId = :templateId AND d.deleted = false ORDER BY d.createdTime DESC")
    List<LedgerData> findByTemplateIdAndDeletedFalse(@Param("templateId") Long templateId);

    // 根据上传ID查找未删除的数据
    @Query("SELECT d FROM LedgerData d WHERE d.uploadId = :uploadId AND d.deleted = false ORDER BY d.rowNumber")
    List<LedgerData> findByUploadIdAndDeletedFalse(@Param("uploadId") Long uploadId);

    // 统计方法
    @Query("SELECT COUNT(d) FROM LedgerData d WHERE d.templateId = :templateId AND d.deleted = false")
    Long countByTemplateIdAndDeletedFalse(@Param("templateId") Long templateId);

    @Query("SELECT COUNT(d) FROM LedgerData d WHERE d.unitName = :unitName AND d.deleted = false")
    Long countByUnitNameAndDeletedFalse(@Param("unitName") String unitName);

    @Query("SELECT COUNT(d) FROM LedgerData d WHERE d.uploadId = :uploadId AND d.deleted = false")
    Long countByUploadIdAndDeletedFalse(@Param("uploadId") Long uploadId);

    // 添加统计查询方法
    @Query("SELECT d.unitName, COUNT(d) as total, " +
            "SUM(CASE WHEN d.validationStatus = 'VALID' THEN 1 ELSE 0 END) as valid, " +
            "SUM(CASE WHEN d.validationStatus = 'INVALID' THEN 1 ELSE 0 END) as invalid " +
            "FROM LedgerData d WHERE d.deleted = false " +
            "GROUP BY d.unitName")
    List<Object[]> countByUnitGroup();

    @Query("SELECT d.createdBy, COUNT(d) FROM LedgerData d " +
            "WHERE d.deleted = false " +
            "GROUP BY d.createdBy")
    List<Object[]> countByUserGroup();

    @Query("SELECT COUNT(d) FROM LedgerData d WHERE d.unitName = :unitName AND d.validationStatus = :status AND d.deleted = false")
    Long countByUnitNameAndValidationStatus(@Param("unitName") String unitName,
                                            @Param("status") String status);

    // 添加分页查询方法
    @Query("SELECT d FROM LedgerData d WHERE d.deleted = false")
    Page<LedgerData> findAllActive(Pageable pageable);

    @Query("SELECT d FROM LedgerData d WHERE d.unitName = :unitName AND d.deleted = false")
    Page<LedgerData> findByUnitNameAndDeletedFalse(@Param("unitName") String unitName, Pageable pageable);

    @Query("SELECT d FROM LedgerData d WHERE d.createdBy = :userId AND d.deleted = false")
    Page<LedgerData> findByCreatedByAndDeletedFalse(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT d FROM LedgerData d WHERE d.validationStatus = 'INVALID' AND d.deleted = false")
    Page<LedgerData> findByValidationStatusInvalidAndDeletedFalse(Pageable pageable);

    // 按多个上传ID查询 - 按上传ID和行号排序
    @Query("SELECT d FROM LedgerData d WHERE d.uploadId IN :uploadIds AND d.deleted = false ORDER BY d.uploadId ASC, d.rowNumber ASC")
    List<LedgerData> findByUploadIdInAndDeletedFalseOrderByRowNumber(@Param("uploadIds") List<Long> uploadIds);

    /**
     * 查询单位的最新数据
     */
    @Query("SELECT d FROM LedgerData d WHERE d.unitName = :unitName AND d.isLatest = true AND d.deleted = false")
    List<LedgerData> findByUnitNameAndIsLatestTrueAndDeletedFalse(@Param("unitName") String unitName);

    /**
     * 查询单位的历史数据（非最新）
     */
    @Query("SELECT d FROM LedgerData d WHERE d.unitName = :unitName AND d.isLatest = false AND d.deleted = false")
    List<LedgerData> findByUnitNameAndIsLatestFalseAndDeletedFalse(@Param("unitName") String unitName);

    /**
     * 根据历史数据ID查找原数据
     */
    @Query("SELECT d FROM LedgerData d WHERE d.historicalDataId = :historicalDataId AND d.deleted = false")
    Optional<LedgerData> findByHistoricalDataId(@Param("historicalDataId") Long historicalDataId);

    /**
     * 根据历史数据ID查找所有版本
     */
    @Query("SELECT d FROM LedgerData d WHERE d.historicalDataId = :historicalDataId OR d.id = :historicalDataId AND d.deleted = false ORDER BY d.dataVersion DESC")
    List<LedgerData> findDataVersions(@Param("historicalDataId") Long historicalDataId);

    /**
     * 查询指定批次的上传数据
     */
    @Query("SELECT d FROM LedgerData d WHERE d.uploadId = :uploadId AND d.deleted = false ORDER BY d.rowNumber")
    List<LedgerData> findByUploadIdOrderByRowNumber(@Param("uploadId") Long uploadId);

    /**
     * 根据数据版本号查询
     */
    @Query("SELECT d FROM LedgerData d WHERE d.unitName = :unitName AND d.dataVersion = :version AND d.deleted = false")
    List<LedgerData> findByUnitNameAndDataVersion(@Param("unitName") String unitName, @Param("version") Integer version);

    /**
     * 获取单位的最新数据版本号
     */
    @Query("SELECT MAX(d.dataVersion) FROM LedgerData d WHERE d.unitName = :unitName AND d.deleted = false")
    Integer findMaxDataVersionByUnitName(@Param("unitName") String unitName);

    /**
     * 批量更新数据为最新状态
     */
    @Modifying
    @Transactional
    @Query("UPDATE LedgerData d SET d.isLatest = :isLatest WHERE d.id IN :ids")
    void updateIsLatestStatus(@Param("ids") List<Long> ids, @Param("isLatest") Boolean isLatest);

    /**
     * 根据用户ID和单位名称查找数据（用于覆盖更新）
     */
    @Query("SELECT d FROM LedgerData d WHERE d.createdBy = :userId AND d.unitName = :unitName AND d.deleted = false")
    List<LedgerData> findByUserIdAndUnitName(@Param("userId") Long userId, @Param("unitName") String unitName);

    /**
     * 批量逻辑删除用户在某单位的所有数据（用于覆盖更新）
     */
    @Modifying
    @Transactional
    @Query("UPDATE LedgerData d SET d.deleted = true, d.dataStatus = 'DELETED', d.updatedTime = :updateTime WHERE d.createdBy = :userId AND d.unitName = :unitName AND d.deleted = false")
    int deleteAllByUserIdAndUnitName(@Param("userId") Long userId,
                                     @Param("unitName") String unitName,
                                     @Param("updateTime") LocalDateTime updateTime);

    /**
     * 物理删除已标记为删除的用户数据（可选，用于清理）
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM LedgerData d WHERE d.createdBy = :userId AND d.unitName = :unitName AND d.deleted = true")
    int permanentlyDeleteByUserIdAndUnitName(@Param("userId") Long userId, @Param("unitName") String unitName);

    /**
     * 查找用户在某单位的最新上传数据
     */
    @Query("SELECT d FROM LedgerData d WHERE d.createdBy = :userId AND d.unitName = :unitName AND d.isLatest = true AND d.deleted = false ORDER BY d.createdTime DESC")
    List<LedgerData> findLatestByUserIdAndUnitName(@Param("userId") Long userId, @Param("unitName") String unitName);

    /**
     * 统计用户在某单位的数据量
     */
    @Query("SELECT COUNT(d) FROM LedgerData d WHERE d.createdBy = :userId AND d.unitName = :unitName AND d.deleted = false")
    Long countByUserIdAndUnitName(@Param("userId") Long userId, @Param("unitName") String unitName);

    /**
     * 查找同一用户同单位的所有上传批次
     */
    @Query("SELECT DISTINCT d.uploadId FROM LedgerData d WHERE d.createdBy = :userId AND d.unitName = :unitName AND d.deleted = false")
    List<Long> findUploadIdsByUserIdAndUnitName(@Param("userId") Long userId, @Param("unitName") String unitName);

    /**
     * 按年份查询数据（原生SQL）
     */
    @Query(value = "SELECT * FROM ledger_data WHERE YEAR(created_time) = :year AND deleted = false ORDER BY created_time DESC",
            nativeQuery = true)
    List<LedgerData> findByYear(@Param("year") int year);

    /**
     * 按年月查询数据（原生SQL）
     */
    @Query(value = "SELECT * FROM ledger_data WHERE YEAR(created_time) = :year AND MONTH(created_time) = :month AND deleted = false ORDER BY created_time DESC",
            nativeQuery = true)
    List<LedgerData> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * 按单位和年月查询数据（原生SQL）
     */
    @Query(value = "SELECT * FROM ledger_data WHERE unit_name = :unitName AND YEAR(created_time) = :year AND MONTH(created_time) = :month AND deleted = false ORDER BY created_time DESC",
            nativeQuery = true)
    List<LedgerData> findByUnitNameAndYearAndMonth(@Param("unitName") String unitName,
                                                   @Param("year") int year,
                                                   @Param("month") int month);

    /**
     * 按单位和年份查询数据（原生SQL）
     */
    @Query(value = "SELECT * FROM ledger_data WHERE unit_name = :unitName AND YEAR(created_time) = :year AND deleted = false ORDER BY created_time DESC",
            nativeQuery = true)
    List<LedgerData> findByUnitNameAndYear(@Param("unitName") String unitName, @Param("year") int year);

    /**
     * 获取所有不重复的年份
     */
    @Query(value = "SELECT DISTINCT YEAR(created_time) as year FROM ledger_data WHERE deleted = false ORDER BY year DESC",
            nativeQuery = true)
    List<Integer> findAllDistinctYears();

    /**
     * 按单位获取不重复的年份
     */
    @Query(value = "SELECT DISTINCT YEAR(created_time) as year FROM ledger_data WHERE unit_name = :unitName AND deleted = false ORDER BY year DESC",
            nativeQuery = true)
    List<Integer> findDistinctYearsByUnitName(@Param("unitName") String unitName);

    /**
     * 获取所有不重复的年月
     */
    @Query(value = "SELECT DISTINCT CONCAT(YEAR(created_time), '-', LPAD(MONTH(created_time), 2, '0')) as yearMonth FROM ledger_data WHERE deleted = false ORDER BY yearMonth DESC",
            nativeQuery = true)
    List<String> findAllDistinctYearMonths();

    /**
     * 按单位获取不重复的年月
     */
    @Query(value = "SELECT DISTINCT CONCAT(YEAR(created_time), '-', LPAD(MONTH(created_time), 2, '0')) as yearMonth FROM ledger_data WHERE unit_name = :unitName AND deleted = false ORDER BY yearMonth DESC",
            nativeQuery = true)
    List<String> findDistinctYearMonthsByUnitName(@Param("unitName") String unitName);

    /**
     * 按年份查询数据（原生SQL） - 修复排序字段
     */
    @Query(value = "SELECT * FROM ledger_data WHERE YEAR(created_time) = :year AND deleted = false ORDER BY created_time DESC",
            countQuery = "SELECT COUNT(*) FROM ledger_data WHERE YEAR(created_time) = :year AND deleted = false",
            nativeQuery = true)
    Page<LedgerData> findByYear(@Param("year") int year, Pageable pageable);

    /**
     * 按年月查询数据（原生SQL） - 修复排序字段
     */
    @Query(value = "SELECT * FROM ledger_data WHERE YEAR(created_time) = :year AND MONTH(created_time) = :month AND deleted = false ORDER BY created_time DESC",
            countQuery = "SELECT COUNT(*) FROM ledger_data WHERE YEAR(created_time) = :year AND MONTH(created_time) = :month AND deleted = false",
            nativeQuery = true)
    Page<LedgerData> findByYearAndMonth(@Param("year") int year, @Param("month") int month, Pageable pageable);

    /**
     * 按单位和年份查询数据（原生SQL） - 修复排序字段
     */
    @Query(value = "SELECT * FROM ledger_data WHERE unit_name = :unitName AND YEAR(created_time) = :year AND deleted = false ORDER BY created_time DESC",
            countQuery = "SELECT COUNT(*) FROM ledger_data WHERE unit_name = :unitName AND YEAR(created_time) = :year AND deleted = false",
            nativeQuery = true)
    Page<LedgerData> findByUnitNameAndYear(@Param("unitName") String unitName,
                                           @Param("year") int year,
                                           Pageable pageable);

    /**
     * 按单位和年月查询数据（原生SQL） - 修复排序字段
     */
    @Query(value = "SELECT * FROM ledger_data WHERE unit_name = :unitName AND YEAR(created_time) = :year AND MONTH(created_time) = :month AND deleted = false ORDER BY created_time DESC",
            countQuery = "SELECT COUNT(*) FROM ledger_data WHERE unit_name = :unitName AND YEAR(created_time) = :year AND MONTH(created_time) = :month AND deleted = false",
            nativeQuery = true)
    Page<LedgerData> findByUnitNameAndYearAndMonth(@Param("unitName") String unitName,
                                                   @Param("year") int year,
                                                   @Param("month") int month,
                                                   Pageable pageable);

    /**
     * 获取年份统计数据
     */
    @Query(value = "SELECT YEAR(created_time) as year, COUNT(*) as count FROM ledger_data WHERE deleted = false GROUP BY YEAR(created_time) ORDER BY year DESC",
            nativeQuery = true)
    List<Object[]> getYearStatistics();

    /**
     * 按单位获取年份统计数据
     */
    @Query(value = "SELECT YEAR(created_time) as year, COUNT(*) as count FROM ledger_data WHERE unit_name = :unitName AND deleted = false GROUP BY YEAR(created_time) ORDER BY year DESC",
            nativeQuery = true)
    List<Object[]> getYearStatisticsByUnitName(@Param("unitName") String unitName);
}