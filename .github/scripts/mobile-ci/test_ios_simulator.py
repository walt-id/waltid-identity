import unittest

from ensure_ios_simulator import resolve_simulator


class EnterpriseSimulatorTest(unittest.TestCase):
    def test_reuses_available_device_on_latest_compatible_runtime(self):
        inventory = {
            "runtimes": [
                {"identifier": "older", "name": "iOS 26.4", "version": "26.4", "isAvailable": True},
                {"identifier": "current", "name": "iOS 26.5", "version": "26.5.0", "isAvailable": True},
                {"identifier": "newer", "name": "iOS 27.0", "version": "27.0", "isAvailable": True},
                {"identifier": "missing", "name": "iOS 26.6", "version": "26.6", "isAvailable": False},
            ],
            "devices": {"current": [
                {"name": "iPhone 17", "udid": "unavailable", "isAvailable": False},
                {"name": "iPhone 17 Pro", "udid": "other-model", "isAvailable": True},
                {"name": "iPhone 17", "udid": "selected", "isAvailable": True},
            ]},
        }
        runtime, device = resolve_simulator(inventory, "26.5")
        self.assertEqual("current", runtime["identifier"])
        self.assertEqual("selected", device)

    def test_missing_registered_device_requests_creation_on_installed_runtime(self):
        inventory = {"runtimes": [
            {"identifier": "current", "name": "iOS 26.5", "version": "26.5", "isAvailable": True},
        ], "devices": {}}
        runtime, device = resolve_simulator(inventory, "26.5")
        self.assertEqual("current", runtime["identifier"])
        self.assertIsNone(device)

    def test_no_compatible_runtime_fails_before_test_execution(self):
        inventory = {"runtimes": [
            {"identifier": "newer", "name": "iOS 27.0", "version": "27.0", "isAvailable": True},
        ], "devices": {}}
        with self.assertRaisesRegex(RuntimeError, "compatible with SDK 26.5"):
            resolve_simulator(inventory, "26.5")


if __name__ == "__main__":
    unittest.main()
