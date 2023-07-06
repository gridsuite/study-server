package org.gridsuite.study.server.networkmodificationtree.dto;

import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.junit.jupiter.api.Test;

import static org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Florent MILLOT <florent.millot@rte-france.com>
 */
class BuildStatusTest {

    @Test
    void shouldConvertCorrectlyFromApplicationStatus() {
        assertEquals(BUILT_WITH_ERROR, from(NetworkModificationResult.ApplicationStatus.WITH_ERRORS));
        assertEquals(BUILT_WITH_WARNING, from(NetworkModificationResult.ApplicationStatus.WITH_WARNINGS));
        assertEquals(BUILT, from(NetworkModificationResult.ApplicationStatus.ALL_OK));
    }

    @Test
    void checkIsNotBuilt() {
        assertFalse(BUILT_WITH_ERROR.isNotBuilt());
        assertFalse(BUILT_WITH_WARNING.isNotBuilt());
        assertFalse(BUILT.isNotBuilt());
        assertFalse(BUILDING.isNotBuilt());
        assertTrue(NOT_BUILT.isNotBuilt());
    }

    @Test
    void checkIsBuilding() {
        assertFalse(BUILT_WITH_ERROR.isBuilding());
        assertFalse(BUILT_WITH_WARNING.isBuilding());
        assertFalse(BUILT.isBuilding());
        assertTrue(BUILDING.isBuilding());
        assertFalse(NOT_BUILT.isBuilding());
    }

    @Test
    void checkIsBuilt() {
        assertTrue(BUILT_WITH_ERROR.isBuilt());
        assertTrue(BUILT_WITH_WARNING.isBuilt());
        assertTrue(BUILT.isBuilt());
        assertFalse(BUILDING.isBuilt());
        assertFalse(NOT_BUILT.isBuilt());
    }

    @Test
    void checkMax() {
        assertEquals(BUILT_WITH_ERROR, BUILT_WITH_ERROR.max(BUILT_WITH_WARNING));
        assertEquals(BUILT_WITH_ERROR, BUILT_WITH_ERROR.max(BUILT));
        assertEquals(BUILT_WITH_WARNING, BUILT_WITH_WARNING.max(BUILT));
    }
}
