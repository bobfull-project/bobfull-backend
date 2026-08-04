package com.bobfull.lambda.restaurantimage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageSignatureDetectorTest {

    private final ImageSignatureDetector imageSignatureDetector = new ImageSignatureDetector();

    @Test
    void jpeg_시그니처를_감지한다() {
        // when
        String result = imageSignatureDetector.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})
                .orElseThrow();

        // then
        assertThat(result).isEqualTo("image/jpeg");
    }

    @Test
    void png_시그니처를_감지한다() {
        // when
        String result = imageSignatureDetector.detect(new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        }).orElseThrow();

        // then
        assertThat(result).isEqualTo("image/png");
    }

    @Test
    void 알_수_없는_시그니처는_empty를_반환한다() {
        // when & then
        assertThat(imageSignatureDetector.detect(new byte[] {0x01, 0x02, 0x03})).isEmpty();
    }
}
