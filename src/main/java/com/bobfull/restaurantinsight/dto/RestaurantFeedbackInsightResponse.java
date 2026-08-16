package com.bobfull.restaurantinsight.dto;

import com.bobfull.restaurantinsight.repository.RestaurantFeedbackInsightRepository.Aggregation;

public record RestaurantFeedbackInsightResponse(String category, String aspectType, String normalizedAspect, String opinionType, String sentiment, long count, String summary) {
    public static RestaurantFeedbackInsightResponse from(Aggregation result) {
        return new RestaurantFeedbackInsightResponse(result.getCategory().name(), result.getAspectType().name(), result.getAspect(), result.getOpinionType().name(), result.getSentiment().name(), result.getSenderCount(), result.getAspect() + " " + opinionKorean(result.getOpinionType().name()) + "에 대한 " + sentimentKorean(result.getSentiment().name()) + " 의견 " + result.getSenderCount() + "명");
    }
    private static String sentimentKorean(String sentiment) { return switch (sentiment) { case "POSITIVE" -> "긍정"; case "NEGATIVE" -> "부정"; default -> "중립"; }; }
    private static String opinionKorean(String opinion) {
        return switch (opinion) {
            case "TASTE" -> "맛"; case "TEXTURE" -> "식감"; case "SALTINESS" -> "간"; case "SPICINESS" -> "매운맛";
            case "SWEETNESS" -> "단맛"; case "PORTION" -> "양"; case "FRESHNESS" -> "신선도"; case "TEMPERATURE" -> "온도";
            case "FRIENDLINESS" -> "친절"; case "SERVICE_SPEED" -> "응대 속도"; case "PRICE_LEVEL" -> "가격";
            case "CLEANLINESS" -> "청결"; case "WAITING" -> "대기 시간"; default -> "평가";
        };
    }
}
