package com.bobfull.restaurant.image.adapter;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ImageErrorCode;
import com.bobfull.restaurant.image.config.RestaurantImageS3Properties;
import com.bobfull.restaurant.image.port.RestaurantImageStoragePort;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3RestaurantImageStorageAdapter implements RestaurantImageStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RestaurantImageS3Properties properties;

    public S3RestaurantImageStorageAdapter(
            S3Client s3Client,
            S3Presigner s3Presigner,
            RestaurantImageS3Properties properties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public String createUploadUrl(String imageKey, String contentType, long contentLength, Duration expiration) {
        validateBucket();
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.imageBucket())
                .key(imageKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putObjectRequest)
                .build();
        try {
            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            throw new CustomException(ImageErrorCode.IMAGE_STORAGE_REQUEST_FAILED);
        }
    }

    @Override
    public String createGetUrl(String imageKey, Duration expiration) {
        if (!StringUtils.hasText(imageKey)) {
            return null;
        }
        validateBucket();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(request -> request.bucket(properties.imageBucket()).key(imageKey))
                .build();
        try {
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            throw new CustomException(ImageErrorCode.IMAGE_STORAGE_REQUEST_FAILED);
        }
    }

    @Override
    public boolean exists(String imageKey) {
        validateBucket();
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.imageBucket())
                    .key(imageKey)
                    .build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new CustomException(ImageErrorCode.IMAGE_STORAGE_REQUEST_FAILED);
        } catch (RuntimeException exception) {
            throw new CustomException(ImageErrorCode.IMAGE_STORAGE_REQUEST_FAILED);
        }
    }

    @Override
    public void delete(String imageKey) {
        if (!StringUtils.hasText(imageKey)) {
            return;
        }
        validateBucket();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.imageBucket())
                    .key(imageKey)
                    .build());
        } catch (RuntimeException exception) {
            throw new CustomException(ImageErrorCode.IMAGE_STORAGE_REQUEST_FAILED);
        }
    }

    private void validateBucket() {
        if (!StringUtils.hasText(properties.imageBucket())) {
            throw new CustomException(ImageErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
        }
    }
}
