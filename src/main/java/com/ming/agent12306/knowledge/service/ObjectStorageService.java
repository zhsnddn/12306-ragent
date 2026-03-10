package com.ming.agent12306.knowledge.service;

/** 对象存储服务抽象接口 */
public interface ObjectStorageService {

    void upload(String objectKey, String contentType, byte[] content);

    void delete(String objectKey);

    String bucketName();
}
