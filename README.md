[![Actions Status](https://github.com/gridsuite/study-server/workflows/CI/badge.svg)](https://github.com/gridsuite/study-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Astudy-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Astudy-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
# study-server
To automatically generate the sql schema file you can use the following command:
```
mvn package -DskipTests && rm -f src/main/resources/study.sql && java  -jar target/gridsuite-study-server-1.0.0-SNAPSHOT-exec.jar --spring.jpa.properties.javax.persistence.schema-generation.scripts.action=create 
```


to generate a new changeSet (diff between current schema & new schema):
```
mvn compile && mvn liquibase:dropAll@dummyDB && mvn liquibase:update@dummyDB && mvn liquibase:diff@dummyDB
```
and add the generated file in in src/resource/db/changelog/db.changelog-master.yml

to generate a plain sql file from the hibernate annotations (for easy inspecting, should be equivalent to generating with hibernate)
```
mvn liquibase:generateChangeLog -Dliquibase.outputFile=generated.postgresql.sql
```

```
mvn liquibase:help
```