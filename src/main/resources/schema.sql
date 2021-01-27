CREATE TYPE IF NOT EXISTS study.loadFlowParameters (
    id uuid,
    voltageInitMode varchar(100),
    transformerVoltageControlOn boolean,
    noGeneratorReactiveLimits boolean,
    phaseShifterRegulationOn boolean,
    twtSplitShuntAdmittance boolean,
    simulShunt boolean,
    readSlackBus boolean,
    writeSlackBus boolean,
    dc boolean,
    distributedSlack boolean,
    balanceType varchar(100),
    PRIMARY KEY (id)
);

CREATE TYPE IF NOT EXISTS study.componentResult (
    id uuid,
    componentNum int,
    status varchar(100),
    iterationCount int,
    slackBusId varchar(100),
    slackBusActivePowerMismatch double,
    loadFlowResultUuid uuid,
    PRIMARY KEY (id)
);

CREATE TYPE IF NOT EXISTS study.loadFlowResult (
    id uuid,
    ok boolean,
    metrics map<varchar(100),varchar(100)>,
    logs varchar(100),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS study.study (
    studyName varchar(100),
    creationDate timestamp,
    networkUuid uuid,
    networkId varchar(100),
    description varchar(100),
    caseFormat varchar(100),
    caseUuid uuid,
    casePrivate boolean,
    userId varchar(100),
    isPrivate boolean,
    loadFlowStatus varchar(100),
    loadFlowResultUuid uuid,
    securityAnalysisResultUuid uuid,
    loadFlowParametersUuid uuid,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS study.studyCreationRequest (
    id uuid,
    studyName varchar(100),
    creationDate timestamp,
    userId varchar(100),
    PRIMARY KEY (id)
);
