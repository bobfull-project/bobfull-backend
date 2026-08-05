package com.bobfull.lambda.restaurantimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
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
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class RestaurantImageValidatorTest {

    private static final String TEMP_KEY = "temp/restaurants/1/11111111-1111-1111-1111-111111111111.png";
    private static final String FINAL_KEY = "restaurants/1/11111111-1111-1111-1111-111111111111.png";
    private static final String SECOND_TEMP_KEY = "temp/restaurants/1/22222222-2222-2222-2222-222222222222.png";

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
    void 유효한_temp_이미지를_final_key로_복사하고_temp를_삭제한다() throws IOException {
        // given
        given(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(1024L).contentType("image/png").build());
        given(s3Client.getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .willReturn(responseInputStream(imageBytes("png")));

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
                .willReturn(responseInputStream(new byte[] {
                        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03
                }));

        // when
        validator.validateAndPromote("event-bucket", TEMP_KEY);

        // then
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void 이미_승격된_temp_이벤트가_다시_전달되면_정상_종료한다() throws IOException {
        // given
        given(s3Client.headObject(argThat((HeadObjectRequest request) ->
                request != null && TEMP_KEY.equals(request.key()))))
                .willReturn(HeadObjectResponse.builder().contentLength(1024L).contentType("image/png").build())
                .willThrow(S3Exception.builder().statusCode(404).message("not found").build());
        given(s3Client.headObject(argThat((HeadObjectRequest request) ->
                request != null && FINAL_KEY.equals(request.key()))))
                .willReturn(HeadObjectResponse.builder().build());
        given(s3Client.getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .willReturn(responseInputStream(imageBytes("png")));

        // when
        validator.validateAndPromote("event-bucket", TEMP_KEY);
        validator.validateAndPromote("event-bucket", TEMP_KEY);

        // then
        verify(s3Client, times(1)).copyObject(any(CopyObjectRequest.class));
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 앞_레코드가_이미_승격된_재시도에서는_뒤_레코드까지_도달할수_있다() {
        // given
        given(s3Client.headObject(argThat((HeadObjectRequest request) ->
                request != null && TEMP_KEY.equals(request.key()))))
                .willThrow(S3Exception.builder().statusCode(404).message("not found").build());
        given(s3Client.headObject(argThat((HeadObjectRequest request) ->
                request != null && FINAL_KEY.equals(request.key()))))
                .willReturn(HeadObjectResponse.builder().build());
        given(s3Client.headObject(argThat((HeadObjectRequest request) ->
                request != null && SECOND_TEMP_KEY.equals(request.key()))))
                .willThrow(S3Exception.builder().statusCode(500).message("S3 error").build());

        // when
        validator.validateAndPromote("event-bucket", TEMP_KEY);
        Throwable result = catchThrowable(() -> validator.validateAndPromote("event-bucket", SECOND_TEMP_KEY));

        // then
        assertThat(result).isInstanceOf(S3Exception.class);
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

    private byte[] imageBytes(String formatName) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, formatName, outputStream);
        return outputStream.toByteArray();
    }
}
