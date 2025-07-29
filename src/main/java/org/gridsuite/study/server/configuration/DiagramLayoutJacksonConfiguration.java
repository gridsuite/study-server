/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.AbstractDiagramLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.AbstractDiagramLayoutJsonMapper;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Jackson configuration for diagram layout polymorphic serialization.
 * This external configuration avoids circular dependencies between the abstract base class
 * and its subtypes by using a mixin approach with type mappings.
 */
@Configuration
public class DiagramLayoutJacksonConfiguration {

    private final ObjectMapper objectMapper;

    public DiagramLayoutJacksonConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void configureDiagramLayoutMixIn() {
        objectMapper.addMixIn(AbstractDiagramLayout.class, AbstractDiagramLayoutJsonMapper.class);
    }
}
