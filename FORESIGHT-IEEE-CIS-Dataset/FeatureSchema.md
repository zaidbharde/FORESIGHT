# FORESIGHT Model V2 Canonical Feature Schema

**Contract ID:** `foresight.transaction_risk_features`  
**Schema version:** `2.0.0`  
**Status:** implemented infrastructure contract; not connected to Model V1 scoring  
**Time standard:** UTC  
**Distance unit:** kilometres

## 1. Contract authority

This document is the canonical semantic specification for the six Model V2 business features. Its machine-readable single source of truth is `shared/contracts/feature_schema_v2.json`; its SHA-256 schema hash is verified whenever it is loaded. Future Android bindings, the backend feature assembler, the offline dataset builder, and `PredictionService` must generate bindings or validation from that artifact; none may maintain an independent feature list.

The canonical ordered model vector is:

```text
trusted_contact
trusted_contact_age
device_change_flag
location_anomaly
location_distance
known_device
```

Identifiers and raw signals such as `user_id`, `receiver_id`, `event_ts`, device fingerprint, latitude, and longitude are join inputs. They are not members of this model vector.

## 2. Canonical feature definitions

| Feature name | Description | Logical / storage type | Source | Training source | Inference source | Encoding | Default | Validation rules | Example |
|---|---|---|---|---|---|---|---|---|---|
| `trusted_contact` | Whether the receiver was in the sender's active trusted-contact set immediately before the transaction. | Boolean / `int8` | `TrustedContactEvent` state, with `TrustedContact` as the current-state projection | Point-in-time event replay for `(user_id, receiver_id)` using events strictly before the feature cutoff | Authoritative `TrustedContact` lookup for `(user_id, receiver_id)` at the scoring cutoff | `false -> 0`, `true -> 1` | `0` only for a confirmed absence or a new user with an empty authoritative set; source failure is an error, not a default | Required; integer in `{0,1}`; event state must be active for `1`; never trust a client-supplied flag as authority | `1` |
| `trusted_contact_age` | Whole and fractional days since the start of the current uninterrupted trusted-contact period. | Non-negative number / `float32` | Most recent unmatched `ADDED` event for the active relationship | `(cutoff_ts - active_since_ts) / 86400`, using only pre-cutoff events | Same formula using `TrustedContact.active_since_ts` and the scoring cutoff | Continuous days, no rounding; `float32` at vector boundary | `0.0` when `trusted_contact=0` | Required; finite; `>= 0`; must equal `0.0` when not trusted; cannot exceed account age within tolerance | `12.5` |
| `device_change_flag` | Whether the current device is newly observed for an established user's payment history. This is the canonical feature currently carried by the Android/API signal `new_device`. | Boolean / `int8` | Current stable device fingerprint plus `DeviceHistory` | Current fingerprint absent from pre-cutoff history **and** at least one prior eligible device exists | Same point-in-time existence checks at scoring cutoff | `false -> 0`, `true -> 1`; wire alias `new_device -> device_change_flag` | `0` for user cold start (no prior eligible device); source failure is an error | Required; integer in `{0,1}`; if prior-device count is zero then `0`; if `1`, `known_device` must be `0`; fingerprint must be computed by an approved version | `1` |
| `location_anomaly` | Whether the current valid location is farther than the versioned distance threshold from every eligible historical location in the lookback window. | Boolean / `int8` | Current location, `LocationHistory`, and versioned location policy | Threshold `location_distance` using the identical policy and pre-cutoff history | Threshold `location_distance` using the identical policy and scoring-time history | `false -> 0`, `true -> 1` | `0` when no reliable current location or insufficient baseline exists; availability is recorded in snapshot provenance | Required; integer in `{0,1}`; must be `0` when `location_distance=-1.0`; otherwise must equal policy comparison exactly | `1` |
| `location_distance` | Haversine distance in kilometres from the current valid location to the nearest eligible prior location in the lookback window. | Number / `float32` | Current location plus `LocationHistory` | Minimum distance to eligible observations strictly before cutoff | Same computation against observations available at scoring cutoff | Continuous kilometres; Haversine/WGS84; Earth radius `6371.0088 km` | `-1.0` sentinel when current location is unusable or no eligible history exists | Required; finite; either exactly `-1.0` or `>=0`; must be `-1.0` when baseline is insufficient; reject coordinates outside latitude `[-90,90]` or longitude `[-180,180]` | `37.42` |
| `known_device` | Whether the current stable device fingerprint has appeared in an eligible prior successful payment for this user. | Boolean / `int8` | Current stable device fingerprint plus `DeviceHistory` | Existence of same fingerprint strictly before cutoff | Existence of same fingerprint at scoring cutoff | `false -> 0`, `true -> 1` | `0` when fingerprint is unavailable or there is no prior match; availability is recorded in snapshot provenance | Required; integer in `{0,1}`; `known_device=1` implies `device_change_flag=0`; fingerprint version must match stored history | `1` |

