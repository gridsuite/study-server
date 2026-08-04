/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.gridsuite.study.server.error.StudyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NODE_ACTIVITY_CONFLICT;
import static org.gridsuite.study.server.nodeactivity.NodeActivityService.assertNoConflict;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.BUILD;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.DELETE_NODES;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_EVENTS;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_MODIFICATIONS;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_PARAMETERS;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_TREE;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.LOADFLOW_ON_SECURITY_NODE;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.REIMPORT_CASE;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.UNBUILD;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.UNBUILD_ALL;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.UNBUILD_CHILDREN;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
class NodeActivityRulesTest {

    private static final UUID STUDY = UUID.randomUUID();
    private static final UUID ROOT_NETWORK = UUID.randomUUID();
    private static final UUID OTHER_ROOT_NETWORK = UUID.randomUUID();

    private static final UUID ROOT = UUID.randomUUID();
    private static final UUID NODE = UUID.randomUUID();
    private static final UUID CHILD = UUID.randomUUID();
    private static final UUID GRANDCHILD = UUID.randomUUID();
    private static final UUID SIBLING = UUID.randomUUID();

    /**
    *   ROOT
    *    |- NODE - CHILD - GRANDCHILD
    *    |- SIBLING
    */
    private static final Map<UUID, Set<UUID>> PARENTS = Map.of(
        ROOT, Set.of(),
        NODE, Set.of(ROOT),
        CHILD, Set.of(ROOT, NODE),
        GRANDCHILD, Set.of(ROOT, NODE, CHILD),
        SIBLING, Set.of(ROOT));

    private static final List<UUID> EVERY_NODE = List.of(ROOT, NODE, CHILD, GRANDCHILD, SIBLING);

    private static boolean refused(NodeActivityType runningType, UUID runningNode,
                                   NodeActivityType requestedType, UUID requestedNode) {
        return refused(runningType, runningNode, ROOT_NETWORK, requestedType, requestedNode, ROOT_NETWORK);
    }

    private static boolean refused(NodeActivityType runningType, UUID runningNode, UUID runningRootNetwork,
                                   NodeActivityType requestedType, UUID requestedNode, UUID requestedRootNetwork) {
        List<NodeActivityEntity> runningActivities =
            List.of(NodeActivityEntity.from(runningType, STUDY, runningRootNetwork, runningNode));
        List<UUID> requestedNodes = List.of(requestedNode);
        try {
            assertNoConflict(runningActivities, requestedType, requestedRootNetwork, requestedNodes, PARENTS);
            return false;
        } catch (StudyException e) {
            assertThat(e.getBusinessErrorCode()).isEqualTo(NODE_ACTIVITY_CONFLICT);
            return true;
        }
    }

    static Stream<Arguments> everyPairOfTypes() {
        return Arrays.stream(NodeActivityType.values())
            .flatMap(runningType -> Arrays.stream(NodeActivityType.values())
                .map(requestedType -> Arguments.of(runningType, requestedType)));
    }

    @ParameterizedTest(name = "{0} running, {1} requested")
    @MethodSource("everyPairOfTypes")
    void anActivityOnTheSameNodeAlwaysConflicts(NodeActivityType runningType, NodeActivityType requestedType) {
        assertThat(refused(runningType, NODE, requestedType, NODE)).isTrue();
    }

    @ParameterizedTest(name = "{0} running, {1} requested")
    @MethodSource("everyPairOfTypes")
    void unrelatedNodesNeverConflict(NodeActivityType runningType, NodeActivityType requestedType) {
        assertThat(refused(runningType, NODE, requestedType, SIBLING)).isFalse();
    }

    @ParameterizedTest(name = "{0} on the parent, {1} on a child")
    @MethodSource("everyPairOfTypes")
    void aParentConflictsWithItsChildrenOnlyWhenItInvalidatesThem(NodeActivityType onParent, NodeActivityType onChild) {
        assertThat(refused(onParent, NODE, onChild, GRANDCHILD))
            .as("%s running on the parent, %s requested on a child", onParent, onChild)
            .isEqualTo(onParent.isInvalidatesChildren());

        assertThat(refused(onChild, GRANDCHILD, onParent, NODE))
            .as("%s running on a child, %s requested on the parent", onChild, onParent)
            .isEqualTo(onParent.isInvalidatesChildren());
    }

