import unittest
from select_ios_simulator import select_simulator


class SimulatorSelectionTest(unittest.TestCase):
    def inventory(self, *devices):
        return {"devices": {"com.apple.CoreSimulator.SimRuntime.iOS-26-5": list(devices)}}

    def device(self, identifier="selected", name="iPhone 17", available=True):
        return {"udid": identifier, "name": name, "isAvailable": available}

    def test_physical_or_implicit_destinations_are_rejected(self):
        for destination in ["platform=iOS,id=physical", "platform=iOS Simulator", "platform=iOS Simulator,name=iPhone 17,id=selected", "platform=iOS Simulator,name=iPhone 17,name=iPhone 17"]:
            with self.subTest(destination=destination), self.assertRaises(ValueError):
                select_simulator(destination, self.inventory(self.device()))

    def test_unavailable_or_ambiguous_simulators_are_rejected(self):
        for devices in [[], [self.device(available=False)], [self.device(), self.device("duplicate")]]:
            with self.subTest(devices=devices), self.assertRaises(ValueError):
                select_simulator("platform=iOS Simulator,name=iPhone 17", self.inventory(*devices))

    def test_exact_id_and_os_resolve_only_the_requested_simulator(self):
        inventory = self.inventory(self.device(), self.device("different"))
        self.assertEqual("selected", select_simulator("platform=iOS Simulator,id=selected,OS=26.5", inventory))
        with self.assertRaises(ValueError):
            select_simulator("platform=iOS Simulator,id=selected,OS=26.6", inventory)

    def test_latest_selects_the_newest_matching_runtime(self):
        inventory = self.inventory(self.device("older"))
        inventory["devices"]["com.apple.CoreSimulator.SimRuntime.iOS-26-6"] = [self.device("newest")]
        self.assertEqual("newest", select_simulator("platform=iOS Simulator,name=iPhone 17,OS=latest", inventory))


if __name__ == "__main__":
    unittest.main()
