/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.studyexport.parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
class ExportedParametersReferencesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID ID_1 = UUID.randomUUID();
    private static final UUID ID_2 = UUID.randomUUID();
    private static final UUID ID_3 = UUID.randomUUID();
    private static final UUID ID_4 = UUID.randomUUID();
    private static final UUID ID_5 = UUID.randomUUID();

    @Test
    void testSecurityAnalysisReferences() throws Exception {
        String json = "{\"provider\":\"OpenLoadFlow\",\"contingencyListsInfos\":["
                + "{\"contingencyLists\":[\"" + ID_1 + "\",\"" + ID_2 + "\"],\"description\":\"d\",\"activated\":true},"
                + "{\"contingencyLists\":[\"" + ID_2 + "\"],\"activated\":false},{\"contingencyLists\":null}]}";
        SecurityAnalysisExportedParameters parameters = objectMapper.readValue(json, SecurityAnalysisExportedParameters.class);
        assertEquals(Set.of(ID_1, ID_2), parameters.getContingencyListUuids());
        assertEquals(Set.of(), parameters.getFilterUuids());
        assertEquals(Set.of(), objectMapper.readValue("{}", SecurityAnalysisExportedParameters.class).getContingencyListUuids());
    }

    @Test
    void testSensitivityAnalysisReferences() throws Exception {
        String json = "{\"provider\":\"OpenLoadFlow\","
                + "\"sensitivityInjectionsSet\":[{\"monitoredBranches\":[\"" + ID_1 + "\"],\"injections\":[\"" + ID_2 + "\"],\"contingencies\":[\"" + ID_3 + "\"],\"activated\":true}],"
                + "\"sensitivityInjection\":[{\"monitoredBranches\":[\"" + ID_1 + "\"],\"injections\":[],\"contingencies\":[]}],"
                + "\"sensitivityHVDC\":[{\"monitoredBranches\":[],\"hvdcs\":[\"" + ID_4 + "\"],\"contingencies\":[\"" + ID_5 + "\"]}],"
                + "\"sensitivityPST\":[{\"monitoredBranches\":[],\"psts\":[\"" + ID_2 + "\"],\"contingencies\":[]}],"
                + "\"sensitivityNodes\":[{\"monitoredVoltageLevels\":[\"" + ID_5 + "\"],\"equipmentsInVoltageRegulation\":[\"" + ID_3 + "\"],\"contingencies\":null}]}";
        SensitivityAnalysisExportedParameters parameters = objectMapper.readValue(json, SensitivityAnalysisExportedParameters.class);
        assertEquals(Set.of(ID_1, ID_2, ID_3, ID_4, ID_5), parameters.getFilterUuids());
        assertEquals(Set.of(ID_3, ID_5), parameters.getContingencyListUuids());
    }

    @Test
    void testVoltageInitReferences() throws Exception {
        String json = "{\"voltageLimitsModification\":[{\"priority\":0,\"filters\":[{\"filterId\":\"" + ID_1 + "\",\"filterName\":\"f1\"}]}],"
                + "\"voltageLimitsDefault\":[{\"priority\":0,\"filters\":[{\"filterId\":\"" + ID_2 + "\"}]}],"
                + "\"variableQGenerators\":[{\"filterId\":\"" + ID_3 + "\"}],"
                + "\"variableTwoWindingsTransformers\":[{\"filterId\":\"" + ID_4 + "\"}],"
                + "\"variableShuntCompensators\":[{\"filterId\":null}],\"updateBusVoltage\":true}";
        VoltageInitExportedParameters parameters = objectMapper.readValue(json, VoltageInitExportedParameters.class);
        assertEquals(Set.of(ID_1, ID_2, ID_3, ID_4), parameters.getFilterUuids());
        assertEquals(Set.of(), parameters.getContingencyListUuids());
    }

    @Test
    void testPccMinReferences() throws Exception {
        String json = "{\"uuid\":\"" + ID_5 + "\",\"filters\":[\"" + ID_1 + "\"]}";
        PccMinExportedParameters parameters = objectMapper.readValue(json, PccMinExportedParameters.class);
        assertEquals(Set.of(ID_1), parameters.getFilterUuids());
        assertEquals(Set.of(), objectMapper.readValue("{}", PccMinExportedParameters.class).getFilterUuids());
    }
}
