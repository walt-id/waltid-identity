import fnmatch
import json
from pathlib import Path
import re
import textwrap
import unittest


GITHUB = Path(__file__).resolve().parents[2]
FIXTURE = "waltid-applications/waltid-wallet-demo-test-fixtures/key-attestation/"


class DemoAttestationPathsTest(unittest.TestCase):
    def test_shared_kotlin_and_profile_inputs_select_android_and_itb(self):
        android = (GITHUB / "workflows/android-eligibility.yml").read_text()
        android_patterns = json.loads(textwrap.dedent(android.split("      path-patterns: |\n", 1)[1]))
        itb = (GITHUB / "workflows/itb-profile-tests.yml").read_text()
        itb_patterns = re.findall(r"^      - '([^']+)'$", itb, re.MULTILINE)
        for path in ["Resources/KeyAttestationProfiles.json", "sources.gradle.kts",
                     "kotlin/id/walt/walletdemo/attestation/DemoKeyAttestationProviders.kt"]:
            for lane, patterns in [("Android", android_patterns), ("ITB offline", itb_patterns)]:
                with self.subTest(path=path, lane=lane):
                    self.assertTrue(any(fnmatch.fnmatchcase(FIXTURE + path, pattern) for pattern in patterns),
                                    f"{lane} must run when its shared attestation input changes")
