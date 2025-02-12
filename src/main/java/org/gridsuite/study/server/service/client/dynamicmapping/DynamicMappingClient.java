/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicmapping;

import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.service.client.RestClient;

import java.util.List;

import static org.gridsuite.study.server.StudyConstants.DYNAMIC_MAPPING_API_VERSION;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface DynamicMappingClient extends RestClient {
    String API_VERSION = DYNAMIC_MAPPING_API_VERSION;
    String DYNAMIC_MAPPING_END_POINT_MAPPING = "mappings";

    List<MappingInfos> getAllMappings();

    List<ModelInfos> getModels(String mapping);
}
