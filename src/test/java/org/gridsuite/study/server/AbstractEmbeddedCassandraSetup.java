/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.github.nosan.embedded.cassandra.api.connection.ClusterCassandraConnection;
import com.github.nosan.embedded.cassandra.api.cql.CqlDataSet;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.gridsuite.study.test.EmbeddedCassandraFactoryConfig;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 *
 * This base class is absolutely necessary to properly separate the parent
 * context with the cassandra configuration and the child context when child contexts
 * use @MockBean
 */
@ContextHierarchy({
    @ContextConfiguration(classes = {EmbeddedCassandraFactoryConfig.class, CassandraConfig.class}),
    })
public abstract class AbstractEmbeddedCassandraSetup {

    @Autowired
    private ClusterCassandraConnection clusterCassandraConnection;

    @Before
    public void setup() {
        CqlDataSet.ofClasspaths("truncate.cql").forEachStatement(clusterCassandraConnection::execute);
    }
}
