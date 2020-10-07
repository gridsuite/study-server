
CREATE TYPE IF NOT EXISTS study.loadFlowParameters (
    voltageInitMode text,
    transformerVoltageControlOn boolean,
    noGeneratorReactiveLimits boolean,
    phaseShifterRegulationOn boolean,
    twtSplitShuntAdmittance boolean,
    simulShunt boolean,
    readSlackBus boolean,
    writeSlackBus boolean
);

CREATE TYPE IF NOT EXISTS study.loadFlowResult (
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
    loadFlowResult study.loadFlowResult,
    loadFlowParameters study.loadFlowParameters,
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
    loadFlowParameters study.loadFlowParameters,
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
    loadFlowParameters study.loadFlowParameters,
    PRIMARY KEY (userId, studyName)
);
