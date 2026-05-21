package com.spotfinder.storage.service.impl;

import com.spotfinder.storage.config.MinioProperties;
import com.spotfinder.storage.service.ObjectStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(minioProperties.bucket())
                            .build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(minioProperties.bucket())
                                .build()
                );
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize MinIO bucket", exception);
        }
    }

    @Override
    public void upload(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    ) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upload object to MinIO", exception);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to download object from MinIO", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to delete object from MinIO", exception);
        }
    }
}
