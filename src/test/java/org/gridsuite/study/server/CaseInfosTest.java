package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.CaseInfos;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class CaseInfosTest {

    @Test
    public void test() {
        UUID uuid = UUID.randomUUID();
        CaseInfos caseInfos = new CaseInfos("case", "XIIDM", uuid);
        assertEquals("XIIDM", caseInfos.getFormat());
        assertEquals("case", caseInfos.getName());
        assertEquals(uuid, caseInfos.getUuid());

        CaseInfos caseInfos2 = new CaseInfos();
        assertNull(caseInfos2.getFormat());
        assertNull(caseInfos2.getName());
        assertNull(caseInfos2.getUuid());
    }
}
