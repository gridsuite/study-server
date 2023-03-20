/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockWebServer;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public abstract class AbstractRestClientTest {

    protected MockWebServer server;

    public final Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    protected abstract Dispatcher getDispatcher();

    protected String initMockWebServer() throws RuntimeException {
        server = new MockWebServer();
        try {
            server.start();
            getLogger().info("Mock server started at port = " + server.getPort());
        } catch (IOException e) {
            throw new RuntimeException("Can not init the mock server " + this.getClass().getSimpleName(), e);
        }

        // setup dispatcher
        Dispatcher dispatcher = getDispatcher();
        // attach dispatcher
        server.setDispatcher(dispatcher);

        // get base URL
        HttpUrl baseUrl = server.url("");
        return baseUrl.toString();
    }

    @Before
    public void setup() {

    }

    @After
    public void tearDown() {
        try {
            server.shutdown();
        } catch (Exception e) {
            getLogger().info("Can not shutdown the mock server " + this.getClass().getSimpleName());
        }
    }
}
