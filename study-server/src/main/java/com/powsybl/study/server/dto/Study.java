/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.study.server.dto;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */


@Table
public class Study implements Serializable {

    @PrimaryKey("studyName")
    private String name;

    @Column("networkUuid")
    private UUID networkUuid;

    @Column("networkId")
    private String networkId;

    @Column("caseName")
    private String networkCase;

    @Column("description")
    private String description;

    public Study(String name, UUID networkUuid, String networkId, String networkCase, String description) {
        this.name = name;
        this.networkUuid = networkUuid;
        this.networkId = networkId;
        this.networkCase = networkCase;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getNetworkCase() {
        return networkCase;
    }

    public void setNetworkCase(String networkCase) {
        this.networkCase = networkCase;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
