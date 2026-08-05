package com.bobfull.lambda.restaurantimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

class RestaurantImageKeyResolverTest {

    private final RestaurantImageKeyResolver keyResolver = new RestaurantImageKeyResolver();

    @Test
    void temp_key를_final_key로_변환한다() {
        // when
        RestaurantImageKeyResolver.RestaurantImageObject result = keyResolver.resolveTempKey(
                "temp/restaurants/1/11111111-1111-1111-1111-111111111111.png");

        // then
        assertThat(result.tempKey()).isEqualTo("temp/restaurants/1/11111111-1111-1111-1111-111111111111.png");
        assertThat(result.finalKey()).isEqualTo("restaurants/1/11111111-1111-1111-1111-111111111111.png");
        assertThat(result.contentType()).isEqualTo("image/png");
    }

    @Test
    void webp_확장자는_거부한다() {
        // when
        Throwable result = catchThrowable(() -> keyResolver.resolveTempKey(
                "temp/restaurants/1/11111111-1111-1111-1111-111111111111.webp"));

        // then
        assertThat(result).isInstanceOf(InvalidRestaurantImageException.class);
    }
}
