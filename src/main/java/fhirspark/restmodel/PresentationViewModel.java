package fhirspark.restmodel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PresentationViewModel(Map<UUID, List<SlideNode>> slides) {
}
