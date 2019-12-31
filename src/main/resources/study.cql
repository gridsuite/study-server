-- for single node deployments
-- CREATE KEYSPACE IF NOT EXISTS study WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };
-- for multi datacenters deployments
CREATE KEYSPACE IF NOT EXISTS study WITH REPLICATION = { 'class' : 'NetworkTopologyStrategy', 'dc1' : 1, 'dc2' : 1 };

CREATE TABLE IF NOT EXISTS study.study (
    studyName text PRIMARY KEY,
    networkUuid uuid,
    networkId text,
    caseName text,
    description text
);

