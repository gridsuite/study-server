package org.gridsuite.study.server.service.shortcircuit;

public enum ShortcircuitAnalysisType {
    Global("global"),
    Selective("selective");

    private final String name;

    ShortcircuitAnalysisType(String s) {
        name = s;
    }
}
