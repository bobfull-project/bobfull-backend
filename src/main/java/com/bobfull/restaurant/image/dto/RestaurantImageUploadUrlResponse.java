package com.bobfull.restaurant.image.dto;

public record RestaurantImageUploadUrlResponse(
        String uploadUrl,
        String tempImageKey,
        String finalImageKey
) {
}
