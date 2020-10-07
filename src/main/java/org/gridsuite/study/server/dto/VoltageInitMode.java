package org.gridsuite.study.server.dto;

public enum VoltageInitMode {
    UNIFORM_VALUES, // v=1pu, theta=0
    PREVIOUS_VALUES,
    DC_VALUES; // preprocessing to compute DC angles
}
