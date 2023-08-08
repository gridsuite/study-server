/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Component
@ConfigurationProperties(prefix = "gridsuite")
@Data
public class RemoteServicesProperties {

    private List<Service> services;

    @Data
    public static class Service {
        private String name;
        private String baseUri;
        private Boolean optional = false;
    }

    public String getServiceUri(String serviceName) {
        String defaultUri = "http://" + serviceName + "/";
        return Objects.isNull(services) ? defaultUri : services.stream()
                .filter(s -> s.getName().equalsIgnoreCase(serviceName))
                .map(RemoteServicesProperties.Service::getBaseUri)
                .findFirst()
                .orElse(defaultUri);
    }

    public void setServiceUri(String serviceName, String newUri) {
        if (!Objects.isNull(services)) {
            services.stream()
                .filter(s -> s.getName().equalsIgnoreCase(serviceName))
                .findFirst()
                .ifPresent(s -> s.setBaseUri(newUri));
        }
    }
}
