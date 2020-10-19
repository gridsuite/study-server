CREATE TYPE IF NOT EXISTS study.componentResult (
    componentNum int,
    status text,
    iterationCount int,
    slackBusId text,
    slackBusActivePowerMismatch double,
);

CREATE TYPE IF NOT EXISTS study.loadFlowResult (
    isOk boolean,
    metrics frozen<map<text,text>>,
    logs text,
    componentResults frozen<list<study.componentResult>>,
);

CREATE TYPE IF NOT EXISTS study.loadFlowStatus (
    status text
);

CREATE TABLE IF NOT EXISTS study.study (
    studyName text,
    networkUuid uuid,
    networkId text,
    description text,
    caseFormat text,
    caseUuid uuid,
    casePrivate boolean,
    userId text,
    isPrivate boolean,
    loadFlowStatus study.loadFlowStatus,
    loadFlowResult frozen<study.loadFlowResult>,
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
    loadFlowStatus study.loadFlowStatus,
    loadFlowResult frozen <study.loadFlowResult>,
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
    loadFlowStatus study.loadFlowStatus,
    loadFlowResult frozen <study.loadFlowResult>,
    securityAnalysisResultUuid uuid,
    PRIMARY KEY (userId, studyName)
);