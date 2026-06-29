package com.example.sensitivedetection.security.embedding;

/**
 * 文本 embedding 服务（对应设计文档 6.2）。
 */
public interface EmbeddingService {

    /**
     * 生成文本的 embedding 向量。
     *
     * @param text 输入文本
     * @return 向量（维度由配置决定，默认 1024）
     */
    float[] embed(String text);
}
