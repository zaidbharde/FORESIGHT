# FORESIGHT Model V2 Dataset Architecture

Implemented ETA-2 components are `shared/contracts/feature_schema_v2.json` and the isolated `backend/feature_infrastructure` package. The diagrams retain future adapters, persistence, and training paths for context; no current endpoint or prediction path is connected to the new package.

## 1. System boundary

```mermaid
flowchart LR
    A[Android app<br/>raw permitted context] -->|existing request boundary| B[Backend orchestration]
    B --> T[(TrustedContact<br/>current projection)]
    B --> D[(DeviceHistory<br/>current projection)]
    B --> L[(LocationHistory<br/>eligible observations)]
    TE[(TrustedContactEvent<br/>append-only)] --> T
    TX[(Transaction and outcome events<br/>append-only)] --> D
    TX --> L

    C[[feature_schema_v2.json<br/>schema hash + policy versions]] --> F[Shared feature transformer]
    T --> F
    D --> F
    L --> F
    B --> F
    F --> V{Contract validator}
    V -->|valid canonical vector| P[PredictionService adapter]
    V -->|mismatch or source failure| X[Reject and alert]
    V --> S[(FeatureSnapshot<br/>immutable audit/replay)]

    TE --> O[Point-in-time source adapters]
    TX --> O
    L --> O
    O --> F
    F --> Q{Offline quality gates}
    S --> Q
    Q -->|parity and quality pass| DS[(Versioned V2 dataset candidate)]
    Q -->|failure| Z[Quarantine build]
    DS -. separate future sprint .-> M[Model V2 training]
```

Android supplies permitted raw transaction context; authoritative relationship, device-history, and location-history features are resolved by backend-owned state. The current endpoint is unchanged by this architecture sprint.

## 2. Point-in-time sequence

```mermaid
sequenceDiagram
    participant App as Android
    participant Back as Backend
    participant Store as Feature source stores
    participant Build as Shared transformer
    participant Snap as FeatureSnapshot
    participant Pred as PredictionService

    App->>Back: Existing transaction request + raw context
    Back->>Back: Assign trusted cutoff_ts
    Back->>Store: Read committed state strictly before cutoff
    Store-->>Build: Trusted/device/location point-in-time state
    Back->>Build: Current raw context + schema/policy versions
    Build->>Build: Compute and validate canonical ordered vector
    Build->>Snap: Persist vector, lineage, versions, hashes
    Build->>Pred: Validated Model V2 vector (future)
    Pred-->>Back: Risk result
    Back->>Store: After snapshot, record eligible outcome events
```

The write-after-snapshot rule prevents the current payment from becoming its own trusted history, known device, or historical location.

## 3. Storage relationships

```mermaid
erDiagram
    USER ||--o{ TRUSTED_CONTACT : owns
    USER ||--o{ TRUSTED_CONTACT_EVENT : emits
    RECEIVER ||--o{ TRUSTED_CONTACT : identifies
    RECEIVER ||--o{ TRUSTED_CONTACT_EVENT : identifies
    TRUSTED_CONTACT_EVENT }o--|| TRUSTED_CONTACT : projects_to
    USER ||--o{ DEVICE_HISTORY : uses
    USER ||--o{ LOCATION_HISTORY : observes
    TRANSACTION ||--o| LOCATION_HISTORY : supplies
    TRANSACTION ||--|| FEATURE_SNAPSHOT : captured_as
    USER ||--o{ FEATURE_SNAPSHOT : receives

    TRUSTED_CONTACT {
      uuid user_id
      uuid receiver_id
      enum status
      timestamptz active_since_ts
      bigint version
    }
    TRUSTED_CONTACT_EVENT {
      uuid event_id
      uuid user_id
      uuid receiver_id
      enum event_type
      timestamptz event_ts
      bigint event_sequence
    }
    DEVICE_HISTORY {
      uuid user_id
      char fingerprint_hash
      varchar fingerprint_version
      timestamptz first_seen_ts
      timestamptz last_seen_ts
    }
    LOCATION_HISTORY {
      uuid transaction_id
      uuid user_id
      decimal latitude
      decimal longitude
      real accuracy_m
      timestamptz event_ts
      enum eligibility_status
    }
    FEATURE_SNAPSHOT {
      uuid transaction_id
      varchar schema_hash
      varchar transformer_version
      jsonb features
      jsonb provenance
      char vector_hash
    }
```

## 4. Feature lineage

```mermaid
flowchart TD
    I1[user_id + receiver_id + cutoff] --> TC[Point-in-time trusted state]
    E1[TrustedContactEvent] --> TC
    TC --> F1[trusted_contact]
    TC --> F2[trusted_contact_age]

    I2[user_id + current fingerprint/version + cutoff] --> DH[Prior eligible device membership]
    E2[DeviceHistory / transaction device events] --> DH
    DH --> F3[known_device]
    DH --> F4[device_change_flag]

    I3[user_id + current valid location + cutoff] --> LH[Prior eligible locations<br/>90-day window, minimum 3]
    E3[LocationHistory] --> LH
    LH --> HD[Nearest Haversine distance]
    HD --> F5[location_distance]
    HD --> TH[Versioned 50 km threshold]
    TH --> F6[location_anomaly]
```

## 5. Contract deployment and quality gates

```mermaid
flowchart LR
    H[Human semantic spec<br/>FeatureSchema.md] --> J[Canonical JSON contract]
    J --> A[Generated Android names/aliases]
    J --> B[Backend source validation]
    J --> C[Shared transformer]
    J --> D[Training column builder]
    J --> E[PredictionService vector validator]
    C --> G[Golden parity fixtures]
    D --> G
    E --> G
    G -->|exact match| OK[Publish/start]
    G -->|any mismatch| NO[Block build/start]
```

Required gate identity is the tuple `(schema_hash, transformer_version, device_fingerprint_version, location_policy_version)`. Names alone are insufficient: a formula or policy change with unchanged columns is still incompatible.

## 6. Trust and privacy boundaries

- Android is a signal producer, not the authority for trusted-contact history or prior behavior.
- The backend owns identity resolution, cutoff assignment, source retrieval, validation, and snapshot lineage.
- Raw hardware identifiers are replaced by versioned pseudonymous fingerprints.
- Precise location is consented, encrypted, access-controlled, retention-limited, and omitted from `FeatureSnapshot` provenance.
- Offline builders receive governed point-in-time views, not unrestricted production tables.
- No Model V2 training path exists until real source coverage, mature labels, privacy approval, and parity gates pass.
