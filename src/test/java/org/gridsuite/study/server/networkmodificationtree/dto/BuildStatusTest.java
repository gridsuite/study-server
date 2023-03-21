package org.gridsuite.study.server.networkmodificationtree.dto;

import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildStatusTest {

    @Test
    void shouldConvertCorrectlyFromApplicationStatus() {
        assertEquals(BUILT_WITH_ERROR, fromApplicationStatus(NetworkModificationResult.ApplicationStatus.WITH_ERRORS));
        assertEquals(BUILT_WITH_WARNING, fromApplicationStatus(NetworkModificationResult.ApplicationStatus.WITH_WARNINGS));
        assertEquals(BUILT, fromApplicationStatus(NetworkModificationResult.ApplicationStatus.ALL_OK));
    }

    @Test
    void checkIsBuilt() {
        assertTrue(BUILT_WITH_ERROR.isBuilt());
        assertTrue(BUILT_WITH_WARNING.isBuilt());
        assertTrue(BUILT.isBuilt());
        assertFalse(BUILDING.isBuilt());
        assertFalse(NOT_BUILT.isBuilt());
    }

    // If you change the values, then you will have to edit this test and be careful about the order of the enum as said in the BuildStatus class
    @Test
    void checkEnumValues() {
        assertArrayEquals(values(), List.of(NOT_BUILT, BUILDING, BUILT, BUILT_WITH_WARNING, BUILT_WITH_ERROR).toArray());
    }
}
