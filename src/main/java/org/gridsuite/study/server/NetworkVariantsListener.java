/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.NetworkListener;
import com.powsybl.iidm.network.VariantManagerConstants;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;

import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
public class NetworkVariantsListener implements NetworkListener {

    private final UUID networkUuid;

    private final EquipmentInfosService equipmentInfosService;

    protected NetworkVariantsListener(UUID networkUuid, EquipmentInfosService equipmentInfosService) {
        this.networkUuid = networkUuid;
        this.equipmentInfosService = equipmentInfosService;
    }

    @Override
    public void onCreation(Identifiable identifiable) {
        // Nothing to do in this listener
    }

    @Override
    public void beforeRemoval(Identifiable identifiable) {
        // Nothing to do in this listener
    }

    @Override
    public void afterRemoval(String s) {
        // Nothing to do in this listener
    }

    @Override
    public void onUpdate(Identifiable identifiable, String attribute, Object oldValue, Object newValue) {
        // Nothing to do in this listener
    }

    @Override
    public void onVariantCreated(String sourceVariantId, String targetVariantId) {
        // Initial variant modifications are not cloned
        if (!sourceVariantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID)) {
            equipmentInfosService.cloneVariantModifications(networkUuid, sourceVariantId, targetVariantId);
        }
    }

    @Override
    public void onVariantRemoved(String variantId) {
        equipmentInfosService.deleteVariants(networkUuid, List.of(variantId));
    }

}
