/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.studyexport.modifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
class ExportedModificationsInfosTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID ID_1 = UUID.randomUUID();
    private static final UUID ID_2 = UUID.randomUUID();
    private static final UUID ID_3 = UUID.randomUUID();
    private static final UUID ID_4 = UUID.randomUUID();
    private static final UUID ID_5 = UUID.randomUUID();
    private static final UUID ID_6 = UUID.randomUUID();
    private static final UUID ID_7 = UUID.randomUUID();
    private static final UUID LF_1 = UUID.randomUUID();
    private static final UUID LF_2 = UUID.randomUUID();

    @Test
    void testModificationsReferences() throws Exception {
        String json = "["
                + "{\"type\":\"BY_FILTER_DELETION\",\"filters\":[{\"id\":\"" + ID_1 + "\",\"name\":\"f1\"}]},"
                + "{\"type\":\"GENERATOR_SCALING\",\"variations\":[{\"filters\":[{\"id\":\"" + ID_2 + "\"}]},{\"filters\":null}]},"
                + "{\"type\":\"MODIFICATION_BY_ASSIGNMENT\",\"assignmentInfosList\":[{\"filters\":[{\"id\":\"" + ID_3 + "\"}]}]},"
                + "{\"type\":\"BY_FORMULA_MODIFICATION\",\"formulaInfosList\":[{\"filters\":[{\"id\":\"" + ID_4 + "\"}]}]},"
                + "{\"type\":\"GENERATION_DISPATCH\",\"generatorsWithoutOutage\":[{\"id\":\"" + ID_5 + "\"}],"
                + "\"generatorsWithFixedSupply\":[{\"id\":\"" + ID_6 + "\"}],"
                + "\"generatorsFrequencyReserve\":[{\"generatorsFilters\":[{\"id\":\"" + ID_7 + "\"}],\"frequencyReserve\":2.0}]},"
                + "{\"type\":\"BALANCES_ADJUSTMENT_MODIFICATION\",\"loadFlowParametersId\":\"" + LF_1 + "\"},"
                + "{\"type\":\"COMPOSITE_MODIFICATION\",\"modificationsInfos\":["
                + "{\"type\":\"BY_FILTER_DELETION\",\"filters\":[{\"id\":\"" + ID_1 + "\"}]},"
                + "{\"type\":\"BALANCES_ADJUSTMENT_MODIFICATION\",\"loadFlowParametersId\":\"" + LF_2 + "\"}]},"
                + "{\"type\":\"GENERATOR_MODIFICATION\",\"equipmentId\":\"GEN\"}]";
        ExportedModificationsInfos[] modifications = objectMapper.readValue(json, ExportedModificationsInfos[].class);
        assertEquals(Set.of(ID_1, ID_2, ID_3, ID_4, ID_5, ID_6, ID_7),
                Arrays.stream(modifications).flatMap(m -> m.getFilterUuids().stream()).collect(Collectors.toSet()));
        assertEquals(Set.of(LF_1, LF_2),
                Arrays.stream(modifications).flatMap(m -> m.getLoadFlowParametersUuids().stream()).collect(Collectors.toSet()));
        assertEquals(Set.of(), objectMapper.readValue("{}", ExportedModificationsInfos.class).getFilterUuids());
        assertEquals(Set.of(), objectMapper.readValue("{}", ExportedModificationsInfos.class).getContingencyListUuids());
    }
}
