/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.Impacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.IdentifiableType;
import org.gridsuite.study.server.dto.impacts.AbstractBaseImpact;
import org.gridsuite.study.server.dto.impacts.CollectionElementImpact;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact.SimpleImpactType;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult.ApplicationStatus;
import org.gridsuite.study.server.notification.dto.EquipmentDeletionInfos;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.utils.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.gridsuite.study.server.utils.ImpactUtils.createCollectionElementImpact;
import static org.gridsuite.study.server.utils.ImpactUtils.createElementImpact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
class NetworkImpactTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testNetworkModificationResult() throws Exception {
        SimpleElementImpact creationImpact = createElementImpact(SimpleImpactType.CREATION, IdentifiableType.LINE, "lineId", new TreeSet<>(List.of("s2", "s1")));
        SimpleElementImpact modificationImpact = createElementImpact(SimpleImpactType.MODIFICATION, IdentifiableType.LOAD, "loadId", new TreeSet<>(List.of("s3")));
        SimpleElementImpact injectionDeletionImpact = createElementImpact(SimpleImpactType.DELETION, IdentifiableType.GENERATOR, "generatorId", new TreeSet<>(List.of("s4")));
        SimpleElementImpact substationModificationImpact = createElementImpact(SimpleImpactType.MODIFICATION, IdentifiableType.SUBSTATION, "substationId1", new TreeSet<>(List.of("substationId1")));
        SimpleElementImpact substationDeletionImpact = createElementImpact(SimpleImpactType.DELETION, IdentifiableType.SUBSTATION, "substationId2", new TreeSet<>(List.of("substationId2")));

        assertTrue(creationImpact.isSimple());
        assertTrue(creationImpact.isCreation());
        assertTrue(modificationImpact.isModification());
        assertTrue(substationDeletionImpact.isDeletion());

        List<AbstractBaseImpact> impacts = List.of(creationImpact, modificationImpact, injectionDeletionImpact, substationModificationImpact, substationDeletionImpact);

        assertEquals("{\"type\":\"SIMPLE\",\"elementType\":\"LINE\",\"simpleImpactType\":\"CREATION\",\"elementId\":\"lineId\",\"substationIds\":[\"s1\",\"s2\"]}", mapper.writeValueAsString(creationImpact));
        assertEquals("{\"type\":\"SIMPLE\",\"elementType\":\"LOAD\",\"simpleImpactType\":\"MODIFICATION\",\"elementId\":\"loadId\",\"substationIds\":[\"s3\"]}", mapper.writeValueAsString(modificationImpact));
        assertEquals("{\"type\":\"SIMPLE\",\"elementType\":\"GENERATOR\",\"simpleImpactType\":\"DELETION\",\"elementId\":\"generatorId\",\"substationIds\":[\"s4\"]}", mapper.writeValueAsString(injectionDeletionImpact));
        assertEquals("{\"type\":\"SIMPLE\",\"elementType\":\"SUBSTATION\",\"simpleImpactType\":\"MODIFICATION\",\"elementId\":\"substationId1\",\"substationIds\":[\"substationId1\"]}", mapper.writeValueAsString(substationModificationImpact));
        assertEquals("{\"type\":\"SIMPLE\",\"elementType\":\"SUBSTATION\",\"simpleImpactType\":\"DELETION\",\"elementId\":\"substationId2\",\"substationIds\":[\"substationId2\"]}", mapper.writeValueAsString(substationDeletionImpact));

        NetworkModificationResult result = NetworkModificationResult.builder()
            .applicationStatus(ApplicationStatus.ALL_OK)
            .lastGroupApplicationStatus(ApplicationStatus.ALL_OK)
            .networkImpacts(impacts)
            .build();
        assertEquals(TestUtils.resourceToString("/network-modification-result-with-all-ok.json"), mapper.writeValueAsString(result));

        result.setApplicationStatus(ApplicationStatus.WITH_WARNINGS);
        result.setLastGroupApplicationStatus(ApplicationStatus.WITH_WARNINGS);
        assertEquals(TestUtils.resourceToString("/network-modification-result-with-with-warnings.json"), mapper.writeValueAsString(result));

        result.setApplicationStatus(ApplicationStatus.WITH_ERRORS);
        result.setLastGroupApplicationStatus(ApplicationStatus.WITH_ERRORS);
        assertEquals(TestUtils.resourceToString("/network-modification-result-with-with-errors.json"), mapper.writeValueAsString(result));

        assertEquals("[s1, s2, s3, substationId1]", result.getImpactedSubstationsIds().toString());

        Set<AbstractBaseImpact> impactsSet = Set.copyOf(List.of(creationImpact, creationImpact, creationImpact));

        assertEquals("[{\"type\":\"SIMPLE\",\"elementType\":\"LINE\",\"simpleImpactType\":\"CREATION\",\"elementId\":\"lineId\",\"substationIds\":[\"s1\",\"s2\"]}]", mapper.writeValueAsString(impactsSet));
    }

    @Test
    void testCollectionElementImpact() throws Exception {

        CollectionElementImpact linesCollectionImpact = createCollectionElementImpact(IdentifiableType.LINE);
        CollectionElementImpact loadsCollectionImpact = createCollectionElementImpact(IdentifiableType.LOAD);
        CollectionElementImpact generatorsCollectionImpact = createCollectionElementImpact(IdentifiableType.GENERATOR);

        assertTrue(linesCollectionImpact.isCollection());

        assertEquals("{\"type\":\"COLLECTION\",\"elementType\":\"LINE\"}", mapper.writeValueAsString(linesCollectionImpact));
        assertEquals("{\"type\":\"COLLECTION\",\"elementType\":\"LOAD\"}", mapper.writeValueAsString(loadsCollectionImpact));
        assertEquals("{\"type\":\"COLLECTION\",\"elementType\":\"GENERATOR\"}", mapper.writeValueAsString(generatorsCollectionImpact));

        List<AbstractBaseImpact> impacts = List.of(linesCollectionImpact, loadsCollectionImpact, generatorsCollectionImpact);

        NetworkModificationResult result = NetworkModificationResult.builder()
            .applicationStatus(ApplicationStatus.ALL_OK)
            .lastGroupApplicationStatus(ApplicationStatus.ALL_OK)
            .networkImpacts(impacts)
            .build();
        assertEquals(TestUtils.resourceToString("/network-modification-result-collection-impacts-with-all-ok.json"), mapper.writeValueAsString(result));

        assertEquals(Set.of(), result.getImpactedSubstationsIds());

        Set<AbstractBaseImpact> impactsSet = Set.copyOf(List.of(linesCollectionImpact, linesCollectionImpact, linesCollectionImpact));

        assertEquals("[{\"type\":\"COLLECTION\",\"elementType\":\"LINE\"}]", mapper.writeValueAsString(impactsSet));
    }

    @Test
    void testNetworkImpact() throws Exception {
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
