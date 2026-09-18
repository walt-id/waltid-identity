"""Pure, hardware-free policy shared by the explicit local physical runner and its tests."""

from dataclasses import dataclass


class PreconditionError(ValueError):
    pass


CI_VARIABLES = ("CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION")
PEER_REVISION = "7c0988bee3384d13a0732e0c33336ae0faf3b863"
FIXTURE = "synthetic-ada-v1"


@dataclass(frozen=True)
class Device:
    identifier: str
    platform: str
    physical: bool
    available: bool
    os_major: int
    capabilities: frozenset[str]


def reject_ci(environment):
    # Presence rejects even CI=false: an opt-in must not reclassify a CI executor as local.
    if any(key in environment for key in CI_VARIABLES):
        raise PreconditionError("Physical suites are local only; a CI environment was detected")


def preflight(*, environment, opt_in, holder_id, reader_id, devices, configuration,
              fixture, peer_revision, signed_host=None):
    reject_ci(environment)
    if opt_in != "physical-local":
        raise PreconditionError("Explicit --opt-in physical-local is required")
    if not holder_id or not reader_id or holder_id == reader_id:
        raise PreconditionError("Select distinct holder and reader devices explicitly")
    if fixture != FIXTURE or peer_revision != PEER_REVISION:
        raise PreconditionError("The disposable synthetic fixture and pinned reader are required")
    selected = []
    for identifier in (holder_id, reader_id):
        matches = [device for device in devices if device.identifier == identifier]
        if len(matches) != 1 or not matches[0].available or not matches[0].physical:
            raise PreconditionError("Selected device is missing, ambiguous, unavailable or simulated")
        selected.append(matches[0])
    holder, reader = selected
    if reader.platform != "android" or reader.os_major < 30:
        raise PreconditionError("The pinned controllable reader requires Android API 30 or later")
    if holder.platform not in {"android", "ios"}:
        raise PreconditionError("Unsupported holder platform")
    if (holder.platform == "android" and holder.os_major < 30
            or holder.platform == "ios" and holder.os_major < 26):
        raise PreconditionError("Selected holder OS is outside the implemented physical host baseline")
    ble = {"ble-gatt-central", "ble-gatt-peripheral", "ble-l2cap-central", "ble-l2cap-peripheral"}
    if configuration in ble:
        required_holder = required_reader = {"ble"}
    elif configuration == "nfc-direct-disconnect":
        required_holder, required_reader = {"nfc-host"}, {"nfc-reader"}
    elif configuration == "nfc-ble-continuation":
        required_holder, required_reader = {"nfc-host", "ble"}, {"nfc-reader", "ble"}
    else:
        raise PreconditionError("No implemented controllable peer for the selected configuration")
    if not required_holder <= holder.capabilities or not required_reader <= reader.capabilities:
        raise PreconditionError("Selected device lacks a required radio or host capability")
    if holder.platform == "ios":
        if configuration == "nfc-direct-disconnect":
            raise PreconditionError("iOS direct NFC needs a separate prepared-sharing procedure")
        if (not signed_host or not signed_host.get("bundle_id", "").endswith(".proximityphysical")
                or not signed_host.get("team_id")):
            raise PreconditionError("An explicitly selected signed iOS host is required")
        if "nfc-host" in required_holder and not signed_host.get("card_session_entitled"):
            raise PreconditionError("The selected signed host lacks the required signed HCE entitlements")
    return holder, reader
