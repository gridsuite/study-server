package org.gridsuite.study.server.utils.annotations;

import org.gridsuite.study.server.service.client.RemoteServiceName;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour gérer les appels REST optionnels
 * Wraps la méthode pour :
 * 1. Exécuter la méthode
 * 2. Si elle rate et que le service est optionnel et DOWN, retourner null
 * 3. Sinon, forwarder l'erreur
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandleOptionalServiceFailure {
//    RemoteServiceName service() default RemoteServiceName.STATE_ESTIMATION_SERVER;
}
