# FORESIGHT Sprint ETA - Dataset Engineering Plan

## 1. Scope and outcome

This sprint defines the data foundation for Model V2. It does not train a model, generate synthetic values, modify Android/payment behavior, or change FastAPI endpoints.

The IEEE-CIS source can support the existing `device_change_flag` derivation, but it contains no authoritative `trusted_contact` or `location_anomaly` history. Those features must remain unavailable for Model V2 training until real, consented application events and outcomes have been collected. Zero-filling those absent historical columns and presenting them as observations is prohibited.

## 2. Shared feature contract

`FeatureSchema.md` is the normative semantic specification. It is serialized as `shared/contracts/feature_schema_v2.json`, the only machine-readable feature contract for future Android bindings, backend validation, the shared feature transformer, the training dataset builder, and `PredictionService`.

The implemented `backend/feature_infrastructure` package owns all six formulas, strict validation, immutable domain models, snapshot serialization, and verification. Online and offline adapters will call it against point-in-time state. Source retrieval differs, feature semantics do not. Sprint ETA-2 deliberately does not connect this package to the existing prediction endpoint.

Required identifiers for construction, outside the model vector, are `transaction_id`, `user_id`, `receiver_id`, trusted backend `cutoff_ts`, current device fingerprint plus fingerprint version, and optional consented location plus accuracy/source.

## 3. Production storage models

All timestamps are UTC `TIMESTAMPTZ`; IDs are UUID/UUIDv7; identity values are opaque internal IDs. Mutable projections never replace immutable history.

### 3.1 `TrustedContact`

Current-state projection for low-latency reads. Event history remains authoritative.

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | UUID | Sender; not null |
| `receiver_id` | UUID | Canonical payee; not null |
| `status` | ENUM | `ACTIVE`, `REMOVED`, `BLOCKED`; not null |
| `active_since_ts` | TIMESTAMPTZ nullable | Required only when active |
| `last_verified_ts` | TIMESTAMPTZ nullable | Most recent verification |
| `last_event_id` | UUID | Event through which projection is built; unique |
| `version` | BIGINT | Optimistic-lock/state sequence; positive |
| `created_at`, `updated_at` | TIMESTAMPTZ | Audit timestamps |

Unique `(user_id, receiver_id)`. Check active status iff `active_since_ts` is non-null. Index `(user_id, status, receiver_id)`. Updates are transactional with insertion of the corresponding event and idempotent by event ID.

### 3.2 `TrustedContactEvent`

Append-only source for point-in-time reconstruction.

| Column | Type | Constraints / purpose |
|---|---|---|
| `event_id` | UUID | Primary key and idempotency key |
| `user_id`, `receiver_id` | UUID | Relationship key; not null |
| `event_type` | ENUM | `ADDED`, `RESTORED`, `VERIFIED`, `REMOVED`, `BLOCKED` |
| `event_ts` | TIMESTAMPTZ | Business event time; not null |
| `event_sequence` | BIGINT | Monotonic tie-breaker per relationship |
| `actor_type` | ENUM | `USER`, `SYSTEM`, `ADMIN` |
| `source` | VARCHAR(32) | Versioned producer identifier |
| `reason_code` | VARCHAR(64) nullable | Controlled reason, no free-form PII |
| `ingested_at` | TIMESTAMPTZ | Warehouse availability time |

Unique `(user_id, receiver_id, event_sequence)`. Index `(user_id, receiver_id, event_ts, event_sequence)`. Deny updates/deletes except governed retention operations; preserve late events and rebuild affected projections/datasets.

### 3.3 `DeviceHistory`

One row per user and pseudonymous device fingerprint; supports point-in-time device membership.

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | UUID | Not null |
| `fingerprint_hash` | CHAR(64) | Keyed/pseudonymous hash; never raw hardware ID |
| `fingerprint_version` | VARCHAR(32) | Algorithm/key generation version |
| `first_seen_ts`, `last_seen_ts` | TIMESTAMPTZ | Eligible successful-payment bounds |
| `first_seen_transaction_id`, `last_seen_transaction_id` | UUID | Lineage |
| `successful_payment_count` | BIGINT | Non-negative |
| `revoked_at` | TIMESTAMPTZ nullable | Compromise/revocation state |
| `created_at`, `updated_at` | TIMESTAMPTZ | Audit timestamps |

Unique `(user_id, fingerprint_hash, fingerprint_version)`. Checks `first_seen_ts <= last_seen_ts`, count `>=1`. Index `(user_id, last_seen_ts DESC)`. For exact historical rebuilds, the transaction/device event stream is retained append-only; this table is its online projection.

