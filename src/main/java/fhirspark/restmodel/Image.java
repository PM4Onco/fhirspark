package fhirspark.restmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hl7.fhir.utilities.MimeType;

public record Image(
    @JsonProperty("contentType") MimeType contentType,
    @JsonProperty("data") String data
) {
}