Defaults are deterministic missing-context policies, not permission to hide outages. A failed authoritative lookup, malformed payload, stale store, or contract mismatch must fail feature construction and must not silently produce the default.

## 3. Exact semantic rules

### 3.1 Feature cutoff and event ordering

- `cutoff_ts` is the transaction decision timestamp supplied by the trusted backend clock.
- Offline joins may use only records with `event_ts < cutoff_ts` and `ingested_at <= dataset_build_watermark`.
- Online lookups use committed state visible immediately before snapshot creation.
- Equal timestamps are resolved by monotonic `event_sequence`; an event with the transaction's sequence or a later sequence is excluded.
- State updates caused by the current payment occur only after the `FeatureSnapshot` is written. This prevents target and future leakage.

### 3.2 Trusted-contact state machine

- `ADDED` or `RESTORED` starts a new active period.
- `VERIFIED` refreshes verification metadata but does not reset age.
- `REMOVED` or `BLOCKED` ends the active period.
- Duplicate transitions are idempotent by `event_id`; invalid transitions are quarantined.

### 3.3 Device eligibility

- A stable, pseudonymous fingerprint is produced by a versioned backend-approved algorithm; raw hardware identifiers are not model features.
- Only prior successful, non-reversed payments with an accepted fingerprint version establish device history.
- For a user with at least one prior eligible device: unseen fingerprint means `device_change_flag=1`; previously seen fingerprint means `known_device=1` and `device_change_flag=0`.
- With no eligible prior device, both flags are `0` (cold start). With an unavailable current fingerprint, both are `0` and provenance records `DEVICE_UNAVAILABLE`.

### 3.4 Location eligibility and anomaly policy

- Include only consented observations with valid coordinates, approved source, `accuracy_m <= 1000`, and age within the policy lookback.
- Exclude the current transaction and observations created from failed, reversed, test, or synthetic transactions.
- Baseline minimum: three eligible prior observations in the previous 90 days.
- `location_distance = min(haversine(current, historical_i))` when the baseline exists; otherwise `-1.0`.
- Initial policy: `location_anomaly=1` when `location_distance > 50.0 km`; otherwise `0`. Both the `90-day`, `3-observation`, `1000 m`, and `50 km` values belong to a versioned `location_policy_version`, not scattered constants.

## 4. Cross-system field contract

| Consumer | Contract responsibility |
|---|---|
| Android | Sends raw identifiers/signals already permitted by the payment contract. Existing `new_device` is a transport alias for `device_change_flag`; it is not an additional feature. Android does not authoritatively calculate trusted-contact or location-history state. |
| Backend | Validates raw signal shape, resolves identities, invokes the shared feature transformer, and rejects unavailable authoritative sources. |
| Training pipeline | Imports ordered names, types, defaults, validation, policy versions, and transformer version from the same artifact; performs point-in-time reconstruction. |
| `PredictionService` | Accepts only a validated canonical vector in exact order. Existing internal name `is_trusted_contact` must be treated as a legacy adapter, never as a second schema name. |

The machine-readable artifact contains `contract_id`, `schema_version`, ordered and individually versioned `features`, logical and physical types, encodings, defaults, constraints, declarative cross-field rules, aliases, `transformer_version`, `device_fingerprint_version`, `location_policy`, and a SHA-256 `schema_hash` computed from canonical JSON excluding the hash field itself.

## 5. Versioning and enforcement

- PATCH: wording or metadata that cannot alter a vector.
- MINOR: backward-compatible optional metadata or alias.
- MAJOR: name, order, type, default, meaning, formula, source, or policy change.
- Every dataset, feature snapshot, and model package records the schema hash and all transformer/policy versions.
- Dataset publication and inference are blocked unless names, order, types, versions, and schema hash match exactly.
- Model V1 remains on its existing contract. This draft does not change its request schema, endpoint, or model artifact.
