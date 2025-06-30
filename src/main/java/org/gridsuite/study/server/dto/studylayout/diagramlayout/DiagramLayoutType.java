package org.gridsuite.study.server.dto.studylayout.diagramlayout;

import lombok.Getter;

@Getter
public enum DiagramLayoutType {
    NETWORK_AREA("network-area"),
    SUBSTATION("substation"),
    VOLTAGE_LEVEL("voltage-level");

    private final String label;

    DiagramLayoutType(String label) {
        this.label = label;
    }

}
