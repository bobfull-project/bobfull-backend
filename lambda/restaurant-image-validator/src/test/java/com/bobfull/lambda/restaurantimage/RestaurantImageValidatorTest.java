package com.bobfull.lambda.restaurantimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class RestaurantImageValidatorTest {

    private static final String TEMP_KEY = "temp/restaurants/1/11111111-1111-1111-1111-111111111111.png";
    private static final String FINAL_KEY = "restaurants/1/11111111-1111-1111-1111-111111111111.png";

    @Mock
    private S3Client s3Client;

    private RestaurantImageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RestaurantImageValidator(
                s3Client,
                new RestaurantImageKeyResolver(),
                new ImageSignatureDetector(),
                "configured-bucket",
                5L * 1024L * 1024L
        );
    }

    @Test
    void 유효한_temp_이미지를_final_key로_복사하고_temp를_삭제한다() {
        // given
        given(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(1024L).contentType("image/png").build());
        given(s3Client.getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .willReturn(responseInputStream(new byte[] {
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                }));

        // when
        validator.validateAndPromote("event-bucket", TEMP_KEY);

        // then
        ArgumentCaptor<CopyObjectRequest> copyCaptor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).copyObject(copyCaptor.capture());
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(copyCaptor.getValue().sourceBucket()).isEqualTo("configured-bucket");
        assertThat(copyCaptor.getValue().sourceKey()).isEqualTo(TEMP_KEY);
        assertThat(copyCaptor.getValue().destinationKey()).isEqualTo(FINAL_KEY);
        assertThat(deleteCaptor.getValue().key()).isEqualTo(TEMP_KEY);
    }

    @Test
    void 이미지_시그니처가_맞지_않으면_temp를_삭제하고_재시도하지_않는다() {
        // given
        given(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(1024L).contentType("image/png").build());
        given(s3Client.getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .willReturn(responseInputStream(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));

        // when
        validator.validateAndPromote("event-bucket", TEMP_KEY);

        // then
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void s3_요청_실패는_lambda_재시도를_위해_예외를_전파한다() {
        // given
        given(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(500).message("S3 error").build());

        // when
        Throwable result = catchThrowable(() -> validator.validateAndPromote("event-bucket", TEMP_KEY));

        // then
        assertThat(result).isInstanceOf(S3Exception.class);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    private ResponseInputStream<GetObjectResponse> responseInputStream(byte[] bytes) {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes))
        );
    }
}
