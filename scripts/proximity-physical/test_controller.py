"""Controller contracts with loopback HTTP and synthetic processes; never invokes device tools."""
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

spec = importlib.util.spec_from_file_location("physical_controller", Path(__file__).with_name("run.py"))
controller = importlib.util.module_from_spec(spec)
spec.loader.exec_module(controller)


class ControllerTests(unittest.TestCase):
    def test_http_auth_shape_duplicate_and_command_payload(self):
        server = controller.IOS("test-run", "127.0.0.1")
        self.addCleanup(server.close)
        def request(path, value=None, token=None):
            request = Request(server.url + "/test-run/" + path,
                              data=None if value is None else json.dumps(value).encode(),
                              headers={"Authorization": "Bearer " + (token or server.token)})
            try:
                with urlopen(request, timeout=2) as response:
                    return response.status, json.load(response)
            except HTTPError as error:
                code = error.code
                error.close()
                return code, None
        event = dict(event="holder-started", role="holder", elapsedNanos="1")
        self.assertEqual(request("events/holder-started", event, "invalid")[0], 403)
        for bad in [[], "text", {**event, "role": "reader"}, {**event, "processId": 3}]:
            self.assertEqual(request("events/holder-started", bad)[0], 400)
        self.assertIsNone(server.event("holder-started"))
        self.assertEqual(request("events/holder-started", event)[0], 200)
        self.assertEqual(request("events/holder-started", event)[0], 409)
        self.assertEqual(server.event("holder-started"), event)
        self.assertEqual(request("commands/approve-1")[0], 404)
        server.send("approve-1", {"control": "exact"})
        self.assertEqual(request("commands/approve-1"), (200, {"control": "exact"}))

    def test_evidence_rejects_mismatched_identity_or_false_oracles(self):
        value = dict(event="reader-verified-1", role="reader", elapsedNanos=1, fieldCount="2",
                     issuerAuthenticated="true", deviceAuthenticated="true", issuerTrusted="true", getResponseCount="1")
        controller.validate_event(value, "reader-verified-1", "nfc-direct-disconnect")
        for change in [dict(event="reader-verified-3"), dict(role="holder"), dict(elapsedNanos=""),
                       dict(fieldCount="1"), dict(issuerTrusted="false"), dict(getResponseCount="0")]:
            with self.subTest(change=change), self.assertRaises(controller.PreconditionError):
                controller.validate_event(value | change, "reader-verified-1", "nfc-direct-disconnect")

    def test_phase_wait_is_bounded_and_process_failure_is_not_success(self):
        endpoint = Mock()
        endpoint.event.return_value = None
        failed = Mock(returncode=1)
        failed.poll.return_value = 1
        with self.assertRaisesRegex(controller.PreconditionError, "process failed"):
            controller.wait_event(endpoint, "holder-ready-1", [failed])
        with self.assertRaisesRegex(controller.PreconditionError, "timed out"):
            controller.wait_event(endpoint, "holder-ready-1", [], deadline=0)

    def test_cleanup_failure_marks_run_failed_and_closes_remaining_resources(self):
        endpoint, ios, log = Mock(), Mock(), Mock()
        endpoint.cleanup.side_effect = OSError("disconnected")
        ios.event.return_value = {"processId": "123"}
        report = dict(status="passed")
        with patch.object(controller, "command", side_effect=controller.PreconditionError("stop failed")):
            controller.cleanup_run([], [endpoint], ios, "selected-iphone", [log], report)
        self.assertEqual(report["status"], "failed")
        self.assertTrue(report["cleanupRequired"])
        self.assertEqual(len(report["cleanupErrors"]), 2)
        ios.close.assert_called_once()
        log.close.assert_called_once()

    def test_cleanup_terminates_only_selected_process_groups_and_waits_after_kill(self):
        worker = Mock(pid=123)
        worker.poll.return_value = None
        worker.wait.side_effect = [controller.subprocess.TimeoutExpired("test", 5), 0]
        report = dict(status="passed")
        with patch.object(controller.os, "killpg") as kill:
            controller.cleanup_run([worker], [], None, "unused", [], report)
        self.assertEqual([call.args for call in kill.call_args_list],
                         [(123, controller.signal.SIGTERM), (123, controller.signal.SIGKILL)])
        self.assertEqual(worker.wait.call_count, 2)
        self.assertEqual(report["status"], "passed")


if __name__ == "__main__":
    unittest.main()
