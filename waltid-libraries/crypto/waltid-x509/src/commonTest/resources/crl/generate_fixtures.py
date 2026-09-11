"""Generate public CRL/certificate vectors with Python cryptography (OpenSSL).

Run manually with cryptography installed. Private keys exist only in memory and are
never written. Tests use a fixed evaluation instant; regenerated signatures vary.
"""
from base64 import b64encode
from datetime import datetime, timezone
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from cryptography.x509.oid import NameOID, ObjectIdentifier


def time(value):
    return datetime.fromisoformat(value).replace(tzinfo=timezone.utc)


START = time("2026-01-01T00:00:00")
END = time("2036-01-01T00:00:00")
THIS = time("2026-09-07T20:00:00")
NEXT = time("2026-09-08T04:00:00")
REVOKED = time("2026-09-07T19:00:00")
DER = serialization.Encoding.DER
vectors = {}


def certificates(label, key, digest):
    dn = x509.Name([x509.NameAttribute(NameOID.COUNTRY_NAME, "AT"),
                    x509.NameAttribute(NameOID.COMMON_NAME, f"CRL test {label} CA")])
    ski = x509.SubjectKeyIdentifier.from_public_key(key.public_key())
    aki = x509.AuthorityKeyIdentifier.from_issuer_subject_key_identifier(ski)
    ca = (x509.CertificateBuilder().subject_name(dn).issuer_name(dn)
          .public_key(key.public_key()).serial_number(0x8001).not_valid_before(START).not_valid_after(END)
          .add_extension(x509.BasicConstraints(True, 0), True)
          .add_extension(x509.KeyUsage(False, False, False, False, False, True, True, None, None), True)
          .add_extension(ski, False).add_extension(aki, False)
          .add_extension(x509.CRLDistributionPoints([x509.DistributionPoint(
              [x509.UniformResourceIdentifier(f"https://crl.example.test/{label}.crl")], None, None, None)]), False)
          .sign(key, digest))
    leaf_key = ec.generate_private_key(ec.SECP256R1())
    leaf = (x509.CertificateBuilder()
            .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "CRL test reader")]))
            .issuer_name(dn).public_key(leaf_key.public_key()).serial_number(0x8002)
            .not_valid_before(START).not_valid_after(END)
            .add_extension(x509.BasicConstraints(False, None), True)
            .add_extension(x509.KeyUsage(True, False, False, False, False, False, False, None, None), True)
            .add_extension(x509.ExtendedKeyUsage([ObjectIdentifier("1.0.18013.5.1.6")]), False)
            .add_extension(x509.SubjectKeyIdentifier.from_public_key(leaf_key.public_key()), False)
            .add_extension(aki, False)
            .add_extension(x509.CRLDistributionPoints([x509.DistributionPoint(
                [x509.UniformResourceIdentifier(f"https://crl.example.test/{label}.crl")], None, None, None)]), False)
            .sign(key, digest))
    vectors[f"{label}_CA"] = ca.public_bytes(DER)
    vectors[f"{label}_LEAF"] = leaf.public_bytes(DER)
    if label == "EC256":
        for name, is_ca, crl_sign, start, end in [
            ("ISSUER_NOT_CA", False, True, START, END),
            ("ISSUER_NO_CRL_SIGN", True, False, START, END),
            ("ISSUER_EXPIRED", True, True, START, time("2026-09-01T00:00:00")),
            ("ISSUER_NOT_YET_VALID", True, True, NEXT, END),
        ]:
            variant = (x509.CertificateBuilder().subject_name(dn).issuer_name(dn)
                       .public_key(key.public_key()).serial_number(0x8001)
                       .not_valid_before(start).not_valid_after(end)
                       .add_extension(x509.BasicConstraints(is_ca, 0 if is_ca else None), True)
                       .add_extension(x509.KeyUsage(False, False, False, False, False, is_ca, crl_sign, None, None), True)
                       .add_extension(ski, False).add_extension(aki, False).sign(key, digest))
            vectors[name] = variant.public_bytes(DER)
    return ca, leaf


