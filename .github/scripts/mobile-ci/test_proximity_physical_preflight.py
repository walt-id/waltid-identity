import unittest
from dataclasses import replace

from proximity_physical_preflight import Device, FIXTURE, PEER_REVISION, PreconditionError, preflight


class PhysicalPreflightTests(unittest.TestCase):
    holder = Device("holder-serial", "android", True, True, 35, frozenset({"ble", "nfc-host"}))
    reader = Device("reader-serial", "android", True, True, 35, frozenset({"ble", "nfc-reader"}))

    def check(self, **changes):
        args = dict(environment={}, opt_in="physical-local", holder_id=self.holder.identifier,
                    reader_id=self.reader.identifier, devices=[self.holder, self.reader],
                    configuration="nfc-direct-disconnect", fixture=FIXTURE, peer_revision=PEER_REVISION)
        return preflight(**(args | changes))

    def test_explicit_distinct_supported_devices_pass_preflight_only(self):
        self.assertEqual(self.check(), (self.holder, self.reader))

    def test_ci_cannot_opt_into_physical_execution(self):
        for environment in ({"CI": "true"}, {"CI": "false"}, {"GITHUB_ACTIONS": "true"},
                            {"BUILD_BUILDID": "123"}, {"GITLAB_CI": "true"}):
            with self.subTest(environment=environment), self.assertRaises(PreconditionError):
                self.check(environment=environment)

    def test_opt_in_and_both_explicit_selectors_are_required(self):
        for changes in ({"opt_in": None}, {"holder_id": ""}, {"reader_id": ""},
                        {"reader_id": self.holder.identifier}):
            with self.subTest(changes=changes), self.assertRaises(PreconditionError):
                self.check(**changes)

    def test_missing_ambiguous_unavailable_and_simulated_devices_fail(self):
        for devices in ([self.reader], [self.holder, self.holder, self.reader],
                        [replace(self.holder, available=False), self.reader],
                        [replace(self.holder, physical=False), self.reader],
                        [self.holder, replace(self.reader, physical=False)]):
            with self.subTest(devices=devices), self.assertRaises(PreconditionError):
                self.check(devices=devices)

    def test_wrong_fixture_peer_version_and_unsupported_peer_fail(self):
        for changes in ({"fixture": "normal-wallet"}, {"peer_revision": "main"},
                        {"configuration": "wifi-aware"}, {"configuration": "browser-reader"}):
            with self.subTest(changes=changes), self.assertRaises(PreconditionError):
                self.check(**changes)

    def test_actual_capability_and_os_are_required(self):
        for holder, reader in ((replace(self.holder, capabilities=frozenset()), self.reader),
                               (self.holder, replace(self.reader, capabilities=frozenset())),
                               (replace(self.holder, os_major=29), self.reader),
                               (self.holder, replace(self.reader, os_major=29))):
            with self.subTest(holder=holder, reader=reader), self.assertRaises(PreconditionError):
                self.check(devices=[holder, reader])

    def test_ios_requires_named_signed_host_and_separate_nfc_eligibility(self):
        ios = replace(self.holder, platform="ios", os_major=26)
        for signed_host in (None, {}, {"bundle_id": "fixture.proximityphysical", "team_id": "TEST"}):
            with self.subTest(signed_host=signed_host), self.assertRaises(PreconditionError):
                self.check(devices=[ios, self.reader], configuration="nfc-ble-continuation", signed_host=signed_host)
        self.assertEqual(self.check(devices=[ios, self.reader], configuration="nfc-ble-continuation", signed_host={
            "bundle_id": "fixture.proximityphysical", "team_id": "TEST", "card_session_entitled": True})[0], ios)
        self.assertEqual(self.check(devices=[ios, self.reader], configuration="ble-gatt-central",
                                    signed_host={"bundle_id": "fixture.proximityphysical", "team_id": "TEST"})[0], ios)


if __name__ == "__main__":
    unittest.main()
