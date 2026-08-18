package com.bobfull.restaurantinsight.port;

import com.bobfull.restaurantinsight.dto.RestaurantFeedbackAnalysis;

public interface RestaurantFeedbackInsightPort {
    Result analyze(String content);
    record Result(RestaurantFeedbackAnalysis analysis, String provider, String modelName) { }
}
