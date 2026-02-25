package com.example.ledger.repository;

/**
 * @author 霜月
 * @create 2025/12/20 22:20
 */

import com.example.ledger.entity.TemplateField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateFieldRepository extends JpaRepository<TemplateField, Long> {

    List<TemplateField> findByTemplateIdOrderBySortOrder(Long templateId);

    List<TemplateField> findByTemplateIdAndDeletedFalse(Long templateId);

    Long countByTemplateIdAndDeletedFalse(Long templateId);
}