package org.gridsuite.study.server.repository.rootnetwork;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RootNetworkCreationRequestRepository extends JpaRepository<RootNetworkCreationRequestEntity, UUID> {
}
