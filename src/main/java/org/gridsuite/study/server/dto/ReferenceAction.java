/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

/**
 * What happened to the modification-references of a notification received from the reference.composite destination.
 *
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
public enum ReferenceAction {
    CREATE,
    UPDATE,
    DELETE,
}
