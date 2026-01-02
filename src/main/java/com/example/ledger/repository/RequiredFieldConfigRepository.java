package com.example.ledger.repository;

/**
 * @author 霜月
 * @create 2025/12/21 01:00
 */

import com.example.ledger.entity.RequiredFieldConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequiredFieldConfigRepository extends JpaRepository<RequiredFieldConfig, Long> {

    @Query("SELECT c FROM RequiredFieldConfig c WHERE c.templateId = :templateId AND c.isRequired = true AND c.deleted = false ORDER BY c.fieldName")
    List<RequiredFieldConfig> findByTemplateIdAndRequiredTrue(@Param("templateId") Long templateId);

    @Query("SELECT c FROM RequiredFieldConfig c WHERE c.templateId = :templateId AND c.deleted = false")
    List<RequiredFieldConfig> findByTemplateId(@Param("templateId") Long templateId);

    @Query("SELECT c FROM RequiredFieldConfig c WHERE c.templateId = :templateId AND c.fieldName = :fieldName AND c.deleted = false")
    Optional<RequiredFieldConfig> findByTemplateIdAndFieldName(
            @Param("templateId") Long templateId,
            @Param("fieldName") String fieldName);
}