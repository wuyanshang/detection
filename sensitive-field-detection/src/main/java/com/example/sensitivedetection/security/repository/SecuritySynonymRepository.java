package com.example.sensitivedetection.security.repository;

import java.util.List;

/**
 * 同义词替换规则访问（对应设计文档 4.2）。
 */
public interface SecuritySynonymRepository {

    /**
     * 根据系统名与字段中文名匹配同义词目标词。
     * 优先系统级规则，其次全局规则；支持 EXACT/CONTAINS 匹配。
     *
     * @return 匹配到的 target_term 列表（按优先级排序）；无匹配返回空列表
     */
    List<String> findTargetTerms(String systemName, String columnChnName);

    boolean enabled();
}
