package com.bobfull.lambda.restaurantimage;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageSignatureDetectorTest {

    private final ImageSignatureDetector imageSignatureDetector = new ImageSignatureDetector();

    @Test
    void jpeg_이미지_파일을_감지한다() throws IOException {
        // when
        String result = imageSignatureDetector.detect(imageBytes("jpg")).orElseThrow();

        // then
        assertThat(result).isEqualTo("image/jpeg");
    }

    @Test
    void png_이미지_파일을_감지한다() throws IOException {
        // when
        String result = imageSignatureDetector.detect(imageBytes("png")).orElseThrow();

        // then
        assertThat(result).isEqualTo("image/png");
    }

    @Test
    void jpeg_접두어만_있는_파일은_empty를_반환한다() {
        // when & then
        assertThat(imageSignatureDetector.detect(new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03
        })).isEmpty();
    }

    @Test
    void 알_수_없는_시그니처는_empty를_반환한다() {
        // when & then
        assertThat(imageSignatureDetector.detect(new byte[] {0x01, 0x02, 0x03})).isEmpty();
    }

    private byte[] imageBytes(String formatName) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, formatName, outputStream);
        return outputStream.toByteArray();
    }
}
