# PAdES signing and validation

This JVM library uses PDFBox and Bouncy Castle. It does not depend on DSS.

## Visible and existing-field signatures

```kotlin
import id.walt.ades.pades.PadesSignatureAppearance
import id.walt.ades.pades.PadesSignatureParameters
import id.walt.ades.pades.PadesSignatureRectangle

val signed = signer.sign(pdf, credentials, PadesSignatureParameters(
    fieldName = "Approval",
    pageIndex = 0,
    rectangle = PadesSignatureRectangle(x = 72f, y = 100f, width = 240f, height = 90f),
    appearance = PadesSignatureAppearance(
        text = "Approved by Example Signer\nDocument approval",
        image = optionalPngOrJpeg,
        font = optionalTrueTypeFont,
    ),
))
```

Page indices start at zero. Rectangles use unrotated PDF user-space coordinates, with the origin at the
lower-left corner, and must fit inside the page crop box. PDF page rotation also rotates the appearance.
Text wraps and shrinks to fit, down to four points; text that still does not fit is rejected. The optional
image preserves its aspect ratio. Helvetica is used by default; provide an embeddable font for other
characters. The appearance is part of the signed revision, not a later annotation.

Set `existingFieldName = "Approval"` to sign an existing empty signature field. It retains its page and
rectangle. Omit `appearance` to preserve its previous appearance. Existing fields with multiple widgets,
non-signature fields, already signed fields and locked fields are rejected.

`certificationPermission` creates a DocMDP certification signature, which must be the first signature.
`fieldLock = PadesFieldLock(PadesFieldLockAction.INCLUDE, listOf("Customer.Name"))` adds a signed FieldMDP
transform and a signature-field lock. Include, exclude and all-fields actions are supported; parent field
names also select their descendants.

## Validation and augmentation

`SignatureValidationResult.structuralLevel` describes the profile suggested by the container. For PAdES,
`level` requires a successfully validated signature and validated time-stamps. An invalid time-stamp can
produce a warning under the default lenient policy while the signature remains valid at B-B. Strict
time-stamp policy rejects it. A failed or indeterminate signature has no validated PAdES level.

Augmentation reuses existing time-stamps only after validation against `timestampValidationPolicy`, or
against the collector's trust anchors and transport when that parameter is omitted. Without a trust
policy it adds fresh time instead of assuming an existing token is trusted. New tokens must have valid
cryptography, the correct message imprint and the RFC 3161 TSA EKU. Trust still depends on the policy
used when validating the resulting document. Invalid old tokens are retained as evidence, so a strict
validator can continue to reject a document containing one even after fresh time is added.

Modification checks compare revisions linked by the xref trailer chain. DSS exemptions follow the DSS
schema rather than arbitrary references. Added signature widgets must belong to a signature covering
that revision; unsigned overlays and changes to an already signed appearance are rejected.

DocMDP P=1 permits validation-data and document-time-stamp updates. P=2 additionally permits existing
form values and signatures. P=3 also permits supported markup annotations (text, highlighting, underline,
strikeout, squiggly, ink, square, circle, free text and stamps). Signed FieldMDP/Lock rules are checked
against every later revision. `allowOtherModifications` cannot bypass these permissions.

The modification checker is deliberately conservative: unsupported annotation operations, changes to
existing appearance streams, form restructuring and repaired/non-monotonic xref histories are rejected.
Linearized base files are supported. It is not a general PDF rendering-equivalence engine. Encrypted PDFs and multi-widget signing are not
supported by this API.
