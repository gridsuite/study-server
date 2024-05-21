/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.elasticsearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@Component
@Data
public final class ESIndex {

    // It's not simple SPEL but this syntax is managed by both ES and Spring
    public static final String STUDY_INDEX_NAME = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}studies";
    public static final String EQUIPMENTS_INDEX_NAME = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}equipments";
    public static final String TOMBSTONED_EQUIPMENTS_INDEX_NAME = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}tombstoned-equipments";

    private String studyIndexName;
    private String equipmentsIndexName;
    private String tombstonedEquipmentsIndexName;

    public ESIndex(@Value(STUDY_INDEX_NAME) String studyIndexName, @Value(EQUIPMENTS_INDEX_NAME) String equipmentsIndexName, @Value(TOMBSTONED_EQUIPMENTS_INDEX_NAME) String tombstonedEquipmentsIndexName) {
        this.studyIndexName = studyIndexName;
        this.equipmentsIndexName = equipmentsIndexName;
        this.tombstonedEquipmentsIndexName = tombstonedEquipmentsIndexName;
    }
}
