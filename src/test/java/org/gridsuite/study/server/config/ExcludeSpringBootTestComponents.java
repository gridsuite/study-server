/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.config;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.stream.config.BindingServiceConfiguration;
import org.springframework.cloud.stream.function.FunctionConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.HashSet;
import java.util.Set;

/**
 * This classe is to permit with {@code @Disable*} annotations to exclude some components from auto-configuration
 * when using {@link org.springframework.boot.test.context.SpringBootTest @SpringBootTest}.<br/>
 * It works the same way as {@link EnableAutoConfiguration#exclude()} but is usable by multiple annotations.
 *
 * @see org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch
 * @see DisableJpa
 * @see DisableAmqp
 */
@NoArgsConstructor(onConstructor_ = { @Autowired })
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Slf4j
public class ExcludeSpringBootTestComponents implements AutoConfigurationImportFilter, EnvironmentAware {
    @Setter(onMethod_ = {@Override})
    private Environment environment;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean[] match(final String[] autoConfigurationClasses, final AutoConfigurationMetadata autoConfigurationMetadata) {
        final Set<String> autoConfigDisable = new HashSet<>();
        if (!environment.getProperty(DisableJpa.PROPERTY_NAME, Boolean.class, Boolean.TRUE)) {
            log.debug("Disabling SQL & JPA Autoconfiguration");
            autoConfigDisable.add(DataSourceAutoConfiguration.class.getName());
            autoConfigDisable.add(JpaRepositoriesAutoConfiguration.class.getName());
            autoConfigDisable.add(HibernateJpaAutoConfiguration.class.getName());
            autoConfigDisable.add(LiquibaseAutoConfiguration.class.getName());
        }
        if (!environment.getProperty(DisableElasticsearch.PROPERTY_NAME, Boolean.class, Boolean.TRUE)) {
            log.debug("Disabling ElasticSearch Autoconfiguration");
            autoConfigDisable.add(ElasticsearchClientAutoConfiguration.class.getName());
            autoConfigDisable.add(ElasticsearchDataAutoConfiguration.class.getName());
            autoConfigDisable.add(ElasticsearchRestClientAutoConfiguration.class.getName());
            autoConfigDisable.add(ElasticsearchRepositoriesAutoConfiguration.class.getName());
        }
        if (!environment.getProperty(DisableAmqp.PROPERTY_NAME, Boolean.class, Boolean.TRUE)) {
            log.debug("Disabling AMQP Autoconfiguration");
            autoConfigDisable.add(RabbitAutoConfiguration.class.getName());
            autoConfigDisable.add(FunctionConfiguration.class.getName());
            autoConfigDisable.add(BindingServiceConfiguration.class.getName());
        }
        log.trace("Classes not allowed for autoconfiguration: {}", autoConfigDisable);
        boolean[] passed = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            passed[i] = !autoConfigDisable.contains(autoConfigurationClasses[i]);
        }
        log.trace("Result filtering classes {} → {}", autoConfigurationClasses, passed);
        return passed;
    }
}
