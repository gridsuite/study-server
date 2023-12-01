/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Validated
@Component
@ConfigurationProperties(prefix = "gridsuite")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RemoteServicesProperties {
    private WebServerApplicationContext webSrvAppCtxt;
    private List<Service> services;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class Service {
        @NotBlank private String name;
        @NotBlank private String baseUri;
        private boolean optional = false;
    }

    public String getLocalUri() {
        return "http://localhost:" + this.webSrvAppCtxt.getWebServer().getPort();
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
