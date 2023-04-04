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

    @Test
    void checkMax() {
        assertEquals(BUILT_WITH_ERROR, BUILT_WITH_ERROR.max(BUILT_WITH_WARNING));
        assertEquals(BUILT_WITH_ERROR, BUILT_WITH_ERROR.max(BUILT));
        assertEquals(BUILT_WITH_WARNING, BUILT_WITH_WARNING.max(BUILT));
    }
}
