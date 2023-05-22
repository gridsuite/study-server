package org.gridsuite.study.server.dto.dynamicmapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class VariablesSetInfos {
    private String name;
    private List<ModelVariableDefinitionInfos> variableDefinitions;
}
