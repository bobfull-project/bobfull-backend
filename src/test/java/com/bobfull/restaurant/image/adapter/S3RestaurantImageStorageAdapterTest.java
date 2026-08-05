package com.bobfull.restaurant.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bobfull.restaurant.image.config.RestaurantImageS3Properties;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3RestaurantImageStorageAdapterTest {

    @Test
    void 업로드_url은_content_type만_서명_헤더에_포함한다() {
        // given
        RestaurantImageS3Properties properties = new RestaurantImageS3Properties(
                "bobfull-test-image-bucket",
                Duration.ofMinutes(5),
                Duration.ofMinutes(5)
        );
        try (S3Presigner s3Presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            S3RestaurantImageStorageAdapter adapter = new S3RestaurantImageStorageAdapter(
                    mock(S3Client.class),
                    s3Presigner,
                    properties
            );

            // when
            String uploadUrl = adapter.createUploadUrl(
                    "temp/restaurants/1/11111111-1111-1111-1111-111111111111.png",
                    "image/png",
                    1024L,
                    Duration.ofMinutes(5)
            );

            // then
            assertThat(queryParameter(uploadUrl, "X-Amz-SignedHeaders")).isEqualTo("content-type;host");
        }
    }

    private String queryParameter(String url, String name) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .filter(parameter -> parameter.length == 2)
                .filter(parameter -> parameter[0].equals(name))
                .map(parameter -> URLDecoder.decode(parameter[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElse(null);
    }
}
