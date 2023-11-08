/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.Impacts;

import static org.gridsuite.study.server.utils.ImpactUtils.createCollectionElementImpact;
import static org.gridsuite.study.server.utils.ImpactUtils.createElementImpact;
import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import org.gridsuite.study.server.dto.impacts.AbstractBaseImpact;
import org.gridsuite.study.server.dto.impacts.AbstractBaseImpact.ImpactType;
import org.gridsuite.study.server.dto.impacts.CollectionElementImpact;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult.ApplicationStatus;
import org.gridsuite.study.server.notification.dto.EquipmentDeletionInfos;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.utils.TestUtils;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.IdentifiableType;

import lombok.SneakyThrows;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class ElementImpactTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    @SneakyThrows
    public void testNetworkModificationResult() {
        SimpleElementImpact creationImpact = createElementImpact(ImpactType.CREATION, IdentifiableType.LINE, "lineId", new TreeSet<>(List.of("s2", "s1")));
        SimpleElementImpact modificationImpact = createElementImpact(ImpactType.MODIFICATION, IdentifiableType.LOAD, "loadId", new TreeSet<>(List.of("s3")));
        SimpleElementImpact injectionDeletionImpact = createElementImpact(ImpactType.DELETION, IdentifiableType.GENERATOR, "generatorId", new TreeSet<>(List.of("s4")));
        SimpleElementImpact substationModificationImpact = createElementImpact(ImpactType.MODIFICATION, IdentifiableType.SUBSTATION, "substationId1", new TreeSet<>(List.of("substationId1")));
        SimpleElementImpact substationDeletionImpact = createElementImpact(ImpactType.DELETION, IdentifiableType.SUBSTATION, "substationId2", new TreeSet<>(List.of("substationId2")));

        List<AbstractBaseImpact> impacts = List.of(creationImpact, modificationImpact, injectionDeletionImpact, substationModificationImpact, substationDeletionImpact);

        assertEquals("{\"impactType\":\"CREATION\",\"elementType\":\"LINE\",\"elementId\":\"lineId\",\"substationIds\":[\"s1\",\"s2\"]}", mapper.writeValueAsString(creationImpact));
        assertEquals("{\"impactType\":\"MODIFICATION\",\"elementType\":\"LOAD\",\"elementId\":\"loadId\",\"substationIds\":[\"s3\"]}", mapper.writeValueAsString(modificationImpact));
        assertEquals("{\"impactType\":\"DELETION\",\"elementType\":\"GENERATOR\",\"elementId\":\"generatorId\",\"substationIds\":[\"s4\"]}", mapper.writeValueAsString(injectionDeletionImpact));
        assertEquals("{\"impactType\":\"MODIFICATION\",\"elementType\":\"SUBSTATION\",\"elementId\":\"substationId1\",\"substationIds\":[\"substationId1\"]}", mapper.writeValueAsString(substationModificationImpact));
        assertEquals("{\"impactType\":\"DELETION\",\"elementType\":\"SUBSTATION\",\"elementId\":\"substationId2\",\"substationIds\":[\"substationId2\"]}", mapper.writeValueAsString(substationDeletionImpact));

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

        HashSet<AbstractBaseImpact> impactsSet = new HashSet<>(List.of(creationImpact, creationImpact, creationImpact));

        assertEquals("[{\"impactType\":\"CREATION\",\"elementType\":\"LINE\",\"elementId\":\"lineId\",\"substationIds\":[\"s1\",\"s2\"]}]", mapper.writeValueAsString(impactsSet));
    }

    @Test
    @SneakyThrows
    public void testCollectionElementImpact() {

        CollectionElementImpact linesCollectionImpact = createCollectionElementImpact(IdentifiableType.LINE);
        CollectionElementImpact loadsCollectionImpact = createCollectionElementImpact(IdentifiableType.LOAD);
        CollectionElementImpact generatorsCollectionImpact = createCollectionElementImpact(IdentifiableType.GENERATOR);

        assertEquals("{\"impactType\":\"COLLECTION\",\"elementType\":\"LINE\"}", mapper.writeValueAsString(linesCollectionImpact));
        assertEquals("{\"impactType\":\"COLLECTION\",\"elementType\":\"LOAD\"}", mapper.writeValueAsString(loadsCollectionImpact));
        assertEquals("{\"impactType\":\"COLLECTION\",\"elementType\":\"GENERATOR\"}", mapper.writeValueAsString(generatorsCollectionImpact));

        List<AbstractBaseImpact> impacts = List.of(linesCollectionImpact, loadsCollectionImpact, generatorsCollectionImpact);

        NetworkModificationResult result = NetworkModificationResult.builder()
            .applicationStatus(ApplicationStatus.ALL_OK)
            .lastGroupApplicationStatus(ApplicationStatus.ALL_OK)
            .networkImpacts(impacts)
            .build();
        assertEquals(TestUtils.resourceToString("/network-modification-result-collection-impacts-with-all-ok.json"), mapper.writeValueAsString(result));

        // TODO impacted substations
        // assertEquals(Set.of(), result.getImpactedSubstationsIds());

        HashSet<AbstractBaseImpact> impactsSet = new HashSet<>(List.of(linesCollectionImpact, linesCollectionImpact, linesCollectionImpact));

        assertEquals("[{\"impactType\":\"COLLECTION\",\"elementType\":\"LINE\"}]", mapper.writeValueAsString(impactsSet));
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
