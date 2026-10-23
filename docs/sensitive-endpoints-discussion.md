# Ticket: Identify and Protect Sensitive Endpoints (Consumed by External Apps, e.g. external-app-name)

## Problem

The `study-server` endpoints used by other applications (e.g. external-app-name) are not identified
in the code. They can therefore be modified without warning, which may break these apps in production.

Requirements:
1. Tag certain endpoints in the code as sensitive (to be modified with caution).
2. Add a check (CI / integration test / unit test) that warns or fails when one of these endpoints
   changes its contract.

## Options Considered

### A. Initial Idea: `reference.json` (Fixed, Versioned) vs `current.json` (Generated on Each Build)
- Unit-test comparison: `reference.json` must remain a **subset** of `current.json`.
- Generate `reference.json` initially through a Maven command (possibly a dedicated plugin).
- The developer is warned by a local build failure.
- Complement planned in another repository: integration tests (out of scope here).

**Points discussed:**
- No custom Maven plugin is needed to generate `current.json`: `springdoc-openapi` already exposes
  `/v3/api-docs` at runtime, so it can be retrieved through a Spring test (`MockMvc`) and written to disk.
- "reference is a subset of current" should be implemented as a **filtered subset** (only tagged
  routes are checked), not as a complete diff of the OpenAPI file (otherwise any unrelated new route
  would fail the test).
- A naive JSON diff is fragile (key order, descriptive fields changing without functional impact).
  A dedicated OpenAPI diff tool (e.g. `oasdiff`) could distinguish a *breaking* change from a
  *non-breaking* change. The user's chosen alternative was a **simple custom comparator**, limited
  to a relevant subset of fields (path, method, params, status codes, response schema) rather than
  an external tool.

### B. Spring Cloud Contract
- Requires consumer collaboration (external-app-name) to write and maintain the contracts (CDC), a
  stub-runner, a stub artifact repository, and additional CI infrastructure.
- Without consumer collaboration, this becomes "provider-only contract testing", which loses
  the tool's main benefit.
- Considered too heavyweight for the stated need (a simple alert when the contract changes).

### C. Tagging in the Code
The repository already has a custom annotation + AOP aspect pattern (`PostCompletion` /
`PostCompletionAnnotationAspect` in `org.gridsuite.study.server.utils.annotations`).

Two mechanisms were considered and retained **together**:
1. New custom Java annotation (e.g. `@ExternalContract(consumers = {"external-app-name"})`) placed on
   controller methods.
2. The annotation also feeds the generated OpenAPI document (an `x-external-consumers` extension),
   making the information visible in swagger-ui as well as in the source code.

### D. Alternative Mentioned: ArchUnit
- Not currently present in `pom.xml`.
- Would make it possible to express structural rules (stable HTTP signature for annotated methods)
  without going through OpenAPI generation.
- Less precise than a response-schema diff (for example, it would not detect removal of a field from
  a response DTO).
- Not selected for now; a fallback option if the custom comparator proves insufficient.

## Decisions Made for the Next Steps

| Topic | Choice |
|---|---|
| Tagging mechanism | Custom annotation **and** OpenAPI extension (both; the annotation generates the extension) |
| `current.json` generation | Spring test (`MockMvc` on `/v3/api-docs`) run on every build; no custom Maven plugin |
| Diff tool | Simple custom JSON comparator, limited to a relevant subset of fields (path, method, params, response schema) |
| Initial scope | Implement the **mechanism** first; actual tagging of endpoints used by external-app-name will be done in a later step/ticket |

## Out of Scope (Confirmed by the User)
- Integration tests in another repository (e.g. the contracts/external-app-name repository): handled separately,
  not in this repository.

## Next Step
Propose a detailed work plan for `study-server` to implement:
1. The `@ExternalContract` annotation (+ aspect/processor that injects it into the generated OpenAPI document through springdoc).
2. The test that generates `current.json` from `/v3/api-docs`.
3. The custom comparator that extracts the subset of tagged routes from `reference.json` and verifies
   that they are still present and unchanged (path, method, params, response schema) in `current.json`.
4. The command/process for (re)generating `reference.json` when a sensitive change is intentionally desired.
