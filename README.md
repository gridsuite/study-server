[![Actions Status](https://github.com/gridsuite/study-server/workflows/CI/badge.svg)](https://github.com/gridsuite/study-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Astudy-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Astudy-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
# study-server

### Run integration tests

To run the integration tests, a cassandra distribution is downloaded automatically once for all the projects at the first execution for your user. It is stored in $HOME/.embedded-cassandra . For this first execution, you need http internet access, and if you are a on a restricted network requiring a proxy, you need to set the proxy details. In addition to the standard java system properties -DproxyHost, -DproxyPort, we use -DproxyUser and -DproxyPassword for authenticated proxies. In IDEs, set them in the tests system properties (usually in the "Edit run configuration" menu). For maven CLI, either set them in MAVEN_OPTS or directly on the command line:

```bash
$ export MAVEN_OPTS="-DproxyHost=proxy.com -DproxyPort=8080 -DproxyUser=user -DproxyPassword=XXXX"
$ mvn verify
```

OR

```bash
$ mvn verify -DproxyHost=proxy.com -DproxyPort=8080 -DproxyUser=user -DproxyPassword=XXXX
```