### 3.4 `LocationHistory`

Append-only eligible location observations. Access is restricted and retention is policy-controlled.

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id`, `transaction_id` | UUID | Not null; transaction ID unique |
| `event_ts`, `event_sequence` | TIMESTAMPTZ, BIGINT | Point-in-time ordering |
| `latitude`, `longitude` | DECIMAL(9,6) | Encrypted at rest; valid coordinate ranges |
| `accuracy_m` | REAL | Finite and `>=0` |
| `source` | ENUM | `GPS`, `NETWORK`; approved sources only |
| `consent_version` | VARCHAR(32) | Collection authority |
| `eligibility_status` | ENUM | `ELIGIBLE`, `INACCURATE`, `REVOKED`, `TEST`, `REVERSED` |
| `ingested_at`, `created_at` | TIMESTAMPTZ | Availability and audit time |

Index `(user_id, event_ts DESC, event_sequence DESC)` filtered to eligible rows. Apply least-privilege access, encryption, deletion/retention policy, and regional privacy review before collection.

### 3.5 `FeatureSnapshot`

Immutable record of what scoring saw; enables audit, replay, and train/serve parity checks.

| Column | Type | Constraints / purpose |
|---|---|---|
| `snapshot_id` | UUID | Primary key |
| `transaction_id` | UUID | Unique idempotency key |
| `user_id` | UUID | Audit/partition key |
| `cutoff_ts`, `computed_at` | TIMESTAMPTZ | Decision and computation times |
| `schema_version`, `schema_hash` | VARCHAR | Exact contract identity |
| `transformer_version` | VARCHAR | Shared implementation identity |
| `device_fingerprint_version`, `location_policy_version` | VARCHAR | Policy lineage |
| `features` | JSONB | Canonical ordered keys and typed values |
| `provenance` | JSONB | Source event IDs/watermarks, availability codes, counts; no raw coordinates |
| `vector_hash` | CHAR(64) | SHA-256 of canonical typed vector |
| `model_version` | VARCHAR nullable | Null before a V2 model is deployed |
| `created_at` | TIMESTAMPTZ | Immutable audit timestamp |

Indexes `(user_id, cutoff_ts DESC)`, `(schema_hash)`, and unique `(transaction_id)`. JSON schema checks enforce exactly the six keys until typed feature columns are introduced. Snapshots are never mutated after scoring.

## 4. Feature production pipelines

### 4.1 Trusted contact

```text
(user_id, receiver_id, cutoff_ts)
  -> TrustedContactEvent events strictly before cutoff
  -> replay deterministic state machine
  -> active state + start of current active interval
  -> trusted_contact
  -> trusted_contact_age
```

Online reads the transactionally maintained `TrustedContact` projection. Offline replay is the reference implementation. A scheduled parity job compares projection results with replay.

### 4.2 Device

```text
(user_id, current fingerprint/version, cutoff_ts)
  -> eligible DeviceHistory membership strictly before cutoff
  -> count prior eligible devices and test same-fingerprint membership
  -> known_device
  -> device_change_flag
```

Existing Android/API `new_device` maps to canonical `device_change_flag`; it does not create a seventh feature. The authoritative value is rebuilt from history. The current successful payment updates device history only after the snapshot, preventing self-matches.

### 4.3 Location

```text
(user_id, current consented coordinates/accuracy/source, cutoff_ts)
  -> validate current observation
  -> eligible LocationHistory strictly before cutoff and inside 90-day lookback
  -> require at least 3 prior observations
  -> Haversine distance to each prior observation
  -> minimum distance in km
  -> location_distance
  -> compare with versioned 50 km threshold
  -> location_anomaly
