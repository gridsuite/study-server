/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.study.server.dto;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

public class NetworkIds implements Serializable {

    private UUID networkUuid;
    private String networkId;

    //needed for jackson deserialization
    public NetworkIds() {
        super();
    }

    public NetworkIds(UUID networkUuid, String networkId) {
        this.networkUuid = networkUuid;
        this.networkId = networkId;
    }

    public UUID getNetworkUuid() {
        return networkUuid;
    }

    public void setNetworkUuid(UUID networkUuid) {
        this.networkUuid = networkUuid;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }
}
