package com.bobfull.lambda.restaurantimage;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RestaurantImageKeyResolver {

    private static final String TEMP_PREFIX = "temp/restaurants/";
    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png"
    );

    public boolean isTargetTempKey(String key) {
        return key != null && key.startsWith(TEMP_PREFIX);
    }

    public RestaurantImageObject resolveTempKey(String key) {
        String[] parts = key.split("/");
        if (parts.length != 4 || !"temp".equals(parts[0]) || !"restaurants".equals(parts[1])) {
            throw new InvalidRestaurantImageException("invalid key path");
        }
        String ownerId = parts[2];
        if (!ownerId.matches("[1-9][0-9]*")) {
            throw new InvalidRestaurantImageException("invalid owner id");
        }
        String fileName = parts[3];
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new InvalidRestaurantImageException("invalid file name");
        }
        validateUuid(fileName.substring(0, dotIndex));
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        String contentType = CONTENT_TYPES_BY_EXTENSION.get(extension);
        if (contentType == null) {
            throw new InvalidRestaurantImageException("unsupported extension");
        }
        return new RestaurantImageObject(
                key,
                "restaurants/" + ownerId + "/" + fileName,
                contentType
        );
    }

    private void validateUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRestaurantImageException("invalid uuid");
        }
    }

    public record RestaurantImageObject(String tempKey, String finalKey, String contentType) {
    }
}