```

If current location is unusable or history is insufficient, emit `location_distance=-1.0`, `location_anomaly=0`, and record the reason in snapshot provenance. Never invent coordinates or distances.

## 5. Future training dataset generation

### 5.1 Collection readiness gate

Training-dataset generation starts only after all real event producers are deployed, privacy approval and retention are in force, feature source coverage meets an approved threshold, label maturity has elapsed, and clocks/identities are validated. IEEE-CIS-only rows cannot populate trusted-contact or location features and must not be patched with random or constant synthetic observations.

### 5.2 Deterministic build

1. Freeze a dataset specification containing contract hash, transformer and policy versions, source snapshot/watermark IDs, label definition, label-maturity window, and inclusion dates.
2. Select immutable transaction examples with stable `transaction_id`, resolved identities, backend decision timestamp, and matured outcome label.
3. For each transaction, reconstruct all source state strictly before its cutoff. Apply both event-time and ingestion-watermark constraints to prevent future and late-arrival leakage.
4. Run the same shared pure transformer used online. No SQL-only reinterpretation of feature formulas is allowed.
5. Emit identifiers and label in metadata; emit model columns exactly in canonical order and physical type.
6. Validate row invariants, schema hash, temporal leakage, coverage, distribution, and label joins. Quarantine failures; do not coerce them silently.
7. Write an immutable, partitioned Parquet dataset plus manifest. The manifest includes row count, date range, ordered columns/types, null/default rates, source versions, hashes, code commit, policy versions, and build timestamp.
8. Rebuild a sample of historical `FeatureSnapshot` rows offline and require byte-equivalent typed feature vectors before publishing.
9. Register the dataset as a versioned candidate. Model training is a separate, explicitly approved sprint.

### 5.3 Parity guarantee

Training features equal inference features because both paths use the same contract hash, transformation package, cutoff semantics, source eligibility rules, default policy, fingerprint version, and location policy. CI golden fixtures run through online and offline adapters and compare names, order, types, values, availability reasons, and vector hashes. Any difference blocks dataset publication or scoring startup.

## 6. Validation plan

| Feature | Correctness tests | Missing/quality detection | Schema/parity checks |
|---|---|---|---|
| `trusted_contact` | State-transition table covering add, remove, restore, block, duplicate, late, and equal-time events; projection equals replay | Lookup failures separated from confirmed absence; event/projection lag and orphan identities monitored | Exact `int8`, `{0,1}`; online value equals offline replay for sampled snapshots |
| `trusted_contact_age` | Fixed UTC timestamps, fractional days, restore resets age, verify does not, non-trusted gives zero | Missing `active_since_ts` on active row is invalid; negative/future timestamps quarantined | `float32`, finite, `>=0`; invariant with contact flag and account age |
| `known_device` | First user device, repeated device, return to old device, revoked device, fingerprint-version migration | Missing fingerprint and store outage have distinct reason codes; hash/version/count completeness monitored | `int8`, `{0,1}`; if `1`, change flag is `0`; online/offline membership matches |
| `device_change_flag` | Cold start, unseen device after established history, repeated device, concurrent transactions, current-row exclusion | Missing current fingerprint emits policy default plus provenance; outage fails closed at feature construction | `int8`, `{0,1}`; Android `new_device` adapter maps once; canonical name only downstream |
| `location_distance` | Haversine fixtures for identical points, known city pairs, antimeridian, poles; nearest-of-many; boundary of lookback/minimum count | Invalid/out-of-range coordinates rejected; accuracy, consent, baseline count, sentinel rate monitored | `float32`; exactly `-1` or non-negative finite; same history IDs and policy online/offline |
| `location_anomaly` | Values below, at, and above `50 km`; insufficient baseline; policy-version fixtures | Must be `0` with sentinel distance; threshold and baseline version always present in provenance | `int8`, `{0,1}`; recompute from distance and policy and demand equality |

Dataset-wide gates:

- exact six feature names, order, logical/physical types, schema version, and SHA-256 schema hash;
- no null, NaN, infinity, duplicate transaction IDs, future source event, label leakage, or unapproved source;
- referential integrity for user, receiver, transaction, event lineage, and consent version;
- per-day source coverage, default/sentinel rates, categorical rates, quantiles, and drift against the previous accepted dataset;
- online snapshot freshness and source-store latency service-level objectives;
- contract tests in Android, backend, training, and `PredictionService`; startup/build failure on incompatible major version.

## 7. Delivery sequence and ownership

1. Data/ML platform: approve the schema and serialize the machine-readable contract.
2. Backend/data engineering: implement append-only events, online projections, shared transformer, snapshot writes, and replay jobs without changing current endpoints during this sprint.
3. Android/backend owners: define raw signal collection and consent boundaries; generate contract bindings when implementation is approved.
4. ML engineering: implement the point-in-time dataset builder, manifests, validation suite, and parity harness.
5. Security/privacy: approve identifier hashing, location access, consent, retention, deletion, and audit controls.
6. Model governance: approve readiness thresholds, label definition/maturity, and dataset version before any V2 training.

## 8. Explicitly deferred

- collection/API migrations and any Android or payment-flow change;
- database migrations, persistence repositories, and online/offline source adapters;
- production threshold calibration (the initial location policy is a versioned baseline requiring approval);
- collection of real trusted-contact/location history and matured fraud labels;
- Model V2 dataset materialization, training, evaluation, registration, and deployment.
