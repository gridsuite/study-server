/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.UncheckedIOException;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
public class NetworkInfos {
    public static final String NETWORK_ID = "NETWORK";

    private String id = NETWORK_ID;

    private double capacitorNoReclosingDelay;

    private double danglingLineCurrentLimitMaxTimeOperation;

    private double lineCurrentLimitMaxTimeOperation;

    private double loadTp;

    private double loadTq;

    private double loadAlpha;

    private double loadAlphaLong;

    private double loadBeta;

    private double loadBetaLong;

    private boolean loadIsControllable;

    private boolean loadIsRestorative;

    private double loadZPMax;

    private double loadZQMax;

    private double reactanceNoReclosingDelay;

    private double transformerCurrentLimitMaxTimeOperation;

    private double transformerT1StHT;

    private double transformerT1StTHT;

    private double transformerTNextHT;

    private double transformerTNextTHT;

    private double transformerTolV;

    public static NetworkInfos parseJson(String json, ObjectMapper objectMapper) {
        NetworkInfos network;
        try {
            network = objectMapper.readValue(json, NetworkInfos.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return network;
    }

    public static String toJson(NetworkInfos network, ObjectMapper objectMapper) {
        String json;
        try {
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(network);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return json;
    }
}
