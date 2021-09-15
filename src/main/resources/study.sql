
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
        connectedComponentNum int4,
        iterationCount int4,
        slackBusActivePowerMismatch float8,
        slackBusId varchar(255),
        status int4,
        synchronousComponentNum int4
    );

    create table LoadFlowResultEntity_metrics (
       LoadFlowResultEntity_id uuid not null,
        metrics varchar(255),
        metrics_KEY varchar(255) not null,
        primary key (LoadFlowResultEntity_id, metrics_KEY)
    );

    create table ModelInfo (
       idNode uuid not null,
        description varchar(255),
        name varchar(255),
        model varchar(255),
        primary key (idNode)
    );

    create table NetworkModificationNodeInfo (
       idNode uuid not null,
        description varchar(255),
        name varchar(255),
        networkModificationId uuid,
        primary key (idNode)
    );

    create table Node (
       idNode uuid not null,
        type varchar(255),
        parentNode uuid,
        study_id uuid,
        primary key (idNode)
    );

    create table RootNodeInfo (
       idNode uuid not null,
        description varchar(255),
        name varchar(255),
        primary key (idNode)
    );

    create table study (
       id uuid not null,
        caseFormat varchar(255) not null,
        casePrivate boolean not null,
        caseUuid uuid not null,
        creationDate timestamp not null,
        description varchar(255) not null,
        isPrivate boolean not null,
        loadFlowProvider varchar(255),
        loadFlowStatus varchar(255),
        networkId varchar(255) not null,
        networkUuid uuid not null,
        securityAnalysisResultUuid uuid,
        studyName varchar(255) not null,
        userId varchar(255) not null,
        loadFlowParametersEntity_id uuid not null,
        loadFlowResultEntity_id uuid,
        primary key (id)
    );

    create table studycreationrequest (
       id uuid not null,
        creationDate timestamp not null,
        isPrivate boolean not null,
        studyName varchar(255) not null,
        userId varchar(255) not null,
        primary key (id)
    );
create index loadFlowResultEntity_componentResults_id_index on LoadFlowResultEntity_componentResults (LoadFlowResultEntity_id);
create index nodeEntity_parentNode_idx on Node (parentNode);
create index nodeEntity_studyId_idx on Node (study_id);
create index studyEntity_isPrivate_index on study (isPrivate);
create index studyEntity_userId_index on study (userId);
create index studyCreationRequest_isPrivate_index on studycreationrequest (isPrivate);
create index studyCreationRequest_userId_index on studycreationrequest (userId);

    alter table if exists LoadFlowResultEntity_componentResults 
       add constraint loadFlowResultEntity_componentResults_fk 
       foreign key (LoadFlowResultEntity_id) 
       references loadFlowResult;

    alter table if exists LoadFlowResultEntity_metrics 
       add constraint loadFlowResultEntity_metrics_fk 
       foreign key (LoadFlowResultEntity_id) 
       references loadFlowResult;

    alter table if exists ModelInfo 
       add constraint FK2ppp9cps0tclhqfi7qf90ctgi 
       foreign key (idNode) 
       references Node;

    alter table if exists NetworkModificationNodeInfo 
       add constraint FKnjm62y6yguikmhguw9c4v8ycv 
       foreign key (idNode) 
       references Node;

    alter table if exists Node 
       add constraint node_id_fk_constraint 
       foreign key (parentNode) 
       references Node;

    alter table if exists Node 
       add constraint study_id_fk_constraint 
       foreign key (study_id) 
       references study;

    alter table if exists RootNodeInfo 
       add constraint FK5wmbraw6u13v1ujb15vygr9xi 
       foreign key (idNode) 
       references Node;

    alter table if exists study 
       add constraint loadFlowParameters_id_fk 
       foreign key (loadFlowParametersEntity_id) 
       references loadFlowParameters;

    alter table if exists study 
       add constraint loadFlowResult_id_fk 
       foreign key (loadFlowResultEntity_id) 
       references loadFlowResult;
