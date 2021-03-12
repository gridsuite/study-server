/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@Configuration
@PropertySource(value = {"classpath:database.properties"})
@PropertySource(value = {"file:/config/database.properties"}, ignoreResourceNotFound = true)
public class DataSourceConfig {
}
