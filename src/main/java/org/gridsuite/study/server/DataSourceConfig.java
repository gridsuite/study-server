/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@Configuration
@PropertySource(value = {"classpath:database.properties"})
@PropertySource(value = {"file:/config/database.properties"}, ignoreResourceNotFound = true)
public class DataSourceConfig {

    @Bean
    public DataSource getDataSource(Environment env) {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName(env.getRequiredProperty("driverClassName"));
        dataSourceBuilder.url(env.getRequiredProperty("url"));
        dataSourceBuilder.username(env.getRequiredProperty("username"));
        dataSourceBuilder.password(env.getRequiredProperty("password"));
        return dataSourceBuilder.build();
    }
}
