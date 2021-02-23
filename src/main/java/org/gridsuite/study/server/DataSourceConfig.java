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
    // TODO, make this the default value and allow override via properties file
    private static final String DATABASE_NAME = "study";

    // TODO find a better way to pass default database name to the url
    @Bean
    public DataSource getDataSource(Environment env) {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName(env.getRequiredProperty("driverClassName"));
        dataSourceBuilder.url(env.getRequiredProperty("url") + DATABASE_NAME + env.getRequiredProperty("urlOptions"));
        dataSourceBuilder.username(env.getRequiredProperty("login"));
        dataSourceBuilder.password(env.getRequiredProperty("password"));
        return dataSourceBuilder.build();
    }
}
