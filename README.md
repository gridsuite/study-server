[![Actions Status](https://github.com/gridsuite/study-server/workflows/CI/badge.svg)](https://github.com/gridsuite/study-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Astudy-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Astudy-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
# study-server

### Run integration tests

To run the integration tests, you need to install a [Cassandra 4.0-alpha4](https://downloads.apache.org/cassandra/4.0-alpha4/) in your home directory.

When it's done you'll have to modify some configuration in the cassandra you just installed. Go to the ~/apache-cassandra-4.0-alpha4/conf/cassandra.yaml file and enable the materialized views by changing :

```yaml
enable_materialized_views: false
```

to

```yaml
enable_materialized_views: true
```

and change the storage port by replacing :

```yaml
- seeds: "127.0.0.1:7000"
```

by

```yaml
- seeds: "127.0.0.1:16432"
```

You can then run the integration tests by activating the maven profile perform-integration-test
