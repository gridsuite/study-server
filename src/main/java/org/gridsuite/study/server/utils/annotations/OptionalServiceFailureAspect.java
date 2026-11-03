package org.gridsuite.study.server.utils.annotations;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.gridsuite.study.server.service.RemoteServicesInspector;
import org.gridsuite.study.server.service.common.AbstractComputationRestService;
import org.springframework.stereotype.Component;

/**
 * Aspect pour gérer automatiquement les erreurs des services optionnels
 */
@Aspect
@Component
@Slf4j
public class OptionalServiceFailureAspect {
    // TODO : ne marche pas bien car les problème de "proxy" spring font que si l'appel se fait d'une classe vers elle même l'anotation n'est pas appelée

    @Around("@annotation(org.gridsuite.study.server.utils.annotations.HandleOptionalServiceFailure)")
    public Object handleOptionalServiceFailure(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            System.out.println("---------------------- handleOptionalServiceFailure");
            return joinPoint.proceed();
        } catch (Exception e) {
            System.out.println("---------------------- handleOptionalServiceFailure ---- catch");

            Object target = joinPoint.getTarget();

            if (target instanceof AbstractComputationRestService) {
                AbstractComputationRestService service = (AbstractComputationRestService) target;

                // Vérifier si le service est optionnel et DOWN
                if (service.isOptionalAndDown()) {
                    System.out.println("Optional service is DOWN, returning null instead of throwing error " +  e);
                    return null;
                }
            }

            // Sinon, forwarder l'erreur
            throw e;
        }
    }
}
