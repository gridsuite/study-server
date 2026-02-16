/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessException;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;

import java.util.Map;
import java.util.Objects;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
public class StudyException extends AbstractBusinessException {

    private final StudyBusinessErrorCode errorCode;
    private final transient Map<String, Object> businessErrorValues;

    public StudyException(StudyBusinessErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode.name()));
        this.errorCode = errorCode;
        this.businessErrorValues = Map.of();
    }

    public StudyException(StudyBusinessErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.businessErrorValues = Map.of();
    }

    public StudyException(StudyBusinessErrorCode errorCode, String message, Map<String, Object> businessErrorValues) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.businessErrorValues = businessErrorValues != null ? Map.copyOf(businessErrorValues) : Map.of();
    }

    @Override
    public @NonNull StudyBusinessErrorCode getBusinessErrorCode() {
        return errorCode;
    }

    @NotNull
    @Override
    public Map<String, Object> getBusinessErrorValues() {
        return businessErrorValues;
    }
}
