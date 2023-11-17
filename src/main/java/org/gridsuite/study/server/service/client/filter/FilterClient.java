package org.gridsuite.study.server.service.client.filter;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.FILTER_API_VERSION;

public interface FilterClient {
    String API_VERSION = FILTER_API_VERSION;
    String FILTER_END_POINT_EVALUATE = "filters/evaluate";

    String evaluateFilter(UUID networkUuid, String variantId, String filter);
}
