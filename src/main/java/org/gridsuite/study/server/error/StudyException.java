/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessException;
import com.powsybl.ws.commons.error.BusinessErrorCode;
import lombok.NonNull;

import java.util.Objects;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
public class StudyException extends AbstractBusinessException {

    private final StudyBusinessErrorCode errorCode;

    public StudyException(StudyBusinessErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode.name()));
        this.errorCode = errorCode;
    }

    public StudyException(StudyBusinessErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    @Override
    public @NonNull BusinessErrorCode getBusinessErrorCode() {
        return errorCode;
    }
}
