CREATE TABLE IF NOT EXISTS study.study (
    studyName text,
    networkUuid uuid,
    networkId text,
    creationDate timestamp,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    PRIMARY KEY (studyName, creationDate)
)
WITH CLUSTERING ORDER BY (creationDate DESC);
