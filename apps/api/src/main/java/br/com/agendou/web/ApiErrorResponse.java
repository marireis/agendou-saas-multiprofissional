package br.com.agendou.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiErrorResponse(String code, String message,
        @JsonProperty("correlation_id") String correlationId) {}