def crl(ca, key, digest, revoked=False, extra=None, entry_extra=None, aki=True, number=True,
        revoked_time=REVOKED, revoked_serials=None, reason=x509.ReasonFlags.certificate_hold,
        aki_critical=False, number_critical=False):
    builder = x509.CertificateRevocationListBuilder().issuer_name(ca.subject).last_update(THIS).next_update(NEXT)
    if aki:
        builder = builder.add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(key.public_key()), aki_critical)
    if number:
        builder = builder.add_extension(x509.CRLNumber(1), number_critical)
    if extra:
        builder = builder.add_extension(*extra)
    if revoked:
        for serial in (revoked_serials if revoked_serials is not None else [0x8001, 0x8002]):
            entry = (x509.RevokedCertificateBuilder().serial_number(serial).revocation_date(revoked_time)
                     .add_extension(x509.CRLReason(reason), False))
            if entry_extra:
                entry = entry.add_extension(*entry_extra)
            builder = builder.add_revoked_certificate(entry.build())
    return builder.sign(key, digest).public_bytes(DER)


for label, key, digest in [
    ("EC256", ec.generate_private_key(ec.SECP256R1()), hashes.SHA256()),
    ("EC384", ec.generate_private_key(ec.SECP384R1()), hashes.SHA384()),
    ("EC512", ec.generate_private_key(ec.SECP521R1()), hashes.SHA512()),
    ("RSA256", rsa.generate_private_key(public_exponent=65537, key_size=2048), hashes.SHA256()),
]:
    ca, leaf = certificates(label, key, digest)
    vectors[f"{label}_GOOD"] = crl(ca, key, digest)
    vectors[f"{label}_REVOKED"] = crl(ca, key, digest, revoked=True)
    if label == "RSA256":
        vectors["RSA384_GOOD"] = crl(ca, key, hashes.SHA384())
        vectors["RSA512_GOOD"] = crl(ca, key, hashes.SHA512())
    if label == "EC256":
        for name, options in {
            "CA_ONLY_REVOKED": {"revoked": True, "revoked_serials": [0x8001]},
            "UNKNOWN_CRITICAL": {"extra": (x509.UnrecognizedExtension(ObjectIdentifier("1.2.3.4"), b"\x05\x00"), True)},
            "UNKNOWN_NONCRITICAL": {"extra": (x509.UnrecognizedExtension(ObjectIdentifier("1.2.3.4"), b"\x05\x00"), False)},
            "DELTA": {"extra": (x509.DeltaCRLIndicator(0), True)},
            "PARTITIONED": {"extra": (x509.IssuingDistributionPoint(None, None, True, False, None, False, False), True)},
            "INDIRECT_ENTRY": {"revoked": True, "entry_extra": (x509.CertificateIssuer([x509.DirectoryName(ca.subject)]), True)},
            "NO_AKI": {"aki": False},
            "NO_NUMBER": {"number": False},
            "CRITICAL_AKI": {"aki_critical": True},
            "CRITICAL_NUMBER": {"number_critical": True},
            "REMOVE_FROM_CRL": {"revoked": True, "reason": x509.ReasonFlags.remove_from_crl},
            "FUTURE_REVOCATION": {"revoked": True, "revoked_time": NEXT},
        }.items():
            vectors[name] = crl(ca, key, digest, **options)
        # Same issuer name but a different signing key: the CRL is authentic for that key, not this CA.
        wrong_key = ec.generate_private_key(ec.SECP256R1())
        vectors["WRONG_KEY"] = crl(ca, wrong_key, digest)

out = Path(__file__).resolve().parents[3] / "commonTestFixtures/kotlin/id/walt/certificate/x509/revocation/CrlTestFixtures.kt"
out.parent.mkdir(parents=True, exist_ok=True)
lines = ["// Generated by src/commonTest/resources/crl/generate_fixtures.py; contains no private keys.",
         "package id.walt.certificate.x509.revocation", "", "import kotlin.io.encoding.Base64", "",
         "internal object CrlTestFixtures {", "    fun der(name: String): ByteArray = Base64.decode(values.getValue(name))",
         "    private val values = mapOf("]
lines.extend(f'        "{name}" to "{b64encode(value).decode()}",' for name, value in vectors.items())
lines += ["    )", "}", ""]
out.write_text("\n".join(lines))
print(f"Wrote {len(vectors)} certificate and CRL vectors to {out}")
