CREATE TYPE IF NOT EXISTS study.loadFlowResult (
    status text
);

CREATE TABLE IF NOT EXISTS study.study (
    studyName text,
    creationDate timestamp,
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

CREATE TABLE IF NOT EXISTS study.privateStudy (
    studyName text,
    creationDate timestamp,
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
    creationDate timestamp,
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

CREATE TABLE IF NOT EXISTS study.publicStudyCreationRequest (
    studyName text,
    creationDate timestamp,
    userId text,
    PRIMARY KEY (userId, studyName)
);

CREATE TABLE IF NOT EXISTS study.privateStudyCreationRequest (
    studyName text,
    creationDate timestamp,
    userId text,
    PRIMARY KEY (userId, studyName)
);
