/*
  Copyright (c) 2020, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.gridsuite.study.server.dto.EquipmentInfos;
import org.springframework.lang.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A class to mock metadatas transfer in the DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class EquipmentInfosServiceMock implements EquipmentInfosService {

    @Override
    public void addAll(@NonNull final List<EquipmentInfos> equipmentInfos) {
        // Nothing to insert
    }

    @Override
    public EquipmentInfos add(@NonNull EquipmentInfos equipmentInfos) {
        return equipmentInfos;
    }

    @Override
    public void deleteAll(@NonNull UUID networkUuid) {
        // Nothing to delete
    }

    @Override
    public void deleteVariants(@NonNull UUID networkUuid, List<String> variantIds) {
        // Nothing to delete
    }

    @Override
    public Iterable<EquipmentInfos> findAll(@NonNull UUID networkUuid) {
        return Collections.emptyList();
    }

    @Override
    public void cloneVariantModifications(@NonNull UUID networkUuid, @NonNull String variantToCloneId, @NonNull String variantId) {
        // Do nothing
    }

    @Override
    public List<EquipmentInfos> search(@NonNull final String query) {
        return Collections.emptyList();
    }
}
