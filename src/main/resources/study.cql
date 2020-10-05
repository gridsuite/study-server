CREATE TYPE IF NOT EXISTS study.loadFlowResult (
    status text
);

CREATE TABLE IF NOT EXISTS study.study (
    studyName text,
    networkUuid uuid,
    networkId text,
    creationDate timestamp,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    userId text,
    isPrivate boolean,
    loadFlowResult study.loadFlowResult,
    securityAnalysisResultUuid uuid,
    PRIMARY KEY (userId, studyName)
);

CREATE TABLE IF NOT EXISTS study.privateStudy (
    studyName text,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    userId text,
    isPrivate boolean,
    loadFlowResult study.loadFlowResult,
    securityAnalysisResultUuid uuid,
    PRIMARY KEY (userId, studyName)
);

CREATE TABLE IF NOT EXISTS study.publicStudy (
    studyName text,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    userId text,
    isPrivate boolean,
    loadFlowResult study.loadFlowResult,
    securityAnalysisResultUuid uuid,
    PRIMARY KEY (userId, studyName)
);
