package com.powsybl.study.server;

public class StudyException  extends RuntimeException {

    StudyException(String msg) {
        super(msg);
    }

    StudyException(String message, Throwable cause) {
        super(message, cause);
    }
}
