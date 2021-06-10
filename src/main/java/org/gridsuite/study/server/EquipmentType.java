package org.gridsuite.study.server;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EquipmentType {
    TWO_WINDINGS_TRANSFORMERS("2-windings-transformers"),
    THREE_WINDINGS_TRANSFORMERS("3-windings-transformers");

    EquipmentType(String hop) {
        this.serverPath = hop;
    }

    public static EquipmentType fromString(String str) {
        for (EquipmentType eq : EquipmentType.values()) {
            if (eq.serverPath.equals(str)) {
                return eq;
            }
        }
        return null;
    }

    final String serverPath;

    @JsonValue
    public String getServerPath() {
        return serverPath;
    }
}
