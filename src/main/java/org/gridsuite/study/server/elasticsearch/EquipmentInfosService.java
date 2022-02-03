/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.dto.TombstonedEquipmentInfos;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * An interface to define an api for metadatas transfer in the DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Service
public interface EquipmentInfosService {
    enum FieldSelector {
        NAME, ID
    }

    EquipmentInfos addEquipmentInfos(@NonNull EquipmentInfos equipmentInfos); // Just for unit tests

    TombstonedEquipmentInfos addTombstonedEquipmentInfos(@NonNull TombstonedEquipmentInfos tombstonedEquipmentInfos); // Just for unit tests

    void addAllEquipmentInfos(@NonNull final List<EquipmentInfos> equipmentsInfos);

    void addAllTombstonedEquipmentInfos(@NonNull final List<TombstonedEquipmentInfos> tombstonedEquipmentsInfos);

    List<EquipmentInfos> findAllEquipmentInfos(@NonNull UUID networkUuid); // Just for unit tests

    List<TombstonedEquipmentInfos> findAllTombstonedEquipmentInfos(@NonNull UUID networkUuid); // Just for unit tests

    void deleteVariants(@NonNull UUID networkUuid, List<String> variantIds);

    void cloneVariantModifications(@NonNull UUID networkUuid, @NonNull String variantToCloneId, @NonNull String variantId);

    void deleteAll(@NonNull UUID networkUuid);

    List<EquipmentInfos> searchEquipments(@NonNull final String query);

    List<TombstonedEquipmentInfos> searchTombstonedEquipments(@NonNull final String query);
}
