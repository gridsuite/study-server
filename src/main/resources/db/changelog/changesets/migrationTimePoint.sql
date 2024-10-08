-- Start a transaction to ensure data integrity
BEGIN;
-- Step 1: Insert data from study to timepoint
INSERT INTO timepoint (id, network_uuid, network_id, case_format, case_uuid, case_name, study_uuid)
SELECT
    gen_random_uuid(),
    network_uuid,
    network_id,
    case_format,
    case_uuid,
    case_name,
    id AS study_uuid
FROM
    study;

-- Step 2: Drop the columns from the study table

-- Step 3:

