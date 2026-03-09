package com.ming.agent12306.knowledge.service;

public interface ObjectStorageService {

    void upload(String objectKey, String contentType, byte[] content);

    void delete(String objectKey);

    String bucketName();
}
