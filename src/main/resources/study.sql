
    create table loadFlowParameters (
       id uuid not null,
        balanceType varchar(255),
        dc boolean,
        distributedSlack boolean,
        noGeneratorReactiveLimits boolean,
        phaseShifterRegulationOn boolean,
        readSlackBus boolean,
        simulShunt boolean,
        transformerVoltageControlOn boolean,
        twtSplitShuntAdmittance boolean,
        voltageInitMode varchar(255),
        writeSlackBus boolean,
        primary key (id)
    );

    create table loadFlowResult (
       id uuid not null,
        logs TEXT,
        ok boolean,
        primary key (id)
    );

    create table LoadFlowResultEntity_componentResults (
       LoadFlowResultEntity_id uuid not null,
        componentNum int4,
        iterationCount int4,
        slackBusActivePowerMismatch float8,
        slackBusId varchar(255),
        status int4
    );

    create table LoadFlowResultEntity_metrics (
       LoadFlowResultEntity_id uuid not null,
        metrics varchar(255),
        metrics_KEY varchar(255) not null,
        primary key (LoadFlowResultEntity_id, metrics_KEY)
    );

    create table study (
       id uuid not null,
        caseFormat varchar(255),
        casePrivate boolean,
        caseUuid uuid,
        creationDate timestamp,
        description varchar(255),
        isPrivate boolean,
        loadFlowStatus varchar(255),
        networkId varchar(255),
        networkUuid uuid,
        securityAnalysisResultUuid uuid,
        studyName varchar(255) not null,
        userId varchar(255) not null,
        loadFlowParametersEntity_id uuid,
        loadFlowResultEntity_id uuid,
        primary key (id)
    );

    create table studycreationrequest (
       id uuid not null,
        creationDate timestamp,
        isPrivate boolean,
        studyName varchar(255) not null,
        userId varchar(255) not null,
        primary key (id)
    );
create index isPrivate_index on study (isPrivate);

    alter table if exists LoadFlowResultEntity_componentResults
       add constraint loadFlowResultEntity_componentResults_fk
       foreign key (LoadFlowResultEntity_id)
       references loadFlowResult;

    alter table if exists LoadFlowResultEntity_metrics
       add constraint loadFlowResultEntity_metrics_fk
       foreign key (LoadFlowResultEntity_id)
       references loadFlowResult;

    alter table if exists study
       add constraint loadFlowParameters_id_fk
       foreign key (loadFlowParametersEntity_id)
       references loadFlowParameters;

    alter table if exists study
       add constraint loadFlowResult_id_fk
       foreign key (loadFlowResultEntity_id)
       references loadFlowResult;
