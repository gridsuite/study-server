/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

/**
 * Wrapper in the form of an exception to hold the information that the result returned is partial.
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PartialResultException extends Exception {
    private final Serializable result;

    /**
     * @see Exception#Exception()
     */
    public PartialResultException(Serializable result) {
        super();
        this.result = result;
    }

    /**
     * @see Exception#Exception(String)
     */
    public PartialResultException(Serializable result, String message) {
        super(message);
        this.result = result;
    }

    /**
     * @see Exception#Exception(String, Throwable)
     */
    public PartialResultException(Serializable result, String message, Throwable cause) {
        super(message, cause);
        this.result = result;
    }

    /**
     * @see Exception#Exception(Throwable)
     */
    public PartialResultException(Serializable result, Throwable cause) {
        super(cause);
        this.result = result;
    }

    /**
     * @see Exception#Exception(String, Throwable, boolean, boolean)
     */
    public PartialResultException(Serializable result, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.result = result;
    }
}
