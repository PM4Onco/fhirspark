package fhirspark.restmodel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PresentationRequest(Map<UUID, List<SlideNode>> slides) {
}
