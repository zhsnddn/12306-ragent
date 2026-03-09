package com.ming.agent12306.knowledge.service;

import com.ming.agent12306.properties.AssistantKnowledgeProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;

@Service
public class RustfsObjectStorageService implements ObjectStorageService {

    private final AssistantKnowledgeProperties knowledgeProperties;
    private final S3Client s3Client;

    public RustfsObjectStorageService(AssistantKnowledgeProperties knowledgeProperties) {
        this.knowledgeProperties = knowledgeProperties;
        AssistantKnowledgeProperties.Storage storage = knowledgeProperties.getStorage();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(storage.getEndpoint()))
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey())
                ))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Override
    public void upload(String objectKey, String contentType, byte[] content) {
        ensureBucketExists();
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName())
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content)
        );
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName())
                .key(objectKey)
                .build());
    }

    @Override
    public String bucketName() {
        return knowledgeProperties.getStorage().getBucketName();
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName()).build());
        } catch (NoSuchBucketException ex) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName()).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName()).build());
                return;
            }
            throw ex;
        }
    }
}
