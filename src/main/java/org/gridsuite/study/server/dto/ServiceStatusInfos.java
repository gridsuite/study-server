/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.Builder;
import org.gridsuite.study.server.service.client.RemoteServiceName;

@Builder
public record ServiceStatusInfos(String name, ServiceStatus status) {
    public ServiceStatusInfos(RemoteServiceName name, ServiceStatus status) {
        this(name.serviceName(), status);
    }

    public enum ServiceStatus {
        DOWN, UP
    }
}
