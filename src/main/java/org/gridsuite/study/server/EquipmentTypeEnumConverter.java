package org.gridsuite.study.server;

import org.springframework.stereotype.Component;

import java.beans.PropertyEditorSupport;

@Component
public class EquipmentTypeEnumConverter extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) throws IllegalArgumentException {

        EquipmentType equipmentType = EquipmentType.fromString(text);
        setValue(equipmentType);
    }
}
