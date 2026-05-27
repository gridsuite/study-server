/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.common;

import org.gridsuite.study.server.dto.UserProfileInfos;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Function;

/**
 * @author Abdelsalem HEDHILI <abdelsalem.hedhili at rte-france.com>
 */
public interface ComputationParameters {

    UUID duplicateParameters(UUID parametersUuid);

    void deleteParameters(UUID parametersUuid);

    default void doDeleteComputationParameters(UUID parametersUuid, String computationType, Logger logger) {
        if (parametersUuid != null) {
            try {
                deleteParameters(parametersUuid);
            } catch (Exception e) {
                logger.error("Could not remove {} parameters with uuid: {}", computationType, parametersUuid, e);
            }
        }
    }

    UUID createDefaultParameters();

    default UUID doCreateDefaultParameters(String userId,
                                           UserProfileInfos userProfileInfos,
                                           Function<UserProfileInfos, UUID> profileParameterGetter,
                                           String computationType,
                                           Logger logger) {
        UUID profileParameterId = userProfileInfos == null ?
                null :
                profileParameterGetter.apply(userProfileInfos);

        if (profileParameterId != null) {
            try {
                return duplicateParameters(profileParameterId);
            } catch (Exception e) {
                logger.error("Could not duplicate {} parameters with id '{}' from user/profile '{}/{}'. Using default parameters",
                        computationType, profileParameterId, userId, userProfileInfos.getName(), e);
            }
        }

        try {
            return createDefaultParameters();
        } catch (Exception e) {
            logger.error("Error while creating default parameters for {}", computationType, e);
            return null;
        }
    }
}
