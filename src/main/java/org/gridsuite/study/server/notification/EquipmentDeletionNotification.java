package org.gridsuite.study.server.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Setter
@Getter
public class EquipmentDeletionNotification {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentDeletionNotification.class);

    String id;
    String type;

    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.toString());
        }
        return "";
    }
}

