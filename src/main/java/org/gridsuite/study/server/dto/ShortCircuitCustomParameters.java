package org.gridsuite.study.server.dto;

import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author AJELLAL Ali <ali.ajellal@rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ShortCircuitCustomParameters extends ShortCircuitParameters {
    private ShortCircuitPredefinedParametersType predefinedParameters;
}
