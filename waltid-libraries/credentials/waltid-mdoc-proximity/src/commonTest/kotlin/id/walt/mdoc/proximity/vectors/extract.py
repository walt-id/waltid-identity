#!/usr/bin/env python3
"""Extract the reviewed public Multipaz vectors. This maintenance tool is never run by tests."""
import hashlib
import json
from pathlib import Path
import re
import sys

REVISION = "7c0988bee3384d13a0732e0c33336ae0faf3b863"
SOURCE_SHA256 = "1c5d155cf94eba105b8084576b9a1032fb0f084cb9b35e6ebc8b7de6fc157e7b"
FIELDS = ["SESSION_TRANSCRIPT_BYTES", "DEVICE_REQUEST", "DEVICE_RESPONSE", "SESSION_ESTABLISHMENT", "SESSION_DATA"]
FIELDS += [f"EPHEMERAL_{role}_KEY_{coordinate}" for role in ["READER", "DEVICE"] for coordinate in ["X", "Y", "D"]]

source = Path(sys.argv[1]).read_bytes()
assert hashlib.sha256(source).hexdigest() == SOURCE_SHA256, "Review any source revision change before extracting"
text = source.decode()
constants = dict(re.findall(r"const val ISO_18013_5_ANNEX_D_(\w+)\s*=([\s\S]*?)(?=\n    const val |\n})", text))
header = text[:text.index("package org.multipaz.mdoc")]
output = header + "// Extracted public test fixtures; see manifest.json. Never use these published keys outside tests.\n"
output += "package id.walt.mdoc.proximity.vectors\n\ninternal object MultipazSessionVectors {\n"
manifest = {"origin": f"https://github.com/openwallet-foundation/multipaz/blob/{REVISION}/multipaz/src/commonTest/kotlin/org/multipaz/mdoc/TestVectors.kt",
            "revision": REVISION, "sourceSha256": SOURCE_SHA256, "license": "Apache-2.0", "vectors": {}}
values = {field: "".join(re.findall(r'"([0-9a-fA-F]+)"', constants[field])) for field in FIELDS}


def der(tag, body):
    size = len(body)
    length = bytes([size]) if size < 128 else bytes([0x81, size])
    return bytes([tag]) + length + body


# Re-encode the published EC coordinates/scalar as PKCS#8 for platforms that intentionally
# do not import private JWK. This is structural encoding, not a generated crypto expectation.
for role in ["READER", "DEVICE"]:
    x, y, d = (bytes.fromhex(values[f"EPHEMERAL_{role}_KEY_{part}"]) for part in ["X", "Y", "D"])
    sec1 = der(0x30, bytes.fromhex("020101") + der(4, d) + der(0xa1, der(3, b"\x00\x04" + x + y)))
    algorithm = bytes.fromhex("301306072a8648ce3d020106082a8648ce3d030107")
    values[f"EPHEMERAL_{role}_PKCS8"] = der(0x30, bytes.fromhex("020100") + algorithm + der(4, sec1)).hex()
for field, value in values.items():
    data = bytes.fromhex(value)
    manifest["vectors"][field] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    if field.endswith("PKCS8"):
        manifest["vectors"][field]["transformation"] = "RFC 5208 / RFC 5915 DER encoding of the public example scalar and point"
    parts = [value[index:index+100] for index in range(0, len(value), 100)]
    output += f"    const val {field} =\n" + " +\n".join(f'        "{part}"' for part in parts) + "\n\n"
output += "}\n"
folder = Path(__file__).parent
(folder / "MultipazSessionVectors.kt").write_text(output)
(folder / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
