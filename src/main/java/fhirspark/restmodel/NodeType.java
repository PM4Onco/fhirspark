package fhirspark.restmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum NodeType {
    @JsonProperty("image")
    IMAGE,
    @JsonProperty("text")
    TEXT,
    @JsonProperty("html")
    HTML,
}
