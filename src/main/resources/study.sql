create table loadflowparameters
(
    id uuid not null
        constraint loadflowparameters_pkey
            primary key,
    balancetype                 varchar(255),
    dc                          boolean,
    distributedslack            boolean,
    nogeneratorreactivelimits   boolean,
    phaseshifterregulationon    boolean,
    readslackbus                boolean,
    simulshunt                  boolean,
    transformervoltagecontrolon boolean,
    twtsplitshuntadmittance     boolean,
    voltageinitmode             varchar(255),
    writeslackbus               boolean
);

alter table loadflowparameters
    owner to postgres;

create table loadflowresult
(
    id uuid not null
        constraint loadflowresult_pkey
            primary key,
    logs text,
    ok   boolean
);

alter table loadflowresult
    owner to postgres;

create table componentresult
(
    id uuid not null
        constraint componentresult_pkey
            primary key,
    componentnum                integer,
    iterationcount              integer,
    slackbusactivepowermismatch double precision,
    slackbusid                  varchar(255),
    status                      integer,
    loadflowresult_id           uuid
        constraint componentresult_loadflowresult_fk
            references loadflowresult
);

alter table componentresult
    owner to postgres;

create table loadflowresultentity_metrics
(
    loadflowresultentity_id uuid         not null
        constraint loadflowresultentity_metrics_fk
            references loadflowresult,
    metrics                 varchar(255),
    metrics_key             varchar(255) not null,
    constraint loadflowresultentity_metrics_pkey
        primary key (loadflowresultentity_id, metrics_key)
);

alter table loadflowresultentity_metrics
    owner to postgres;

create table study
(
    id uuid not null
        constraint study_pkey
            primary key,
    caseformat                 varchar(255),
    caseprivate                boolean,
    caseuuid                   uuid,
    creationdate               timestamp,
    description                varchar(255),
    isprivate                  boolean,
    loadflowstatus             varchar(255),
    networkid                  varchar(255),
    networkuuid                uuid,
    securityanalysisresultuuid uuid,
    studyname                  varchar(255) not null,
    userid                     varchar(255) not null,
    loadflowparameters_id      uuid
        constraint loadflowparameters_id_fk
            references loadflowparameters,
    loadflowresultentity_id    uuid
        constraint loadflowresult_id_fk
            references loadflowresult
);

alter table study
    owner to postgres;

create table studycreationrequest
(
    id uuid not null
        constraint studycreationrequest_pkey
            primary key,
    creationdate timestamp,
    isprivate    boolean,
    studyname    varchar(255) not null,
    userid       varchar(255) not null
);

alter table studycreationrequest
    owner to postgres;

