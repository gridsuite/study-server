/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.annotations;

import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com
 */
@Aspect
@AllArgsConstructor
@Component
public class PostCompletionAnnotationAspect {
    private final PostCompletionAdapter postCompletionAdapter;

    @Around("@annotation(PostCompletion)")
    public Object executePostCompletion(final ProceedingJoinPoint pjp) {
        postCompletionAdapter.execute(new PjpAfterCompletionRunnable(pjp));
        return null;
    }

    private static final class PjpAfterCompletionRunnable implements Runnable {
        private final ProceedingJoinPoint pjp;

        public PjpAfterCompletionRunnable(ProceedingJoinPoint pjp) {
            this.pjp = pjp;
        }

        @Override
        public void run() {
            try {
                pjp.proceed(pjp.getArgs());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
