package com.bobfull.sharedtable.dto;

import jakarta.validation.constraints.NotNull;

public record SharedTableCreateRequest(
        @NotNull Integer capacity
) {
}
