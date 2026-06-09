package com.example.sensitivedetection.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LlmResponse {

    @JsonProperty("is_suspected_sensitive")
    private boolean suspectedSensitive;

    @JsonProperty("catalog_node")
    private String catalogNode;

    @JsonProperty("reason")
    private String reason;
}
