package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.CaseInfos;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CaseInfosTest {
    @Test
    public void test() {
        CaseInfos caseInfos = new CaseInfos("case", "XIIDM");
        assertEquals("XIIDM", caseInfos.getFormat());
        assertEquals("case", caseInfos.getName());

        CaseInfos caseInfos2 = new CaseInfos();
        assertNull(caseInfos2.getFormat());
        assertNull(caseInfos2.getName());
    }
}
