"""FORESIGHT Model V2 feature-domain infrastructure.

This package has no dependency on the prediction service or model runtime.  Public
types are exported here so callers do not need to depend on internal modules.
"""

from backend.feature_infrastructure.builder import (
    CurrentLocation,
    FeatureSnapshotBuildRequest,
    FeatureSnapshotBuilder,
)
from backend.feature_infrastructure.contract import FeatureContract
from backend.feature_infrastructure.models import (
    BusinessFeatures,
    DeviceHistory,
    FeatureSnapshot,
    LocationHistory,
    TrustedContact,
    TrustedContactEvent,
)
from backend.feature_infrastructure.verification import FeatureInfrastructureVerifier

__all__ = [
    "BusinessFeatures",
    "CurrentLocation",
    "DeviceHistory",
    "FeatureContract",
    "FeatureInfrastructureVerifier",
    "FeatureSnapshot",
    "FeatureSnapshotBuildRequest",
    "FeatureSnapshotBuilder",
    "LocationHistory",
    "TrustedContact",
    "TrustedContactEvent",
]
