# Langfuse and Grafana integration research

Research date: 2026-10-31. Primary-source desk research only. No dependencies were installed, no images were pulled, no services were started, and no Java-to-Langfuse or Grafana round trip was executed. A public Git tag lookup from the shell timed out; release pages and tagged source files were read through the connected GitHub tool instead. Every deployment and acceptance scenario below is **proposed and not runtime-verified**.

## Decision and released baseline

Use Langfuse **v4.72.0** for a new Java implementation. Java can emit actual experiment traces through OTLP with experiment attributes; it does not need a Python/TypeScript experiment runner. The tagged release contains the experiment attributes and the new experiment read API. Keep the native Langfuse Experiments comparison view as the user-facing comparison tool. This is supported by documentation and source inspection, not a claim that our eventual Java code has been tested. [v4.72.0 release](https://github.com/langfuse/langfuse/releases/tag/v4.72.0), [tagged experiment attributes](https://github.com/langfuse/langfuse/blob/v4.72.0/packages/shared/src/server/otel/attributes.ts), [OpenTelemetry experiments guide](https://langfuse.com/integrations/native/opentelemetry/experiments)

| Component | Concrete released baseline | Evidence / status |
|---|---|---|
| Langfuse web and worker | v4.72.0; release commit `7efeac5`; proposed image tags `langfuse/langfuse:4.72.0` and `langfuse/langfuse-worker:4.72.0` | Released Oct 29. Same version for both processes. Registry manifest, platform availability, image digests, and operation remain unverified. |
| Grafana OSS | v13.3.0; proposed image `grafana/grafana:13.3.0`; release commit `752e346` | Released Oct 27. Tagged source still documents the provisioning API and editable provenance header; API is deprecated, so freeze this baseline and track migration. |
| Prometheus | v3.15.1; proposed image `prom/prometheus:v3.15.1`; release commit `bae3ece` | Stable release Oct 21. Do not choose the listed v3.16.0 release candidates as the baseline. |

Sources: [Langfuse release](https://github.com/langfuse/langfuse/releases/tag/v4.72.0), [Grafana release](https://github.com/grafana/grafana/releases/tag/v13.3.0), [Prometheus release](https://github.com/prometheus/prometheus/releases/tag/v3.15.1), [Prometheus release list](https://github.com/prometheus/prometheus/releases). Langfuse says Docker Hub and its `docker.langfuse.com/` proxy provide identical tags and digests. Grafana now uses `grafana/grafana` for OSS; `grafana/grafana-oss` stopped receiving updates starting with 12.4.0. [Langfuse container images](https://langfuse.com/self-hosting/deployment/infrastructure/containers), [Grafana Docker documentation](https://grafana.com/docs/grafana/latest/setup-grafana/installation/docker/)

Full release commit identities resolved from official repository commit objects: Langfuse [`7efeac5eebe215b461fa0bc37369dc7f80d7752f`](https://github.com/langfuse/langfuse/commit/7efeac5eebe215b461fa0bc37369dc7f80d7752f); Grafana [`752e3462ff71a43a5c1fb62d00a482fa2dc0ee6a`](https://github.com/grafana/grafana/commit/752e3462ff71a43a5c1fb62d00a482fa2dc0ee6a); Prometheus [`bae3eced0a5369e050e83ea73bc381d7c4b1a719`](https://github.com/prometheus/prometheus/commit/bae3eced0a5369e050e83ea73bc381d7c4b1a719). Source commit pins do not substitute for image digest verification.

### Why an unpinned legacy dataset-run integration is unsafe

The current compatibility page announces the Cloud cutover on **2026-11-16**. Legacy trace/observation batch ingestion and dataset-run APIs are removed; direct score ingestion survives. Self-hosted breaking removals follow server major versions rather than that Cloud date. Existing v3 deployments can remain on v3, but current documentation gives v3 security support only through 2027-01-31. The released maintenance fallback is v3.225.15 (`50e79e3`), not an indefinite long-term choice. [Compatibility policy](https://langfuse.com/docs/compatibility), [v3.225.15 release](https://github.com/langfuse/langfuse/releases/tag/v3.225.15)

For v4, do **not** implement `POST /api/public/dataset-run-items` or use `GET /api/public/datasets/{name}/runs` as the new integration contract. There is no REST `POST /experiments` replacement: experiment creation is encoded in OTLP spans; new REST endpoints read those experiments. The official migration guide explicitly gives this route for other languages. [Migration guide](https://langfuse.com/faq/all/deprecated-api-migration), [Experiments data model](https://langfuse.com/docs/evaluation/experiments/data-model)

## Langfuse deployment footprint

Use the **v4.72.0-tagged** Compose file as the baseline, not `main`. It has six services: web, worker, PostgreSQL, ClickHouse, Redis, and MinIO. Both application services depend on healthy PostgreSQL, ClickHouse, Redis, and MinIO. Tagged dependencies include ClickHouse `25.12`, PostgreSQL `${POSTGRES_VERSION:-17}`, Redis `7`, and unpinned `cgr.dev/chainguard/minio`; these are upstream defaults, not fully locked dependency versions. Resolve exact tags/digests before shipping the final Compose artifact. [Tagged Compose](https://github.com/langfuse/langfuse/blob/v4.72.0/docker-compose.yml)

Persist PostgreSQL `/var/lib/postgresql/data`, ClickHouse `/var/lib/clickhouse` and `/var/log/clickhouse-server`, MinIO `/data`, and Redis `/data` in named volumes. Keep Langfuse event/media object storage configuration, queue credentials, database credentials, encryption key, salt, and authentication secrets consistent across web/worker. Change every upstream credential placeholder. MinIO has internal Docker and browser-reachable endpoints with different purposes. Do not expose the database ports publicly. [Tagged Compose](https://github.com/langfuse/langfuse/blob/v4.72.0/docker-compose.yml)

The official Compose guide recommends **at least 4 cores, 16 GiB RAM, and an example 100 GiB storage** for the VM setup. This is an upstream recommendation, not a measured minimum or sufficient sizing for the complete Java + Prometheus + Grafana stack. Compose is a local/single-machine evaluation setup without built-in HA or backup functionality. Named volumes survive ordinary stop/start, but `docker compose down -v` removes them. [Compose deployment guide](https://langfuse.com/self-hosting/deployment/docker-compose)

Headless initialization can create organization, project, API keys, and initial owner only when absent through `LANGFUSE_INIT_*`. Stable organization/project IDs and securely generated credentials make a repeatable local demonstration possible. The initial resources depend on the organization; this is distinct from trace ingestion authentication. [Headless initialization](https://langfuse.com/self-hosting/administration/headless-initialization)

## Java trace and experiment contract

### Transport

Use Java `OtlpHttpSpanExporter`, with the full signal endpoint `http://langfuse-web:3000/api/public/otel/v1/traces` inside Compose, or the mapped localhost URL from a host JVM. Set `Authorization: Basic BASE64(publicKey:secretKey)` and `x-langfuse-ingestion-version: 4`. Use HTTP/protobuf; Langfuse also accepts HTTP/JSON, but not OTLP gRPC. Set the full `/v1/traces` path when using the traces-specific endpoint or Java exporter builder. Flush/shutdown the provider at completion of short-lived experiment jobs. [Langfuse OTLP guide](https://langfuse.com/integrations/native/opentelemetry), [tagged OTLP contract](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/opentelemetry.yml), [Java exporter documentation](https://opentelemetry.io/docs/languages/java/sdk/#spanexporter)

The v4 ingestion header is material: without it, current documentation warns of delayed visibility (different pages describe roughly 10–15 minutes). An accepted export is not proof that experiment counts or scores are already queryable. Use bounded polling and check expected counts before reporting completion. [Compatibility/freshness](https://langfuse.com/docs/compatibility)

### Actual experiments, including native comparison

Use a Langfuse-managed dataset to give both runs the same stored dataset/item identifiers. Record application commit, model/prompt version, evaluator version, dataset version, and fixture origin in experiment metadata. A synthetic fixture or simulated model must be labelled as such. Run A and B must both execute the application task against every required test case; uploading summary numbers alone is not an experiment.

The required attribute vocabulary exists in the released source: `langfuse.experiment.id`, `.name`, `.dataset.id`, `.metadata.*`, `.item.id`, `.item.root_observation_id`, `.item.version`, `.item.expected_output`, `.description`, and `.item.metadata.*`. Input/output use `langfuse.observation.input` and `.output`. [Tagged attribute constants](https://github.com/langfuse/langfuse/blob/v4.72.0/packages/shared/src/server/otel/attributes.ts)

For each run, create one distinct trace per dataset item, with no enclosing experiment span. Propagate experiment identity to all spans through baggage copied onto span attributes; use the returned dataset ID and DatasetItem ID. The item root carries input, actual output, expected output, and `item.root_observation_id` equal to its own span ID. Propagate item identity to children. Attach scores to the root's `traceId` and `spanId`. Keep environment `experiment` separate from production. [Direct OTLP experiment guide](https://langfuse.com/integrations/native/opentelemetry/experiments)

In the native Experiments UI, select baseline and candidate runs, open Compare, and inspect aggregate scores and per-case outputs/traces. Both runs must use the same dataset version and evaluator definitions. Missing cases or missing evaluator results must fail completion checks. Selecting a baseline in the UI does not create a CI release gate. [Native comparison documentation](https://langfuse.com/docs/evaluation/experiments/compare-experiments)

### REST endpoints on v4.72.0

All paths below are relative to `/api/public`; use project Basic Auth and JSON bodies. URL-encode dataset names and query parameters.

| Operation | Endpoint and minimal contract | Tagged primary source |
|---|---|---|
| Create dataset | `POST /v2/datasets`, `{ "name": "agent-regression", "description": "...", "metadata": {...} }`; response includes dataset ID. | [datasets.yml](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/datasets.yml) |
| Read dataset | `GET /v2/datasets/{datasetName}`; use its ID in experiment spans and read filters. | [datasets.yml](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/datasets.yml) |
| Create/upsert item | `POST /dataset-items`, `{ "id": "stable-case-id", "datasetName": "agent-regression", "input": {...}, "expectedOutput": {...}, "metadata": {...} }`. IDs are unique per project, cannot be reused across datasets, max 255 characters. | [dataset-items.yml](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/dataset-items.yml) |
| Retrieve fixed test data | `GET /dataset-items?datasetName=...&version=<UTC timestamp>&page=1&limit=...`; version requires datasetName. | [dataset-items.yml](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/dataset-items.yml) |
| Write scores | `POST /scores` takes a **JSON array**, e.g. `[{"id":"stable-score-id","traceId":"...","observationId":"...","name":"correctness","value":1,"dataType":"NUMERIC","timestamp":"..."}]`. HTTP 202 means accepted asynchronously; HTTP 207 reports accepted/rejected counts. Do not blindly retry a whole 207 batch. Reuse ID **and timestamp** to update an existing score. | [scores.yml](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/scores.yml) |
| Read scores | `GET /v3/scores?traceId=...&observationId=...&fields=subject,details`; observationId requires traceId. Cursor pagination: default 50, max 100. | [scores.yml](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/scores.yml) |
| Find runs | `GET /experiments?fromStartTime=<ISO datetime>&datasetId=...&name=...&fields=core,metadata,scores`; fromStartTime required, cursor pagination default 50/max 100. | [experiments.yml](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/experiments.yml) |
| Read run cases | `GET /experiment-items?fromStartTime=...&experimentId=...&fields=core,dataset,io,scores`; fromStartTime required. `io` exposes input/output/expectedOutput; `scores` exposes case/trace scores. Follow `meta.cursor`; per-item scoreLimit is max 50. | [experiments.yml](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/experiments.yml) |

The experiments endpoint's `scores` field contains scores attached directly to the experiment, **not an aggregation of every case score**. Case scores belong in `/experiment-items`. Choose the query time window to cover the whole run: returned start time and item counts are scoped to the requested window. [Tagged experiment response definitions](https://github.com/langfuse/langfuse/blob/v4.72.0/fern/apis/server/definition/experiments.yml)

## Grafana: native alerts that users can edit and pause

File-provisioned alerting resources are not editable in the UI. Grafana applies files at startup or hot reload, and explicitly prevents UI changes that could be overwritten. Do not mount rule/contact-point/policy provisioning files when the acceptance requirement is persistent native UI editing. [Official file-provisioning documentation](https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/)

Grafana v13.3.0's tagged API documentation confirms `X-Disable-Provenance: true` on rule-group PUT and rule POST permits UI editing. Rule and group provenance must match. The API JSON schema differs from the export/file format. The same source marks this `/api/v1/provisioning` API deprecated but still documents its endpoints; plan a later migration to `/apis/rules.alerting.grafana.app/v1beta1/...` rather than assuming perpetual compatibility. [Tagged Grafana HTTP API documentation](https://github.com/grafana/grafana/blob/v13.3.0/docs/sources/shared/alerts/alerting_provisioning.md)

### Proposed one-time seed workflow

1. Wait for healthy Grafana. Use a service-account token or a local bootstrap credential; do not embed real credentials in committed seed payloads.
2. Use deterministic folder, datasource, group, and rule UIDs. Read each resource before creation. Only an actual 404 permits creation; authentication, server, or network failures abort initialization.
3. `GET /api/v1/provisioning/folder/{folderUID}/rule-groups/{group}`. If absent, create the dedicated group with `PUT` and `X-Disable-Provenance: true`. Its initial rules include `isPaused: true`. If the group exists, preserve its interval and rules; do not repeatedly PUT the whole group.
4. For an incomplete initial seed, `GET /api/v1/provisioning/alert-rules/{uid}` then `POST /api/v1/provisioning/alert-rules` only for a missing rule, with `X-Disable-Provenance: true`. Existing rule means no write. A provenance mismatch is an initialization error, not permission to rewrite somebody else's group.
5. Persist a seed-completed marker outside the container lifecycle after every initial seed resource succeeds. Subsequent restarts skip seeding. This preserves user deletions as well as edits and pause state. Explicit re-seeding is an operator action, not a startup reconciliation loop. The marker workflow is our proposal; Grafana's API provides the underlying GET/create operations.
6. **Do not POST/PUT/DELETE contact points, notification policies, templates, or mute timings during this seed.** Preserve all existing routing and destinations. Users configure native Grafana contact points/policies and explicitly unpause after reviewing thresholds. In particular, `PUT /api/v1/provisioning/policies` replaces the policy tree; do not use it as a harmless default seed.

The pinned API supports rule `uid`, `folderUID`, `ruleGroup`, `title`, `condition`, `data`, `for`, `noDataState`, `execErrState`, `isPaused`, annotations, and labels. A practical payload should use Prometheus query A plus a Grafana expression/threshold B and `condition: "B"`, not Grafana dashboard template variables. Read the created resource back to verify pause/provenance and then inspect native Alerting → Alert rules. [Tagged API contract and example](https://github.com/grafana/grafana/blob/v13.3.0/docs/sources/shared/alerts/alerting_provisioning.md)

Persist `/var/lib/grafana` in a named Docker volume for the embedded SQLite database and native settings, and persist the initializer marker separately. An ordinary container recreate must not delete these volumes. Proposed acceptance: edit a threshold, pause/unpause, change notification routing, restart/recreate containers, then confirm each survives unchanged and a deleted seed rule stays deleted. [Grafana persistence documentation](https://grafana.com/docs/grafana/latest/setup-grafana/installation/docker/)

### No data, query errors, and insufficient traffic

Grafana distinguishes successful queries with no returned points (No Data) from failed queries (Error). Both have configurable handling; the defaults can create special `DatasourceNoData`/`DatasourceError` alerts. Do not conflate missing telemetry with zero failures. [Official state semantics](https://grafana.com/docs/grafana/latest/alerting/fundamentals/alert-rule-evaluation/nodata-and-error-states/)

Proposed seed semantics (all rules still paused initially):

| Condition | Proposed behavior |
|---|---|
| Quality/latency ratio with enough cases | Evaluate threshold with an explicit minimum sample count in the same time window; start with a documented local-demo count such as 10, then calibrate. |
| Low traffic / no requests | Return a numeric false gate (0), not a filtered-away series. Suppress the quality ratio alert; show an insufficient-samples count in the dashboard. This is not proof of healthy quality. |
| Telemetry absent for quality rule | Selected platform design uses `noDataState: "NoData"`, never OK for missing telemetry; a separate missing-telemetry rule identifies scrape failure. This supersedes the alternative of suppressing NoData to OK, because the brief requires missing data to remain visibly unknown. |
| Datasource/query failure | `execErrState: "Error"`; surface the evaluation problem instead of treating it as good quality. Review routing for the generated datasource error alert before enabling notifications. |
| Scrape/export failure | Separate rule on `up` and/or `absent_over_time(required_metric[5m])`, with a sustained pending period. An idle application may have zero work; it should still expose initialized counters and health metrics. |

For counters, calculate `rate()` before aggregation; counter resets are handled by `rate()`/`increase()`. `increase()` is extrapolated and may be fractional, so a traffic threshold is an approximate window count. Use `>= bool <minimum>` when a numeric gate is needed and keep the query numeric through zero traffic (guard denominator with `clamp_min`). `absent_over_time` detects absent series, not zero-valued samples. These are proposed query-construction choices based on official PromQL semantics. [Prometheus functions](https://prometheus.io/docs/prometheus/latest/querying/functions/)

Persist the Prometheus TSDB path and configure retention explicitly for the demonstration. Local TSDB is a single-node database, not replicated storage; retention size must leave room for WAL/head and compaction. Preserve it on restart independently of Grafana's configuration database. [Prometheus storage](https://prometheus.io/docs/prometheus/latest/storage/)

## Required runtime verification before claiming compatibility

- Pull the proposed exact image tags, record digests/platforms, validate Compose, start all six Langfuse dependencies, and inspect health/migrations on the intended machine.
- Compile the selected Java OpenTelemetry dependency baseline; ingest one root and child span with auth/header; read it back through v4 APIs.
- Create a small managed dataset; execute two real Java task runs; attach root scores; wait until every required case and score is visible; select both runs in native Compare and inspect one trace.
- Verify score-array 202 and partial-207 handling; test time-bounded cursor pagination and a missing-case failure. Never label asynchronous acceptance as finished ingestion.
- Seed paused editable Grafana rules; confirm threshold edits and pause changes work in the UI. Recreate Grafana with its volumes; verify edits, routing, and user deletions survive without re-seeding.
- Exercise no traffic, below-minimum traffic, threshold breach, missing series, failed Prometheus query, and recovery. Confirm quality alarms and telemetry alarms have distinct meanings.

Until these checks are executed, the status is **source-supported design, runtime integration unverified**.
