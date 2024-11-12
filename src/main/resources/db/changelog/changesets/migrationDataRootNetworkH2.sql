-- Migrating data into the root_network table, making sure we don't insert duplicates
INSERT INTO root_network (
    id,                  -- UUID (Primary Key) for the root_network table
    case_format,         -- Case format from the study
    case_name,           -- Case name from the study
    case_uuid,           -- UUID associated with the case from the study
    network_id,          -- Network ID from the study
    network_uuid,        -- UUID associated with the network from the study
    report_uuid,         -- UUID from the root_node_info (report)
    study_uuid           -- UUID associated with the study (foreign key to study table)
)
SELECT
    random_uuid(),   -- Generate a new unique UUID for the root_network row
    s.case_format,       -- Retrieve case_format from the study table
    s.case_name,         -- Retrieve case_name from the study table
    s.case_uuid,         -- Retrieve case_uuid from the study table
    s.network_id,        -- Retrieve network_id from the study table
    s.network_uuid,      -- Retrieve network_uuid from the study table
    r.report_uuid,       -- Retrieve report_uuid from root_node_info, linked by node
    s.id AS study_uuid   -- Use the study ID as study_uuid (foreign key)
FROM
    study s              -- Start from the study table
        JOIN node n ON n.study_id = s.id            -- Join with node table based on study_id
        JOIN root_node_info r ON r.id_node = n.id_node  -- Join with root_node_info based on node_id
WHERE
    n.type = 'ROOT'
  AND NOT EXISTS (        -- Prevent duplicates: check if a root_network entry with the same study_uuid already exists
    SELECT 1
    FROM root_network tp
    WHERE tp.study_uuid = s.id
);

-- Migrating data into the root_network_node_info table, ensuring no duplicate rows are inserted
INSERT INTO root_network_node_info (
    id,                              -- UUID (Primary Key) for root_network_node_info
    root_network_id,                    -- Foreign Key, linked to the root_network table
    node_info_id,                     -- Foreign Key, linked to the network_modification_node_info table
    dynamic_simulation_result_uuid,   -- UUID for dynamic simulation result
    loadflow_result_uuid,             -- UUID for load flow result
    global_build_status,              -- Global build status
    local_build_status,               -- Local build status
    non_evacuated_energy_result_uuid, -- UUID for non-evacuated energy result
    one_bus_short_circuit_analysis_result_uuid, -- UUID for one bus short-circuit analysis result
    security_analysis_result_uuid,    -- UUID for security analysis result
    sensitivity_analysis_result_uuid, -- UUID for sensitivity analysis result
    short_circuit_analysis_result_uuid,-- UUID for short-circuit analysis result
    state_estimation_result_uuid,     -- UUID for state estimation result
    variant_id,                       -- Variant ID for the network modification
    voltage_init_result_uuid          -- UUID for voltage initialization result
)
SELECT
    random_uuid(),                -- Generate a new unique UUID for each root_network_node_info row
    t.id,                             -- Reference the id of the associated root_network
    n.id_node,                        -- Reference the id of the associated network_modification_node_info
    n.dynamic_simulation_result_uuid, -- Retrieve dynamic simulation result UUID  from the table network_modification_node_info
    n.loadflow_result_uuid,           -- Retrieve load flow result UUID  from the table network_modification_node_info
    n.global_build_status,            -- Retrieve global build status  from the table network_modification_node_info
    n.local_build_status,             -- Retrieve local build status  from the table network_modification_node_info
    n.non_evacuated_energy_result_uuid, -- Retrieve non-evacuated energy result UUID  from the table network_modification_node_info
    n.one_bus_short_circuit_analysis_result_uuid, -- Retrieve one bus short-circuit analysis result UUID  from the table network_modification_node_info
    n.security_analysis_result_uuid,  -- Retrieve security analysis result UUID  from the table network_modification_node_info
    n.sensitivity_analysis_result_uuid,-- Retrieve sensitivity analysis result UUID  from the table network_modification_node_info
    n.short_circuit_analysis_result_uuid,-- Retrieve short-circuit analysis result UUID  from the table network_modification_node_info
    n.state_estimation_result_uuid,   -- Retrieve state estimation result UUID  from the table network_modification_node_info
    n.variant_id,                     -- Retrieve variant ID  from the table network_modification_node_info
    n.voltage_init_result_uuid        -- Retrieve voltage initialization result UUID  from the table network_modification_node_info
FROM
    network_modification_node_info n    -- Start from the network_modification_node_info table
        JOIN node ne ON ne.id_node = n.id_node  -- Join with the node table based on id_node
        JOIN root_network t ON ne.study_id = t.study_uuid -- Join with root_network based on study_uuid
WHERE
    NOT EXISTS (                        -- Prevent duplicates: check if a root_network_node_info entry with the same root_network_id and node_info_id already exists
        SELECT 1
        FROM root_network_node_info tpni
        WHERE tpni.root_network_id = t.id
          AND tpni.node_info_id = n.id_node
    );

UPDATE modification_reports mr
SET root_network_node_info_entity_id = (
    SELECT tpn.id
    FROM network_modification_node_info n
             JOIN root_network_node_info tpn ON n.id_node = tpn.node_info_id
    WHERE mr.network_modification_node_info_entity_id_node = n.id_node
)
WHERE mr.root_network_node_info_entity_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM network_modification_node_info n
             JOIN root_network_node_info tpn ON n.id_node = tpn.node_info_id
    WHERE mr.network_modification_node_info_entity_id_node = n.id_node
);

UPDATE computation_reports cr
SET root_network_node_info_entity_id = (
    SELECT tpn.id
    FROM network_modification_node_info n
             JOIN root_network_node_info tpn ON n.id_node = tpn.node_info_id
    WHERE cr.network_modification_node_info_entity_id_node = n.id_node
)
WHERE cr.root_network_node_info_entity_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM network_modification_node_info n
             JOIN root_network_node_info tpn ON n.id_node = tpn.node_info_id
    WHERE cr.network_modification_node_info_entity_id_node = n.id_node
);

