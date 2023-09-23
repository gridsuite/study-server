/*
  Copyright (c) 2023, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.utils.audit.AuditorAwareImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Configuration
@EnableJpaRepositories
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JPAConfig {
    @Bean
    public AuditorAware<String> auditorAware() {
        return new AuditorAwareImpl();
    }
}
