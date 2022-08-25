package org.gridsuite.study.server.utils;

import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.springframework.cloud.stream.binder.test.OutputDestination;

import com.powsybl.commons.exceptions.UncheckedInterruptedException;

import okhttp3.mockwebserver.MockWebServer;

public final class TestUtils {

    private static final long TIMEOUT = 1000;

    private TestUtils() {

    }

    public static Set<String> getRequestsDone(int n, MockWebServer server) throws UncheckedInterruptedException {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                return server.takeRequest(TIMEOUT, TimeUnit.MILLISECONDS).getPath();
            } catch (InterruptedException e) {
                throw new UncheckedInterruptedException(e);
            }
        }).collect(Collectors.toSet());
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, UUID caseUuid, String loadflowProvider, LoadFlowParametersEntity loadFlowParametersEntity) {
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat("").caseUuid(caseUuid)
            .date(LocalDateTime.now())
            .networkId("netId")
            .networkUuid(networkUuid)
            .userId("userId")
            .loadFlowProvider(loadflowProvider)
            .loadFlowParameters(loadFlowParametersEntity)
            .build();
    }

    public static void assertQueuesEmpty(List<String> destinations, OutputDestination output) {
        try {
            destinations.forEach(destination -> {
                assertNull("Should not be any messages in queue " + destination + " : ", output.receive(TIMEOUT, destination));
            });
        } catch (NullPointerException e) {
            // Ignoring
        } finally {
            output.clear(); // purge in order to not fail the other tests
        }
    }

    public static void assertServerRequestsEmpty(MockWebServer server) throws UncheckedInterruptedException, IOException {
        Set<String> httpRequest = null;

        try {
            httpRequest = TestUtils.getRequestsDone(1, server);
        } catch (NullPointerException e) {
            // ignoring
        } finally {
            server.shutdown();
        }

        assertNull("Should not be any http requests : ", httpRequest);
    }
}
