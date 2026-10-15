/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.config;

import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
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
 * @see DisableCloudStream
 */
@ToString(onlyExplicitlyIncluded = true) //create a beautiful toString() with only the class-name
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ExcludeSpringBootTestComponents implements AutoConfigurationImportFilter, EnvironmentAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExcludeSpringBootTestComponents.class);

    @Setter
    private Environment environment;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean[] match(final String[] autoConfigurationClasses, final AutoConfigurationMetadata autoConfigurationMetadata) {
        final Set<String> autoConfigDisable = new HashSet<>();
        if (environment.getProperty(DisableJpa.DISABLE_PROPERTY_NAME, Boolean.class, Boolean.FALSE)) {
            LOGGER.debug("Disabling SQL & JPA Autoconfiguration");
            autoConfigDisable.add("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration");
            autoConfigDisable.add("org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration");
            autoConfigDisable.add("org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration");
            autoConfigDisable.add("org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration");
            autoConfigDisable.add("org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration");
        }
        if (environment.getProperty(DisableElasticsearch.DISABLE_PROPERTY_NAME, Boolean.class, Boolean.FALSE)) {
            LOGGER.debug("Disabling ElasticSearch Autoconfiguration");
            autoConfigDisable.add("org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration");
            autoConfigDisable.add("org.springframework.boot.data.elasticsearch.autoconfigure.ElasticsearchDataAutoConfiguration");
            autoConfigDisable.add("org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration");
            autoConfigDisable.add("org.springframework.boot.data.elasticsearch.autoconfigure.ElasticsearchRepositoriesAutoConfiguration");
        }
        if (environment.getProperty(DisableCloudStream.DISABLE_PROPERTY_NAME, Boolean.class, Boolean.FALSE)) {
            LOGGER.debug("Disabling AMQP Autoconfiguration");
            autoConfigDisable.add("org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration");
            autoConfigDisable.add(FunctionConfiguration.class.getName());
            autoConfigDisable.add(BindingServiceConfiguration.class.getName());
        }
        LOGGER.trace("Classes not allowed for autoconfiguration: {}", autoConfigDisable);
        boolean[] passed = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            passed[i] = !autoConfigDisable.contains(autoConfigurationClasses[i]);
        }
        LOGGER.trace("Result filtering classes {} → {}", autoConfigurationClasses, passed);
        return passed;
    }
}
