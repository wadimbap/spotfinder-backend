package com.spotfinder.storage.service;

import java.io.InputStream;

public interface ObjectStorageService {

    void upload(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    );

    InputStream download(String objectKey);

    void delete(String objectKey);
}
