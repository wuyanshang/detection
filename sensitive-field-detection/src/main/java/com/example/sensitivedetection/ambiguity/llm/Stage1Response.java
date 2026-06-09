package com.example.sensitivedetection.ambiguity.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stage1Response {

    @JsonProperty("disambiguation_success")
    private boolean disambiguationSuccess;

    @JsonProperty("disambiguation_path")
    private String disambiguationPath;

    private String summary;
}
