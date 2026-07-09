"""Explicit errors raised by the feature infrastructure."""


class FeatureInfrastructureError(ValueError):
    """Base class for deterministic feature-infrastructure failures."""


class FeatureContractError(FeatureInfrastructureError):
    """The shared contract is missing, malformed, or internally inconsistent."""


class FeatureMissingError(FeatureInfrastructureError):
    """A required feature or construction field is absent."""


class FeatureTypeError(FeatureInfrastructureError):
    """A value does not have the exact type declared by the contract."""


class FeatureValueError(FeatureInfrastructureError):
    """A value violates a range, enumeration, or cross-field rule."""


class FeatureSchemaMismatchError(FeatureInfrastructureError):
    """Names, ordering, version, or hash differ from the shared contract."""


class FeatureSourceStateError(FeatureInfrastructureError):
    """Resolved point-in-time source state is inconsistent with the payment."""
