package com.example.sensitivedetection.ambiguity.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stage2Response {

    private List<Item> ambiguities = new ArrayList<>();
    private String summary;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("rule_code")
        private String ruleCode;
        private String detail;
        private String suggestion;
    }
}
