CREATE KEYSPACE "study" WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1};

CREATE TABLE IF NOT EXISTS study.study (
    studyName text PRIMARY KEY,
    networkUuid uuid,
    networkId text,
    caseName text,
    description text,
    caseFormat text
);