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

import static org.gridsuite.study.server.StudyException.Type.DRIVER_NOT_SUPPORTED;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@Configuration
@PropertySource(value = {"classpath:database.properties"})
@PropertySource(value = {"file:/config/database.properties"}, ignoreResourceNotFound = true)
public class DataSourceConfig {
    private static final String DATABASE_NAME = "study";

    @Bean
    public DataSource getDataSource(Environment env) {

        String driver = env.getRequiredProperty("driver");
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();

        if (driver.equals("h2")) {
            dataSourceBuilder.url(env.getRequiredProperty("url"));
        } else if (driver.equals("postgresql")) {
            dataSourceBuilder.url("jdbc:postgresql://" +
                    env.getRequiredProperty("host") + ":" +
                    env.getRequiredProperty("port") + "/" +
                    DATABASE_NAME);
            dataSourceBuilder.username(env.getRequiredProperty("login"));
            dataSourceBuilder.password(env.getRequiredProperty("password"));
        } else {
            throw new StudyException(DRIVER_NOT_SUPPORTED);
        }

        return dataSourceBuilder.build();
    }
}
