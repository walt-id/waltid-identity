package id.walt.x509.id.walt.x509

import id.walt.x509.X509V3ExtensionOID
import java.security.cert.X509Certificate

fun X509Certificate.criticalX509V3ExtensionOIDs() =
    criticalExtensionOIDs.mapNotNull { X509V3ExtensionOID.fromOID(it) }.toSet()


fun X509Certificate.nonCriticalX509V3ExtensionOIDs() =
    nonCriticalExtensionOIDs.mapNotNull { X509V3ExtensionOID.fromOID(it) }.toSet()