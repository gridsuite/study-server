/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

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
import static org.gridsuite.study.server.nodeactivity.NodeActivityRules.findActivityConflict;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.BUILD;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE_AND_UNBUILD_CHILDREN;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.DELETE_NODES;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_EVENTS;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_MODIFICATIONS;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_TREE;
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
    private static final Map<UUID, Set<UUID>> ANCESTORS = Map.of(
        ROOT, Set.of(),
        NODE, Set.of(ROOT),
        CHILD, Set.of(ROOT, NODE),
        GRANDCHILD, Set.of(ROOT, NODE, CHILD),
        SIBLING, Set.of(ROOT));

    private static final List<UUID> EVERY_NODE = List.of(ROOT, NODE, CHILD, GRANDCHILD, SIBLING);

    static Stream<Arguments> everyPairOfTypes() {
        return Arrays.stream(NodeActivityType.values())
            .flatMap(runningType -> Arrays.stream(NodeActivityType.values())
                .map(requestedType -> Arguments.of(runningType, requestedType)));
    }

    private static UUID rootNetworkOf(NodeActivityType type, UUID rootNetworkUuid) {
        return type.affectsAllRootNetworks() ? null : rootNetworkUuid;
    }

    private static boolean refused(NodeActivityType runningType, UUID runningNode,
                                   NodeActivityType requestedType, UUID requestedNode) {
        return refused(runningType, runningNode, rootNetworkOf(runningType, ROOT_NETWORK),
                       requestedType, requestedNode, rootNetworkOf(requestedType, ROOT_NETWORK));
    }

    private static boolean refused(NodeActivityType runningType, UUID runningNode, UUID runningRootNetwork,
                                   NodeActivityType requestedType, UUID requestedNode, UUID requestedRootNetwork) {
        List<NodeActivityEntity> running =
            List.of(NodeActivityEntity.from(runningType, STUDY, runningRootNetwork, runningNode));
        return findActivityConflict(running,
            NodeActivityEntity.from(requestedType, STUDY, requestedRootNetwork, requestedNode), ANCESTORS).isPresent();
    }

    @Test
    void everyTypeSaysWhetherItInvalidatesChildren() {
        List<NodeActivityType> touchOnlyTheirNode = List.of(BUILD, UNBUILD, COMPUTE, EDIT_EVENTS);
        List<NodeActivityType> invalidateTheirChildren = List.of(UNBUILD_CHILDREN, UNBUILD_ALL,
            COMPUTE_AND_UNBUILD_CHILDREN, REIMPORT_CASE, EDIT_TREE, EDIT_MODIFICATIONS, DELETE_NODES);

        assertThat(touchOnlyTheirNode).noneMatch(NodeActivityType::invalidatesChildren);
        assertThat(invalidateTheirChildren).allMatch(NodeActivityType::invalidatesChildren);
        assertThat(Stream.concat(touchOnlyTheirNode.stream(), invalidateTheirChildren.stream()))
            .as("a new type has to be classified here, not only added to the enum")
            .containsExactlyInAnyOrder(NodeActivityType.values());
    }

    @Test
    void everyTypeSaysWhetherItAffectsAllRootNetworks() {
        List<NodeActivityType> oneRootNetwork = List.of(BUILD, UNBUILD, UNBUILD_CHILDREN, COMPUTE,
            COMPUTE_AND_UNBUILD_CHILDREN, REIMPORT_CASE);
        List<NodeActivityType> allRootNetworks = List.of(UNBUILD_ALL, EDIT_TREE, EDIT_MODIFICATIONS,
            DELETE_NODES, EDIT_EVENTS);

        assertThat(oneRootNetwork).noneMatch(NodeActivityType::affectsAllRootNetworks);
        assertThat(allRootNetworks).allMatch(NodeActivityType::affectsAllRootNetworks);
        assertThat(Stream.concat(oneRootNetwork.stream(), allRootNetworks.stream()))
            .as("a new type has to be classified here, not only added to the enum")
            .containsExactlyInAnyOrder(NodeActivityType.values());
    }

    @Test
    void theTypesRemovedByAResultMessage() {
        assertThat(Arrays.stream(NodeActivityType.values()).filter(a -> !a.isSynchronous()))
            .as("each of these needs a removeNodeActivity in ConsumerService")
            .containsExactlyInAnyOrder(BUILD, COMPUTE, COMPUTE_AND_UNBUILD_CHILDREN, REIMPORT_CASE);
    }

    @ParameterizedTest(name = "{0} running, {1} requested")
    @MethodSource("everyPairOfTypes")
    void anActivityOnTheSameNodeAlwaysConflicts(NodeActivityType runningType, NodeActivityType requestedType) {
        assertThat(refused(runningType, NODE, requestedType, NODE)).isTrue();
    }

    @ParameterizedTest(name = "{0} running, {1} requested")
    @MethodSource("everyPairOfTypes")
    void activitiesOnUnrelatedNodesNeverConflict(NodeActivityType runningType, NodeActivityType requestedType) {
        assertThat(refused(runningType, NODE, requestedType, SIBLING)).isFalse();
    }

    @ParameterizedTest(name = "{0} on an ancestor, {1} on a descendant")
    @MethodSource("everyPairOfTypes")
    void anAncestorConflictsWithADescendantOnlyWhenItInvalidatesChildren(NodeActivityType onAncestor,
                                                                        NodeActivityType onDescendant) {
        assertThat(refused(onAncestor, NODE, onDescendant, GRANDCHILD))
            .as("%s running on an ancestor, %s requested on a descendant", onAncestor, onDescendant)
            .isEqualTo(onAncestor.invalidatesChildren());

        assertThat(refused(onDescendant, GRANDCHILD, onAncestor, NODE))
            .as("%s running on a descendant, %s requested on an ancestor", onDescendant, onAncestor)
            .isEqualTo(onAncestor.invalidatesChildren());
    }

    @ParameterizedTest(name = "{0} in one root network, {1} in another")
    @MethodSource("everyPairOfTypes")
    void activitiesInDifferentRootNetworksConflictOnlyWhenOneAffectsAllRootNetworks(NodeActivityType runningType,
                                                                                    NodeActivityType requestedType) {
        assertThat(refused(runningType, NODE, rootNetworkOf(runningType, ROOT_NETWORK),
                           requestedType, NODE, rootNetworkOf(requestedType, OTHER_ROOT_NETWORK)))
            .as("%s in one root network, %s in another", runningType, requestedType)
            .isEqualTo(runningType.affectsAllRootNetworks() || requestedType.affectsAllRootNetworks());
    }

    @ParameterizedTest(name = "{0} against {1}")
    @MethodSource("everyPairOfTypes")
    void theConflictIsTheSameWhicheverStartedFirst(NodeActivityType oneType, NodeActivityType otherType) {
        for (UUID oneNode : EVERY_NODE) {
            for (UUID otherNode : EVERY_NODE) {
                assertThat(refused(oneType, oneNode, otherType, otherNode))
                    .as("%s on %s, then %s on %s", oneType, oneNode, otherType, otherNode)
                    .isEqualTo(refused(otherType, otherNode, oneType, oneNode));
            }
        }
    }

    @Test
    void theRootNodeIsAnAncestorOfEverything() {
        assertThat(refused(REIMPORT_CASE, ROOT, BUILD, GRANDCHILD)).isTrue();
        assertThat(refused(REIMPORT_CASE, ROOT, COMPUTE, SIBLING)).isTrue();
    }

    @Test
    void unbuildingChildrenRefusesABuildOnAChild() {
        assertThat(refused(UNBUILD_CHILDREN, NODE, BUILD, CHILD)).isTrue();
        assertThat(refused(BUILD, CHILD, UNBUILD_CHILDREN, NODE)).isTrue();
    }

    @Test
    void aNodeAndItsAncestorMayBuildInParallel() {
        assertThat(refused(BUILD, NODE, BUILD, CHILD)).isFalse();
        assertThat(refused(UNBUILD, NODE, BUILD, CHILD)).isFalse();
    }

    @Test
    void aComputeReachesChildrenOnlyWhenItAlsoUnbuildsThem() {
        assertThat(refused(COMPUTE_AND_UNBUILD_CHILDREN, NODE, BUILD, CHILD)).isTrue();
        assertThat(refused(COMPUTE, NODE, BUILD, CHILD)).isFalse();
    }

    @Test
    void anActivityAffectingAllRootNetworksReachesEveryRootNetwork() {
        assertThat(refused(EDIT_TREE, NODE, null, BUILD, CHILD, ROOT_NETWORK)).isTrue();
        assertThat(refused(EDIT_TREE, NODE, null, BUILD, CHILD, OTHER_ROOT_NETWORK)).isTrue();
        assertThat(refused(BUILD, CHILD, OTHER_ROOT_NETWORK, EDIT_TREE, NODE, null)).isTrue();
    }

    @Test
    void anActivityInOneRootNetworkDoesNotReachAnother() {
        assertThat(refused(UNBUILD_CHILDREN, NODE, ROOT_NETWORK, BUILD, CHILD, OTHER_ROOT_NETWORK)).isFalse();
    }

    @Test
    void nothingIsRefusedWhenNoActivityIsRunning() {
        List<NodeActivityEntity> nothingRunning = List.of();
        EVERY_NODE.forEach(nodeUuid ->
            assertThat(findActivityConflict(nothingRunning, NodeActivityEntity.from(EDIT_TREE, STUDY, null, nodeUuid), ANCESTORS))
                .isEmpty());
    }

    @Test
    void theConflictNamesTheActivityHoldingTheNode() {
        NodeActivityEntity unbuildingChildren = NodeActivityEntity.from(UNBUILD_CHILDREN, STUDY, ROOT_NETWORK, NODE);
        NodeActivityEntity requested = NodeActivityEntity.from(BUILD, STUDY, ROOT_NETWORK, GRANDCHILD);
        assertThat(findActivityConflict(List.of(unbuildingChildren), requested, ANCESTORS)).contains(unbuildingChildren);
    }
}
