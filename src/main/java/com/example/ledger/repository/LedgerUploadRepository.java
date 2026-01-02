package com.example.ledger.repository;

/**
 * @author 霜月
 * @create 2025/12/21 00:59
 */

import com.example.ledger.entity.LedgerUpload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerUploadRepository extends JpaRepository<LedgerUpload, Long> {

    @Query("SELECT u FROM LedgerUpload u WHERE u.uploadNo = :uploadNo AND u.deleted = false")
    Optional<LedgerUpload> findByUploadNoAndDeletedFalse(@Param("uploadNo") String uploadNo);

    @Query("SELECT u FROM LedgerUpload u WHERE u.unitName = :unitName AND u.deleted = false ORDER BY u.uploadTime DESC")
    List<LedgerUpload> findByUnitNameAndDeletedFalse(@Param("unitName") String unitName);

    @Query("SELECT u FROM LedgerUpload u WHERE u.templateId = :templateId AND u.deleted = false ORDER BY u.uploadTime DESC")
    List<LedgerUpload> findByTemplateIdAndDeletedFalse(@Param("templateId") Long templateId);

    @Query("SELECT u FROM LedgerUpload u WHERE u.userId = :userId AND u.deleted = false ORDER BY u.uploadTime DESC")
    Page<LedgerUpload> findByUserIdAndDeletedFalse(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT u FROM LedgerUpload u WHERE u.deleted = false ORDER BY u.uploadTime DESC")
    Page<LedgerUpload> findAllActive(Pageable pageable);

    @Query("SELECT u FROM LedgerUpload u WHERE u.unitName = :unitName AND u.uploadTime BETWEEN :startTime AND :endTime AND u.deleted = false")
    List<LedgerUpload> findByUnitNameAndUploadTimeBetween(
            @Param("unitName") String unitName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(u) FROM LedgerUpload u WHERE u.unitName = :unitName AND u.deleted = false")
    Long countByUnitNameAndDeletedFalse(@Param("unitName") String unitName);

    @Query("SELECT COUNT(u) FROM LedgerUpload u WHERE u.templateId = :templateId AND u.deleted = false")
    Long countByTemplateIdAndDeletedFalse(@Param("templateId") Long templateId);

    @Query("SELECT COUNT(u) FROM LedgerUpload u WHERE u.userId = :userId AND u.deleted = false")
    Long countByUserIdAndDeletedFalse(@Param("userId") Long userId);

    /**
     * 根据用户ID和单位名称查找上传记录
     */
    @Query("SELECT u FROM LedgerUpload u WHERE u.userId = :userId AND u.unitName = :unitName AND u.deleted = false ORDER BY u.uploadTime DESC")
    List<LedgerUpload> findByUserIdAndUnitName(@Param("userId") Long userId, @Param("unitName") String unitName);

    /**
     * 批量逻辑删除用户在某单位的上传记录
     */
    @Modifying
    @Transactional
    @Query("UPDATE LedgerUpload u SET u.deleted = true, u.importStatus = 'REPLACED', u.completedTime = :updateTime WHERE u.userId = :userId AND u.unitName = :unitName AND u.deleted = false")
    int markUploadsAsDeleted(@Param("userId") Long userId,
                             @Param("unitName") String unitName,
                             @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查找用户在某单位的最新上传记录
     */
    @Query("SELECT u FROM LedgerUpload u WHERE u.userId = :userId AND u.unitName = :unitName AND u.deleted = false ORDER BY u.uploadTime DESC")
    Optional<LedgerUpload> findLatestByUserIdAndUnitName(@Param("userId") Long userId, @Param("unitName") String unitName);

    /**
     * 统计用户在某单位的上传记录数
     */
    @Query("SELECT COUNT(u) FROM LedgerUpload u WHERE u.userId = :userId AND u.unitName = :unitName AND u.deleted = false")
    Long countByUserIdAndUnitName(@Param("userId") Long userId, @Param("unitName") String unitName);

    /**
     * 根据用户ID查找上传记录（不分页）
     */
    @Query("SELECT u FROM LedgerUpload u WHERE u.userId = :userId AND u.deleted = false ORDER BY u.uploadTime DESC")
    List<LedgerUpload> findByUserId(@Param("userId") Long userId);

    @Query("SELECT u FROM LedgerUpload u WHERE u.userId = :userId AND u.unitName = :unitName AND u.deleted = false ORDER BY u.uploadTime DESC")
    Page<LedgerUpload> findByUserIdAndUnitName(@Param("userId") Long userId,
                                               @Param("unitName") String unitName,
                                               Pageable pageable);
}