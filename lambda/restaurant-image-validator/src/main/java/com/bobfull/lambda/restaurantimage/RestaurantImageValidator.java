package com.bobfull.lambda.restaurantimage;

import java.io.IOException;
import java.util.Locale;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class RestaurantImageValidator {

    private static final long DEFAULT_MAX_FILE_SIZE = 5L * 1024L * 1024L;

    private final S3Client s3Client;
    private final RestaurantImageKeyResolver keyResolver;
    private final ImageSignatureDetector signatureDetector;
    private final String configuredBucket;
    private final long maxFileSize;

    public RestaurantImageValidator(
            S3Client s3Client,
            RestaurantImageKeyResolver keyResolver,
            ImageSignatureDetector signatureDetector,
            String configuredBucket,
            long maxFileSize
    ) {
        this.s3Client = s3Client;
        this.keyResolver = keyResolver;
        this.signatureDetector = signatureDetector;
        this.configuredBucket = configuredBucket;
        this.maxFileSize = maxFileSize;
    }

    static RestaurantImageValidator createDefault() {
        return new RestaurantImageValidator(
                S3Client.create(),
                new RestaurantImageKeyResolver(),
                new ImageSignatureDetector(),
                System.getenv("S3_IMAGE_BUCKET"),
                DEFAULT_MAX_FILE_SIZE
        );
    }

    public void validateAndPromote(String eventBucket, String key) {
        if (!keyResolver.isTargetTempKey(key)) {
            return;
        }
        String bucket = resolveBucket(eventBucket);
        try {
            RestaurantImageKeyResolver.RestaurantImageObject imageObject = keyResolver.resolveTempKey(key);
            HeadObjectResponse headObject = headTempObjectOrReturnIfAlreadyPromoted(bucket, imageObject);
            if (headObject == null) {
                return;
            }
            validateSize(headObject.contentLength());
            validateContentType(headObject.contentType(), imageObject.contentType());
            validateActualImage(bucket, imageObject);
            copyToFinalKey(bucket, imageObject);
            deleteObject(bucket, imageObject.tempKey());
        } catch (InvalidRestaurantImageException exception) {
            deleteQuietly(bucket, key);
            System.out.println("Invalid restaurant image was deleted. key=" + key + ", reason=" + exception.getMessage());
        }
    }

    private String resolveBucket(String eventBucket) {
        if (configuredBucket != null && !configuredBucket.isBlank()) {
            return configuredBucket;
        }
        return eventBucket;
    }

    private HeadObjectResponse headObject(String bucket, String key) {
        return s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    private HeadObjectResponse headTempObjectOrReturnIfAlreadyPromoted(
            String bucket,
            RestaurantImageKeyResolver.RestaurantImageObject imageObject
    ) {
        try {
            return headObject(bucket, imageObject.tempKey());
        } catch (S3Exception exception) {
            if (isNotFound(exception) && objectExists(bucket, imageObject.finalKey())) {
                System.out.println("Restaurant image was already promoted. key=" + imageObject.tempKey());
                return null;
            }
            throw exception;
        }
    }

    private boolean objectExists(String bucket, String key) {
        try {
            headObject(bucket, key);
            return true;
        } catch (S3Exception exception) {
            if (isNotFound(exception)) {
                return false;
            }
            throw exception;
        }
    }

    private boolean isNotFound(S3Exception exception) {
        return exception instanceof NoSuchKeyException || exception.statusCode() == 404;
    }

    private void validateSize(Long contentLength) {
        if (contentLength == null || contentLength <= 0 || contentLength > maxFileSize) {
            throw new InvalidRestaurantImageException("invalid image size");
        }
    }

    private void validateContentType(String actualContentType, String expectedContentType) {
        if (actualContentType == null
                || !actualContentType.toLowerCase(Locale.ROOT).equals(expectedContentType)) {
            throw new InvalidRestaurantImageException("content type mismatch");
        }
    }

    private void validateActualImage(
            String bucket,
            RestaurantImageKeyResolver.RestaurantImageObject imageObject
    ) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(imageObject.tempKey())
                .build();
        try (ResponseInputStream<GetObjectResponse> objectStream = s3Client.getObject(request)) {
            byte[] imageBytes = objectStream.readAllBytes();
            String detectedContentType = signatureDetector.detect(imageBytes)
                    .orElseThrow(() -> new InvalidRestaurantImageException("invalid image file"));
            if (!detectedContentType.equals(imageObject.contentType())) {
                throw new InvalidRestaurantImageException("image signature mismatch");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read image file", exception);
        }
    }

    private void copyToFinalKey(
            String bucket,
            RestaurantImageKeyResolver.RestaurantImageObject imageObject
    ) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(imageObject.tempKey())
                .destinationBucket(bucket)
                .destinationKey(imageObject.finalKey())
                .contentType(imageObject.contentType())
                .metadataDirective(MetadataDirective.REPLACE)
                .build());
    }

    private void deleteObject(String bucket, String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    private void deleteQuietly(String bucket, String key) {
        try {
            deleteObject(bucket, key);
        } catch (RuntimeException exception) {
            System.out.println("Failed to delete invalid restaurant image. key=" + key);
        }
    }
}
