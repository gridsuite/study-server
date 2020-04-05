[![Actions Status](https://github.com/powsybl/powsybl-study-server/workflows/CI/badge.svg)](https://github.com/powsybl/powsybl-study-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-study-server&metric=coverage)](https://sonarcloud.io/component_measures?id=com.powsybl%3Apowsybl-study-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Join the community on Spectrum](https://withspectrum.github.io/badge/badge.svg)](https://spectrum.chat/powsybl)
# powsybl-study-server

### Run integration tests

To run the integration tests, you need to install a [Cassandra 4.0-alpha3](https://downloads.apache.org/cassandra/4.0-alpha3/) in your home directory.

When it's done you'll have to modify some configuration in the cassandra you just installed. Go to the ~/apache-cassandra-4.0-alpha3/conf/cassandra.yaml file and enable the materialized views by changing :

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
