package com.example.ledger.repository;

/**
 * @author 霜月
 * @create 2025/12/20 22:20
 */

import com.example.ledger.entity.TemplateStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateStyleRepository extends JpaRepository<TemplateStyle, Long> {
    List<TemplateStyle> findByTemplateId(Long templateId);
    List<TemplateStyle> findByTemplateIdAndRowIndexLessThan(Long templateId, Integer rowIndex);
    void deleteByTemplateId(Long templateId);
}