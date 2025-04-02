# Study server

[![Actions Status](https://github.com/gridsuite/study-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/study-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Astudy-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Astudy-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets. 
After you generated a changeset do not forget to add it to git and in src/resource/db/changelog/db.changelog-master.yml

The old way to automatically generate the sql schema file (directly using hibernate) can still be used for debugging. Use the following command:
```
mvn package -DskipTests && rm -f src/main/resources/study.sql && java  -jar target/gridsuite-study-server-1.0.0-SNAPSHOT-exec.jar --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create 
```
