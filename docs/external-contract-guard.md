# Sensitive Endpoints for External Applications (e.g. external-app-name)

## Context

Some `study-server` endpoints are used by applications that are not part of this
repository (e.g. external-app-name). A "silent" change to one of these endpoints (path, method,
parameters, request/response shape) can break these applications in production without
being noticed during code review.

See `docs/sensitive-endpoints-discussion.md` for the complete discussion of the options considered.

## 1. Tag a Sensitive Endpoint

Add the `@ExternalContract` annotation to the relevant controller method:

```java
import org.gridsuite.study.server.utils.annotations.ExternalContract;

@GetMapping("")
@Operation(summary = "Get workspaces metadata")
@ExternalContract(consumers = {"external-app-name"}, note = "external-app-name ticket / contact")
public ResponseEntity<String> getWorkspaces(@PathVariable UUID studyUuid) {
    ...
}
```

- `consumers`: list of known external applications that depend on this endpoint.
- `note` (optional): useful context (ticket, contact, migration plan) for whoever will
  modify this endpoint later.

This annotation is automatically exposed in the generated OpenAPI document (the
`x-external-consumers` extension; see `StudySwaggerConfig`), making it visible in
swagger-ui as well as in the source code.

## 2. Automatic Guardrail (Unit Test)

`ExternalContractTest` (a full-context Spring Boot test):
1. Retrieves the current OpenAPI document through `/v3/api-docs`.
2. Extracts, for each endpoint annotated with `@ExternalContract`, a contract
   "snapshot": path, method, consumers, parameters, and the shape (type + top-level
   properties) of requests/responses.
3. Compares this current snapshot with the **committed** file
   `src/test/resources/external-contract/reference.json`.

`reference.json` is treated as a **subset**: only the endpoints it references are
checked (an untagged endpoint or one absent from `reference.json` is never blocking).
The diff is deliberately shallow: a changed description, an example, or a field added
to a nested schema does not fail the test. However, removal of an endpoint, removal or
renaming of a parameter, or removal of a top-level field from the request/response body
does fail the test.

This test runs with the usual test suite (`mvn test`), so the developer is warned during
the local build.

## 3. Intentionally Modify a Sensitive Endpoint

If the change is intentional and coordinated with the consumer(s):

1. Make the code change.
2. Regenerate `reference.json`:
   ```shell
   mvn test -Dtest=ExternalContractReferenceGenerator -DgenerateExternalContractReference=true -Dsurefire.failIfNoSpecifiedTests=false
   ```
3. Carefully review the Git diff of the generated `reference.json`: it must reflect exactly
   the intended change and nothing else.
4. Commit it with the code so that the PR review explicitly documents the impact on
   external consumers.

## Known Limitations

- Shallow comparison (one level deep) of request/response schemas: a field added or removed
  deep inside a nested DTO is not detected. Further strengthening (recursive comparison,
  or switching to a dedicated OpenAPI diff tool such as `oasdiff`) remains possible if needed.
- This mechanism covers only `study-server`. Consumer-side integration tests (external-app-name) are
  being considered separately in another repository.
