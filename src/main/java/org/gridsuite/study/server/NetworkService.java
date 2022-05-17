/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

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
                   @Lazy EquipmentInfosService equipmentInfosService,
                   StudyRepository studyRepository) {
        this.networkStoreService = networkStoreService;
        this.equipmentInfosService = equipmentInfosService;
        this.studyRepository = studyRepository;
    }

    Mono<UUID> getNetworkUuid(UUID studyUuid) {
        return Mono.fromCallable(() -> doGetNetworkUuid(studyUuid).orElse(null))
                .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)));
    }

    Optional<UUID> doGetNetworkUuid(UUID studyUuid) {
        return studyRepository.findById(studyUuid).map(StudyEntity::getNetworkUuid);
    }

    Mono<Void> deleteNetwork(UUID networkUuid) {
        try {
            networkStoreService.deleteNetwork(networkUuid);
        } catch (PowsyblException e) {
            throw new StudyException(NETWORK_NOT_FOUND, networkUuid.toString());
        }
        return Mono.empty();
    }

    Mono<Void> deleteVariants(UUID networkUuid, List<String> variantsToRemove) {
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
        return Mono.empty();
    }

    HashSet<String> doGetVariantsIds(UUID networkUuid) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid);
            VariantManager variantManager = network.getVariantManager();
            Collection<String> allVariants = variantManager.getVariantIds();
            return (HashSet<String>) allVariants;

        } catch (PowsyblException e) {
            throw new StudyException(NETWORK_NOT_FOUND, networkUuid.toString());
        }
    }

    Mono<Void> createNetwork(UUID networkId, UUID parentNetworkId, int targetVariantNum) {
        networkStoreService.createNetwork(networkId, parentNetworkId, targetVariantNum);
        return Mono.empty();
    }
}
