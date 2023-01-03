/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.model.VariantInfos;
import org.gridsuite.study.server.NetworkVariantsListener;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyException.Type.NETWORK_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.STUDY_NOT_FOUND;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Service
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
public class NetworkService {
    private final NetworkStoreService networkStoreService;

    private final EquipmentInfosService equipmentInfosService;

    private final StudyRepository studyRepository;

    NetworkService(NetworkStoreService networkStoreService,
                   EquipmentInfosService equipmentInfosService,
                   StudyRepository studyRepository) {
        this.networkStoreService = networkStoreService;
        this.equipmentInfosService = equipmentInfosService;
        this.studyRepository = studyRepository;
    }

    public UUID getNetworkUuid(UUID studyUuid) {
        UUID networkUuid = doGetNetworkUuid(studyUuid);
        if (networkUuid == null) {
            throw new StudyException(STUDY_NOT_FOUND);
        }
        return networkUuid;
    }

    UUID doGetNetworkUuid(UUID studyUuid) {
        return studyRepository.findById(studyUuid).map(StudyEntity::getNetworkUuid).orElse(null);
    }

    void deleteNetwork(UUID networkUuid) {
        try {
            networkStoreService.deleteNetwork(networkUuid);
        } catch (PowsyblException e) {
            throw new StudyException(NETWORK_NOT_FOUND, networkUuid.toString());
        }
    }

    void deleteVariants(UUID networkUuid, List<String> variantsToRemove) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid);
            network.addListener(new NetworkVariantsListener(networkUuid, equipmentInfosService));
            VariantManager variantManager = network.getVariantManager();
            Collection<String> allVariants = variantManager.getVariantIds();
            variantsToRemove.forEach(v -> {
                if (allVariants.contains(v)) {
                    variantManager.removeVariant(v);
                }
            });
            networkStoreService.flush(network);
        } catch (PowsyblException e) {
            throw new StudyException(NETWORK_NOT_FOUND, networkUuid.toString());
        }
    }

    Network cloneNetwork(UUID sourceNetworkId, List<String> targetVariantIds) {
        return networkStoreService.cloneNetwork(sourceNetworkId, targetVariantIds);
    }

    UUID getNetworkUuid(Network network) {
        return networkStoreService.getNetworkUuid(network);
    }

    List<VariantInfos> getNetworkVariants(UUID networkUuid) {
        return networkStoreService.getVariantsInfos(networkUuid).stream().sorted(Comparator.comparing(VariantInfos::getNum)).collect(Collectors.toList());
    }
}
