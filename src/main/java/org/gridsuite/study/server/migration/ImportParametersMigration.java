/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.UpdateStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Migrates the importParameters values stored in the importParameters table by
 * deserializing each value and re-serializing it to ensure consistent JSON format.
 * If a value is not valid JSON, it is wrapped in quotes.
 */
public class ImportParametersMigration implements CustomSqlChange {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportParametersMigration.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SqlStatement[] generateStatements(Database database) throws CustomChangeException {
        JdbcConnection connection = (JdbcConnection) database.getConnection();
        List<SqlStatement> statements = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT root_network_entity_id, import_parameters_key, import_parameters FROM import_parameters")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID rootNetworkEntityId = rs.getObject("root_network_entity_id", UUID.class);
                String key = rs.getString("import_parameters_key");
                String value = rs.getString("import_parameters");

                String migratedValue = migrateValue(value);

                try (PreparedStatement updateStmt = connection.prepareStatement(
                        "UPDATE import_parameters SET import_parameters = ? WHERE root_network_entity_id = ? AND import_parameters_key = ?")) {
                    updateStmt.setString(1, migratedValue);
                    updateStmt.setObject(2, rootNetworkEntityId);
                    updateStmt.setString(3, key);
                    updateStmt.executeUpdate();
                }
            }
        } catch (SQLException | DatabaseException e) {
            throw new CustomChangeException(e);
        }
        return statements.toArray(new SqlStatement[0]);
    }

    private String migrateValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            Object deserialized = objectMapper.readValue(value, Object.class);
            return objectMapper.writeValueAsString(deserialized);
        } catch (JsonProcessingException e) {
            // if the value is not valid JSON, it is a string value and it should be wrapped in quotes.
            return "\"" + value + "\"";
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "importParameters values were successfully migrated";
    }

    @Override
    public void setUp() throws SetupException {
        LOGGER.info("Set up migration for importParameters");
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        LOGGER.info("Set file opener for importParameters migration");
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}