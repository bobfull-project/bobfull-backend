package com.bobfull.lambda.restaurantimage;

import java.util.Optional;

public class ImageSignatureDetector {

    public Optional<String> detect(byte[] header) {
        if (isJpeg(header)) {
            return Optional.of("image/jpeg");
        }
        if (isPng(header)) {
            return Optional.of("image/png");
        }
        return Optional.empty();
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && unsigned(header[0]) == 0xFF
                && unsigned(header[1]) == 0xD8
                && unsigned(header[2]) == 0xFF;
    }

    private boolean isPng(byte[] header) {
        return header.length >= 8
                && unsigned(header[0]) == 0x89
                && unsigned(header[1]) == 0x50
                && unsigned(header[2]) == 0x4E
                && unsigned(header[3]) == 0x47
                && unsigned(header[4]) == 0x0D
                && unsigned(header[5]) == 0x0A
                && unsigned(header[6]) == 0x1A
                && unsigned(header[7]) == 0x0A;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }
}
