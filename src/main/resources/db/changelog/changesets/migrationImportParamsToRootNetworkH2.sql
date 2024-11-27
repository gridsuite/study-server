-- Migrating import_parameters to be linked to root_network instead of study
UPDATE import_parameters ip
SET root_network_entity_id = (
    SELECT rn.id
    FROM root_network rn
             JOIN study s ON rn.study_uuid = s.id
    WHERE s.id = ip.study_entity_id
    LIMIT 1
);