CREATE TYPE IF NOT EXISTS study.loadFlowResult (
    status text
);



CREATE TABLE IF NOT EXISTS study.study (
    studyName text PRIMARY KEY,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    loadFlowResult study.loadFlowResult
);
