CREATE TABLE IF NOT EXISTS study.study (
    studyName text PRIMARY KEY,
    networkUuid uuid,
    networkId text,
    caseName text,
    description text
);
