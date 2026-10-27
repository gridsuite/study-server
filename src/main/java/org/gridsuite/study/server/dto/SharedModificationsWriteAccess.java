/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

/**
 * Whether a user can write on the shared modifications of a study.
 *
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
public enum SharedModificationsWriteAccess {
    NO_SHARED_MODIFICATION,
    ALL_WRITABLE,
    SOME_WRITABLE,
    NONE_WRITABLE
}
