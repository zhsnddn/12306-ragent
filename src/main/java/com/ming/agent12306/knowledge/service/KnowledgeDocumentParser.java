package com.ming.agent12306.knowledge.service;

import com.ming.agent12306.common.exception.BusinessException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/** 知识文档解析服务 */
@Component
public class KnowledgeDocumentParser {

    private final Tika tika = new Tika();

    public String parse(byte[] content) {
        try {
            String text = tika.parseToString(new ByteArrayInputStream(content));
            if (text == null || text.isBlank()) {
                throw new BusinessException("文档解析后内容为空");
            }
            return text;
        } catch (IOException | TikaException ex) {
            throw new BusinessException("文档解析失败: " + ex.getMessage());
        }
    }
}
