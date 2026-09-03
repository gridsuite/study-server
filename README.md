# Study Server

[![Actions Status](https://github.com/gridsuite/study-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/study-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Astudy-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Astudy-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

## Description

The **study-server** is the central orchestration microservice of the [GridSuite](https://github.com/gridsuite) platform. It manages **power system network studies**: each study holds one or more **root networks** (electrical network datasets loaded from cases) organized in a **network modification tree** — a branching tree of nodes where each node accumulates network modifications and stores computation results. Different branches represent different sets of hypotheses applied to the base network.

The **study-server** is closely linked to [gridstudy-app](https://github.com/gridsuite/gridstudy-app). It is the main entry point requested by the grid study frontend: most user actions in gridstudy-app go through study-server, which dispatches and orchestrates the corresponding requests to the specific downstream microservices.

It provides the following capabilities:

- **Study lifecycle:** create, duplicate, delete, search, and index studies.
- **Multi-root network support:** a study can hold up to 4 root networks. Each node in the modification tree is evaluated independently per root network — meaning the same node can be `BUILT` for one root network and `NOT_BUILT` for another.
- **Network modification tree:** create, move (cut/paste), duplicate, stash, and restore nodes and subtrees. Two node types: `ROOT` and `NETWORK_MODIFICATION`.
- **Node building:** apply accumulated modifications on a node to produce a concrete network variant. Build statuses (`BUILT`, `NOT_BUILT`, `BUILT_WITH_WARNING`, `BUILT_WITH_ERROR`) are tracked per (node, root network) pair.
- **12 computation types orchestrated:** Load Flow, Security Analysis, Sensitivity Analysis, Short Circuit Analysis (all-buses and one-bus), Voltage Initialization, Dynamic Simulation, Dynamic Security Analysis, Dynamic Margin Calculation, State Estimation, PCC Min, Asymmetrical Load.
- **Network visualization:** SLD (Single Line Diagram), NAD (Network Area Diagram), substation diagrams, map data (geo data, voltage levels, lines, substations, branches).
- **Equipment search:** full-text search over indexed equipment data via Elasticsearch.
- **Network export:** export the network in various formats (CGMES, UCTE, etc.) with optional upload to GridExplore.
- **Report aggregation:** retrieve hierarchical computation and modification logs from report-server.
- **Workspace management:** manage workspaces and panels within a study (UI layout persistence) including NAD config per panel.
- **Operation quotas:** optional per-user quota enforcement for expensive computations.

---

## Technical Stack

- Spring Boot (Web, Data JPA, Actuator, Cloud Stream)
- PostgreSQL + Liquibase
- Elasticsearch (Spring Data Elasticsearch) — equipment index and study index
- RabbitMQ via Spring Cloud Stream
- PowSyBl (network store client, loadflow, security analysis, sensitivity analysis, shortcircuit APIs)
- API documentation: OpenAPI / Swagger (`springdoc`)
- Micrometer / Prometheus

---

## Development Scripts

Build Docker image

```shell
mvn install -DskipTests -Dpowsybl.docker.install
```

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets. After you generated a changeset do not forget to add it to git and in `src/main/resources/db/changelog/db.changelog-master.yaml`.

The old way to automatically generate the SQL schema file (directly using Hibernate) can still be used for debugging:

```shell
mvn package -DskipTests && rm -f src/main/resources/study.sql && java -jar target/gridsuite-study-server-2.39.0-SNAPSHOT-exec.jar --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create
```

---

## Interactions with Other Microservices

```
┌──────────────────────┐
│    study-server      │──► case-server                     (import, persist, duplicate cases)
│                      │──► network-store-server            (check network existence via powsybl-network-store-client)
│                      │──► network-modification-server     (create/update/delete/build modifications)
│                      │──► loadflow-server                 (run/stop load flow, manage parameters)
│                      │──► security-analysis-server        (run/stop SA, manage parameters)
│                      │──► sensitivity-analysis-server     (run/stop, manage parameters)
│                      │──► shortcircuit-server             (run/stop all-buses & one-bus, manage parameters)
│                      │──► voltage-init-server             (run/stop, get modifications, manage parameters)
│                      │──► dynamic-simulation-server       (run/stop, get timeseries, manage parameters)
│                      │──► dynamic-security-analysis-server(run/stop, manage parameters)
│                      │──► dynamic-margin-calculation-server(run/stop, manage parameters)
│                      │──► state-estimation-server         (run/stop, manage parameters)
│                      │──► pcc-min-server                  (run/stop, manage parameters)
│                      │──► actions-server                  (get contingency count, export contingency lists)
│                      │──► network-map-server              (get map data, equipment infos)
│                      │──► geo-data-server                 (get line and substation graphics)
│                      │──► single-line-diagram-server      (generate SLD/NAD SVGs)
│                      │──► network-conversion-server       (export network in various formats)
│                      │──► report-server                   (get logs, severities, delete reports)
│                      │──► filter-server                   (evaluate global filters on equipment)
│                      │──► study-config-server             (network viz params, spreadsheet configs, workspaces)
│                      │──► directory-server                (register exported network as a new element)
│                      │──► user-admin-server               (user profile, operation quotas)
│                      │──► timeseries-server                (dynamic simulation timeseries storage)
│                      │──► dynamic-mapping-server           (dynamic simulation mappings)
└──────────────────────┘
         ▲  ▼
      RabbitMQ (publish: study.update / element.update)
      RabbitMQ (consume: results & events from all computation services)
```

---

## Asynchronous Execution Flow

Study-server orchestrates computations asynchronously using RabbitMQ:

1. The controller delegates to the relevant computation service (`LoadFlowService`, `SecurityAnalysisService`, etc.) which sends a `run` message to the computation server with an encoded `receiver` header containing `{nodeUuid, rootNetworkUuid}`.
2. The computation server processes the request and publishes a result/stop/fail message echoing the `receiver` header.
3. `ConsumerService` receives the result message, routes it to the correct (study, node, root network) using the `receiver`, stores the result UUID in `RootNetworkNodeInfoEntity`, and triggers a `study.update` notification to the frontend.

Dead-letter queues (`*.run.dlx`) and quorum queues ensure reliability in case of computation failure.

---

## Notification Events

#### Published (OUT):

| Binding | Destination | Description |
|---|---|---|
| `publishStudyUpdate-out-0` | `study.update` | All study-level notifications to the frontend: node changes, computation status, build status, study creation, indexation status, parameter changes, workspace changes. |
| `publishElementUpdate-out-0` | `element.update` | Notify the directory/explorer that an element has been modified. |

#### Consumed (IN):

| Queue | Description |
|---|---|
| `build.*` | Node build completed / stopped / failed |
| `case.import.*` | Case import completed / failed (triggers study or root network creation) |
| `network.export.finished` | Network export to S3 finished |
| `loadflow.*` | Load flow events |
| `sa.*` | Security analysis events |
| `sensitivityanalysis.*` | Sensitivity analysis events |
| `shortcircuitanalysis.*` | Short circuit analysis events (dispatched to ALL_BUSES or ONE_BUS based on `busId` header) |
| `voltageinit.*` | Voltage initialization events |
| `ds.*` | Dynamic simulation events |
| `dsa.*` | Dynamic security analysis events |
| `dmc.*` | Dynamic margin calculation events |
| `stateestimation.*` | State estimation events |
| `pccmin.*` | PCC Min events |

---

## Result Data

Computation results are **not stored** in study-server. Each computation service stores its own results; study-server only retains the **result UUID** per (node, root network) pair in `RootNetworkNodeInfoEntity`. Results are fetched on demand from the relevant computation server.


---

## Database Schema

PostgreSQL database managed by Liquibase. Main entities:

| Table | Description |
|---|---|
| `study` | Root entity. Holds UUIDs for all computation parameter sets, network viz params, spreadsheet collection, workspaces config, node aliases, and a `monoRoot` boolean. |
| `rootNetwork` | One root network per study entry. Holds `networkUuid`, `caseUuid`, `caseName`, `caseFormat`, `reportUuid`, import parameters, `name`, `tag`, and `indexationStatus`. |
| `node` | One row per node in the modification tree. Stores parent link (self-referential), `type` (`ROOT` or `NETWORK_MODIFICATION`), `stashed` flag, and `stashDate`. |
| `networkModificationNodeInfo` | Per-node info: `modificationGroupUuid` (points to modification group in network-modification-server), `nodeType` (`CONSTRUCTION` or `SECURITY`), `name`, `description`, `columnPosition`. |
| `rootNetworkNodeInfo` | Cross-product (node × root network). Stores `variantId`, build statuses (`localBuildStatus`, `globalBuildStatus`), all 12 computation result UUIDs, computation and modification report UUID maps, and excluded modification UUIDs per root network. |
| `studyCreationRequest` | Tracks in-progress study creation requests. |
| `rootNetworkRequest` | Tracks in-progress root network creation requests. |
| `event` / `eventProperty` | Dynamic simulation events attached to a node, with their name-value properties. |

**Elasticsearch indices** (managed separately):
- **Studies index** — `StudyInfos` documents (UUID, name, description) for study search.
- **Equipment index** — `EquipmentInfos` documents (id, name, type, networkUuid, variantId, substationIds, voltageLevelIds) for equipment search.
- **Tombstoned equipment index** — markers for equipment deleted in modified network variants, used to filter out stale search results.

---

## Notable Patterns

**Transaction-Aware Notifications (`@PostCompletion`):**
All RabbitMQ message publishing in `NotificationService` uses a custom `@PostCompletion` AOP annotation. The aspect defers message sends to `afterCompletion()` via Spring's `TransactionSynchronizationManager`, guaranteeing that messages are only sent once the DB transaction is committed. This avoids race conditions where the frontend polls for data before it is persisted.

**Receiver Pattern (Correlation IDs):**
Outbound computation requests embed a URL-encoded `NodeReceiver` JSON (`{nodeUuid, rootNetworkUuid}`) in the `receiver` AMQP header. The computation service echoes this header in result/stop/fail messages, allowing `ConsumerService` to route the response to the correct (study, node, root network) without any in-memory state.

**Rerun Load Flow Workflow:**
A `workflowType` header can be attached to build requests to implement multi-step workflows. The `RERUN_LOAD_FLOW` workflow automatically re-triggers a load flow once a node build completes, enabling reactive re-computation when modifications invalidate existing results.

**Node Sequences:**
`NodeSequenceType` supports predefined node sequences (e.g., `SECURITY_SEQUENCE`) — sets of pre-configured nodes created together with a single API call.