    @ParameterizedTest(name = "{0} against {1}")
    @MethodSource("everyPairOfTypes")
    void theVerdictIsTheSameWhicheverStartedFirst(NodeActivityType oneType, NodeActivityType otherType) {
        for (UUID oneNode : EVERY_NODE) {
            for (UUID otherNode : EVERY_NODE) {
                assertThat(refused(oneType, oneNode, otherType, otherNode))
                    .as("%s on %s, then %s on %s", oneType, oneNode, otherType, otherNode)
                    .isEqualTo(refused(otherType, otherNode, oneType, oneNode));
            }
        }
    }

    @Test
    void theRootNodeIsAParentOfEverything() {
        assertThat(refused(REIMPORT_CASE, ROOT, BUILD, GRANDCHILD)).isTrue();
        assertThat(refused(REIMPORT_CASE, ROOT, COMPUTE, SIBLING)).isTrue();
    }

    @Test
    void unbuildingChildrenRefusesABuildOnAChild() {
        assertThat(refused(UNBUILD_CHILDREN, NODE, BUILD, CHILD)).isTrue();
        assertThat(refused(BUILD, CHILD, UNBUILD_CHILDREN, NODE)).isTrue();
    }

    @Test
    void aNodeAndItsParentMayBuildInParallel() {
        assertThat(refused(BUILD, NODE, BUILD, CHILD)).isFalse();
        assertThat(refused(UNBUILD, NODE, BUILD, CHILD)).isFalse();
    }

    @Test
    void onlyALoadflowOnASecurityNodeInvalidatesChildren() {
        assertThat(refused(LOADFLOW_ON_SECURITY_NODE, NODE, BUILD, CHILD)).isTrue();
        assertThat(refused(COMPUTE, NODE, BUILD, CHILD)).isFalse();
    }

    @Test
    void activitiesInDifferentRootNetworksNeverConflict() {
        assertThat(refused(BUILD, NODE, ROOT_NETWORK, BUILD, NODE, OTHER_ROOT_NETWORK)).isFalse();
        assertThat(refused(UNBUILD_CHILDREN, NODE, ROOT_NETWORK, BUILD, CHILD, OTHER_ROOT_NETWORK)).isFalse();
    }

    @Test
    void anActivityOnSharedDataConflictsInEveryRootNetwork() {
        assertThat(refused(EDIT_TREE, NODE, null, BUILD, CHILD, ROOT_NETWORK)).isTrue();
        assertThat(refused(EDIT_TREE, NODE, null, BUILD, CHILD, OTHER_ROOT_NETWORK)).isTrue();
        assertThat(refused(BUILD, CHILD, OTHER_ROOT_NETWORK, EDIT_TREE, NODE, null)).isTrue();
    }

    @Test
    void theActivityFamiliesTheFrontendAssumesStillHold() {
        assertThat(List.of(BUILD, UNBUILD, COMPUTE, EDIT_EVENTS))
            .noneMatch(NodeActivityType::isInvalidatesChildren);
        assertThat(List.of(UNBUILD_CHILDREN, UNBUILD_ALL, LOADFLOW_ON_SECURITY_NODE, REIMPORT_CASE,
                EDIT_TREE, EDIT_MODIFICATIONS, EDIT_PARAMETERS, DELETE_NODES))
            .allMatch(NodeActivityType::isInvalidatesChildren);
        assertThat(NodeActivityType.values()).hasSize(12);
    }

    @Test
    void nothingIsRefusedWhenNoActivityIsRunning() {
        List<NodeActivityEntity> nothingRunning = List.of();
        assertThatCode(() -> assertNoConflict(nothingRunning, EDIT_TREE, null, EVERY_NODE, PARENTS))
            .doesNotThrowAnyException();
    }

    @Test
    void theRefusalSaysWhatIsHoldingTheNode() {
        List<NodeActivityEntity> running =
            List.of(NodeActivityEntity.from(UNBUILD_CHILDREN, STUDY, ROOT_NETWORK, NODE));
        List<UUID> requestedNodes = List.of(GRANDCHILD);
        assertThatThrownBy(() -> assertNoConflict(running, BUILD, ROOT_NETWORK, requestedNodes, PARENTS))
            .isInstanceOf(StudyException.class)
            .hasMessageContaining("BUILD on node " + GRANDCHILD)
            .hasMessageContaining("UNBUILD_CHILDREN is running on node " + NODE);
    }
}
