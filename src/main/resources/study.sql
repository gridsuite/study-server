
    create table load_flow_parameters (
       id uuid not null,
        balance_type varchar(255),
        connected_component_mode varchar(255),
        dc boolean default false,
        dc_use_transformer_ratio boolean default true,
        distributed_slack boolean default true,
        hvdc_ac_emulation boolean default true,
        no_generator_reactive_limits boolean default false,
        phase_shifter_regulation_on boolean default false,
        read_slack_bus boolean default true,
        shunt_compensator_voltage_control_on boolean default false,
        transformer_voltage_control_on boolean default false,
        twt_split_shunt_admittance boolean default false,
        voltage_init_mode varchar(255),
        write_slack_bus boolean default false,
        primary key (id)
    );

    create table load_flow_parameters_entity_countries_to_balance (
       load_flow_parameters_entity_id uuid not null,
        countries_to_balance varchar(255)
    );

    create table load_flow_result (
       id uuid not null,
        logs CLOB,
        ok boolean,
        primary key (id)
    );

    create table load_flow_result_entity_component_results (
       load_flow_result_entity_id uuid not null,
        connected_component_num int4,
        distributed_active_power float8,
        iteration_count int4,
        slack_bus_active_power_mismatch float8,
        slack_bus_id varchar(255),
        status int4,
        synchronous_component_num int4
    );

    create table load_flow_result_entity_metrics (
       load_flow_result_entity_id uuid not null,
        metrics varchar(255),
        metrics_key varchar(255) not null,
        primary key (load_flow_result_entity_id, metrics_key)
    );

    create table network_modification_node_info (
       id_node uuid not null,
        description varchar(255),
        name CLOB,
        read_only boolean,
        report_uuid uuid,
        build_status varchar(255) not null,
        load_flow_status varchar(255),
        modification_group_uuid uuid,
        security_analysis_result_uuid uuid,
        variant_id varchar(255),
        load_flow_result_entity_id uuid,
        primary key (id_node)
    );

    create table network_modification_node_info_entity_modifications_to_exclude (
       network_modification_node_info_entity_id_node uuid not null,
        modifications_to_exclude uuid
    );

    create table node (
       id_node uuid not null,
        type varchar(255),
        parent_node uuid,
        study_id uuid,
        primary key (id_node)
    );

    create table root_node_info (
       id_node uuid not null,
        description varchar(255),
        name CLOB,
        read_only boolean,
        report_uuid uuid,
        primary key (id_node)
    );

    create table short_circuit_parameters (
       id uuid not null,
        min_voltage_drop_proportional_threshold float8,
        study_type varchar(255),
        with_feeder_result boolean default true,
        with_limit_violations boolean default true,
        with_voltage_map boolean default true,
        primary key (id)
    );

    create table study (
       id uuid not null,
        case_format varchar(255) not null,
        case_name varchar(255) not null,
        case_private boolean not null,
        case_uuid uuid not null,
        creation_date timestamp not null,
        load_flow_provider varchar(255),
        network_id varchar(255) not null,
        network_uuid uuid not null,
        user_id varchar(255) not null,
        load_flow_parameters_entity_id uuid not null,
        short_circuit_parameters_entity_id uuid not null,
        primary key (id)
    );

    create table study_creation_request (
       id uuid not null,
        creation_date timestamp not null,
        user_id varchar(255) not null,
        primary key (id)
    );
create index loadFlowParametersEntity_countriesToBalance_idx1 on load_flow_parameters_entity_countries_to_balance (load_flow_parameters_entity_id);
create index loadFlowResultEntity_componentResults_id_index on load_flow_result_entity_component_results (load_flow_result_entity_id);
create index networkModificationNodeInfoEntity_modificationsToExclude_idx on network_modification_node_info_entity_modifications_to_exclude (network_modification_node_info_entity_id_node);
create index nodeEntity_parentNode_idx on node (parent_node);
create index nodeEntity_studyId_idx on node (study_id);
create index studyEntity_userId_index on study (user_id);
create index studyCreationRequest_userId_index on study_creation_request (user_id);

    alter table if exists load_flow_parameters_entity_countries_to_balance 
       add constraint loadFlowParametersEntity_countriesToBalance_fk1 
       foreign key (load_flow_parameters_entity_id) 
       references load_flow_parameters;

    alter table if exists load_flow_result_entity_component_results 
       add constraint loadFlowResultEntity_componentResults_fk 
       foreign key (load_flow_result_entity_id) 
       references load_flow_result;

    alter table if exists load_flow_result_entity_metrics 
       add constraint loadFlowResultEntity_metrics_fk 
       foreign key (load_flow_result_entity_id) 
       references load_flow_result;

    alter table if exists network_modification_node_info 
       add constraint loadFlowResult_id_fk 
       foreign key (load_flow_result_entity_id) 
       references load_flow_result;

    alter table if exists network_modification_node_info 
       add constraint FKc40g1mgugxpmbmpr4m1c8w7pv 
       foreign key (id_node) 
       references node;

    alter table if exists network_modification_node_info_entity_modifications_to_exclude 
       add constraint networkModificationNodeInfoEntity_modificationsToExclude_fk 
       foreign key (network_modification_node_info_entity_id_node) 
       references network_modification_node_info;

    alter table if exists node 
       add constraint parent_node_id_fk_constraint 
       foreign key (parent_node) 
       references node;

    alter table if exists node 
       add constraint study_id_fk_constraint 
       foreign key (study_id) 
       references study;

    alter table if exists root_node_info 
       add constraint FK4x41cf843vsqsrl1y9ao1508e 
       foreign key (id_node) 
       references node;

    alter table if exists study 
       add constraint loadFlowParameters_id_fk 
       foreign key (load_flow_parameters_entity_id) 
       references load_flow_parameters;

    alter table if exists study 
       add constraint shortCircuitParameters_id_fk 
       foreign key (short_circuit_parameters_entity_id) 
       references short_circuit_parameters;
