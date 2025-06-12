/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.dto;

import java.util.Set;
import java.util.UUID;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
public record ExcludedNetworkModificationsByRootNetwork(UUID rootNetworkUUID, Set<UUID> modificationUuidsToExclude) { }
