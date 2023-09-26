/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.Impacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.IdentifiableType;
import lombok.SneakyThrows;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult.ApplicationStatus;
import org.gridsuite.study.server.dto.modification.SimpleElementImpact;
import org.gridsuite.study.server.dto.modification.SimpleElementImpact.SimpleImpactType;
import org.gridsuite.study.server.notification.dto.EquipmentDeletionInfos;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.utils.TestUtils;
import org.junit.Test;

import java.util.*;

import static org.gridsuite.study.server.utils.ImpactUtils.createElementImpact;
import static org.junit.Assert.assertEquals;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class ElementImpactTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    @SneakyThrows
    public void testNetworkModificationResult() {
        SimpleElementImpact creationImpact = createElementImpact(SimpleImpactType.CREATION, IdentifiableType.LINE, "lineId", new TreeSet<>(List.of("s2", "s1")));
        SimpleElementImpact modificationImpact = createElementImpact(SimpleImpactType.MODIFICATION, IdentifiableType.LOAD, "loadId", new TreeSet<>(List.of("s3")));
        SimpleElementImpact injectionDeletionImpact = createElementImpact(SimpleImpactType.DELETION, IdentifiableType.GENERATOR, "generatorId", new TreeSet<>(List.of("s4")));
        SimpleElementImpact substationModificationImpact = createElementImpact(SimpleImpactType.MODIFICATION, IdentifiableType.SUBSTATION, "substationId1", new TreeSet<>(List.of("substationId1")));
        SimpleElementImpact substationDeletionImpact = createElementImpact(SimpleImpactType.DELETION, IdentifiableType.SUBSTATION, "substationId2", new TreeSet<>(List.of("substationId2")));

        Collection<SimpleElementImpact> impacts = List.of(creationImpact, modificationImpact, injectionDeletionImpact, substationModificationImpact, substationDeletionImpact);

        assertEquals("{\"impactType\":\"CREATION\",\"elementId\":\"lineId\",\"elementType\":\"LINE\",\"substationIds\":[\"s1\",\"s2\"]}", mapper.writeValueAsString(creationImpact));
        assertEquals("{\"impactType\":\"MODIFICATION\",\"elementId\":\"loadId\",\"elementType\":\"LOAD\",\"substationIds\":[\"s3\"]}", mapper.writeValueAsString(modificationImpact));
        assertEquals("{\"impactType\":\"DELETION\",\"elementId\":\"generatorId\",\"elementType\":\"GENERATOR\",\"substationIds\":[\"s4\"]}", mapper.writeValueAsString(injectionDeletionImpact));
        assertEquals("{\"impactType\":\"MODIFICATION\",\"elementId\":\"substationId1\",\"elementType\":\"SUBSTATION\",\"substationIds\":[\"substationId1\"]}", mapper.writeValueAsString(substationModificationImpact));
        assertEquals("{\"impactType\":\"DELETION\",\"elementId\":\"substationId2\",\"elementType\":\"SUBSTATION\",\"substationIds\":[\"substationId2\"]}", mapper.writeValueAsString(substationDeletionImpact));

        NetworkModificationResult result = NetworkModificationResult.builder()
            .networkImpacts((List<SimpleElementImpact>) impacts)
            .build();
        assertEquals(TestUtils.resourceToString("/network-modification-result-with-all-ok.json"), mapper.writeValueAsString(result));

        result.setApplicationStatus(ApplicationStatus.WITH_WARNINGS);
        result.setLastGroupApplicationStatus(ApplicationStatus.WITH_WARNINGS);
        assertEquals(TestUtils.resourceToString("/network-modification-result-with-with-warnings.json"), mapper.writeValueAsString(result));

        result.setApplicationStatus(ApplicationStatus.WITH_ERRORS);
        result.setLastGroupApplicationStatus(ApplicationStatus.WITH_ERRORS);
        assertEquals(TestUtils.resourceToString("/network-modification-result-with-with-errors.json"), mapper.writeValueAsString(result));

        assertEquals("[s1, s2, s3, substationId1]", result.getImpactedSubstationsIds().toString());

        impacts = new HashSet<>(List.of(creationImpact, creationImpact, creationImpact));

        assertEquals("[{\"impactType\":\"CREATION\",\"elementId\":\"lineId\",\"elementType\":\"LINE\",\"substationIds\":[\"s1\",\"s2\"]}]", mapper.writeValueAsString(impacts));
    }

    @Test
    @SneakyThrows
    public void testNetworkImpact() {
        NetworkImpactsInfos networkImpactsInfos = NetworkImpactsInfos.builder()
            .impactedSubstationsIds(new HashSet<>(List.of("s1", "s2")))
            .deletedEquipments(
                new HashSet<>(List.of(
                    EquipmentDeletionInfos.builder().equipmentType(IdentifiableType.LINE.name()).equipmentId("lineId").build(),
                    EquipmentDeletionInfos.builder().equipmentType(IdentifiableType.LOAD.name()).equipmentId("loadId").build()
                ))
            ).build();
        assertEquals(
            "{\"impactedSubstationsIds\":[\"s1\",\"s2\"],\"deletedEquipments\":[{\"equipmentId\":\"lineId\",\"equipmentType\":\"LINE\"},{\"equipmentId\":\"loadId\",\"equipmentType\":\"LOAD\"}]}",
            mapper.writeValueAsString(networkImpactsInfos)
        );
    }
}
