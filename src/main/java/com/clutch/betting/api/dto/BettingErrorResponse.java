package com.clutch.betting.api.dto;

public record BettingErrorResponse(
        String code,
        String message
) {
}
