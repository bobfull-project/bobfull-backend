package com.bobfull.lambda.restaurantimage;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class RestaurantImageValidationHandler implements RequestHandler<S3Event, Void> {

    private final RestaurantImageValidator restaurantImageValidator;

    public RestaurantImageValidationHandler() {
        this(RestaurantImageValidator.createDefault());
    }

    RestaurantImageValidationHandler(RestaurantImageValidator restaurantImageValidator) {
        this.restaurantImageValidator = restaurantImageValidator;
    }

    @Override
    public Void handleRequest(S3Event event, Context context) {
        if (event == null || event.getRecords() == null) {
            return null;
        }
        for (S3EventNotificationRecord record : event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            String key = URLDecoder.decode(record.getS3().getObject().getKey(), StandardCharsets.UTF_8);
            restaurantImageValidator.validateAndPromote(bucket, key);
        }
        return null;
    }
}
