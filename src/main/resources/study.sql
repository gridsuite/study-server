create table if exists componentResult (
        id  bigserial not null,
        componentNum int4,
        iterationCount int4,
        slackBusActivePowerMismatch float8,
        slackBusId varchar(255),
        status int4,
        loadFlowResult_id int8,
        primary key (id)
)

create table if exists loadFlowResult (
        id  bigserial not null,
        logs varchar(255),
        ok boolean,
        primary key (id)
)

create table if exists LoadFlowResultEntity_metrics (
       LoadFlowResultEntity_id int8 not null,
        metrics varchar(255),
        metrics_KEY varchar(255) not null,
        primary key (LoadFlowResultEntity_id, metrics_KEY)
)

create table if exists loadFlowParameters (
        id  bigserial not null,
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
)

create table if exists study (
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
        loadFlowParameters_id int8,
        loadFlowResultEntity_id int8,
        primary key (id)
)

create table if exists studycreationrequest (
        id  bigserial not null,
        creationDate timestamp,
        isPrivate boolean,
        studyName varchar(255) not null,
        userId varchar(255) not null,
        primary key (id)
)


alter table if exists componentResult
   add constraint componentResult_loadFlowResult_fk
   foreign key (loadFlowResult_id)
   references loadFlowResult

alter table if exists LoadFlowResultEntity_metrics
   add constraint FK4le4ft8plnrh3e1hgslauh1md
   foreign key (LoadFlowResultEntity_id)
   references loadFlowResult

alter table if exists study
   add constraint loadFlowParameters_id_fk
   foreign key (loadFlowParameters_id)
   references loadFlowParameters

alter table if exists study
   add constraint loadFlowResult_id_fk
   foreign key (loadFlowResultEntity_id)
   references loadFlowResult
