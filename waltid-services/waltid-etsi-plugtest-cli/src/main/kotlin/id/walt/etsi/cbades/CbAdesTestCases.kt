package id.walt.etsi.cbades

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** One element of a header bucket in a plugtest test-case definition (e.g. `<x5t><hashAlgorithm>SHA-512</hashAlgorithm>...`). */
data class TcNode(val name: String, val text: String, val children: List<TcNode>) {
    fun child(name: String): TcNode? = children.firstOrNull { it.name == name }
    fun all(name: String): List<TcNode> = children.filter { it.name == name }
    fun value(name: String): String? = child(name)?.text?.takeIf { it.isNotBlank() }

    /** Parses "[a, b]" list notation used in sigD members. */
    fun listValue(name: String): List<String>? =
        value(name)?.trim()?.removePrefix("[")?.removeSuffix("]")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
}

data class TcCounterSignature(val protected: TcNode, val unprotected: TcNode?, val abbreviated: Boolean)

/** One signer (COSE_Sign1 body layer or COSE_Signature in a COSE_Sign). */
data class TcSigner(val protected: TcNode, val unprotected: TcNode?) {
    val uHeaders: List<TcNode> get() = unprotected?.child("uHeaders")?.children ?: emptyList()
    val counterSignatures: List<TcCounterSignature>
        get() = uHeaders.filter { it.name == "COSE_Countersignature" }.map { parseCounter(it) }

    companion object {
        fun parseCounter(n: TcNode) = TcCounterSignature(n.child("ProtectedHeader")!!, n.child("UnprotectedHeader"), false)
    }
}

enum class TcStructure { SIGN1, SIGN }

data class CbAdesTestCase(
    val code: String,
    val structure: TcStructure,
    val tagged: Boolean,
    val payloadFile: String?,
    val detached: Boolean,
    val signers: List<TcSigner>,
    val documentation: String,
)

data class CbAdesAugmentationCase(val code: String, val initial: String, val augmented: String, val documentation: String) {
    /** Portal naming: Aug-B-LTA-2 -> AugLTA-2 (see plugtest forum topic 11). */
    val portalCode: String get() = code.replace(Regex("^Aug-B-"), "Aug")
}

object CbAdesTestCases {
    fun loadAll(dir: File): List<CbAdesTestCase> = dir.walkTopDown()
        .filter { it.isFile && it.extension == "xml" && it.name.startsWith("CB-B-") && !it.name.matches(Regex("CB-B-[A-Z]+N-.*")) }
        .mapNotNull { runCatching { parse(it) }.onFailure { e -> System.err.println("Skipping ${it.name}: ${e.message}") }.getOrNull() }
        .sortedWith(compareBy({ levelOrder(it.code) }, { it.code.substringAfterLast('-').toIntOrNull() ?: 0 }))
        .toList()

    fun loadAugmentations(dir: File): List<CbAdesAugmentationCase> = dir.walkTopDown()
        .filter { it.isFile && it.extension == "xml" && it.name.startsWith("Aug") }
        .map { f ->
            val root = xml(f)
            val aug = root.getElementsByTagName("Augmentation").item(0) as Element
            CbAdesAugmentationCase(
                f.nameWithoutExtension.trim(),
                aug.getElementsByTagName("InitialSignature").item(0).textContent.trim(),
                aug.getElementsByTagName("AugmentedSignature").item(0).textContent.trim(),
                root.getElementsByTagName("Documentation").item(0)?.textContent?.trim() ?: "",
            )
        }.sortedBy { it.code }.toList()

    private fun levelOrder(code: String) = listOf("CB-B-B-", "CB-B-T-", "CB-B-LT-", "CB-B-LTA-").indexOfFirst { code.startsWith(it) }

    fun parse(file: File): CbAdesTestCase {
        val root = xml(file)
        val payloadEl = root.getElementsByTagName("PayloadToBeSigned").item(0) as Element?
        val coseEl = root.childElements().first { it.tagName.startsWith("COSE") }
        val structure = if (coseEl.tagName.replace("_", "") == "COSESign1") TcStructure.SIGN1 else TcStructure.SIGN
        val signers = if (structure == TcStructure.SIGN1) {
            listOf(TcSigner(node(coseEl.childElements().first { it.tagName == "ProtectedHeader" }), coseEl.childElements().firstOrNull { it.tagName == "UnprotectedHeader" }?.let(::node)))
        } else {
            val list = mutableListOf<TcSigner>()
            var protected: TcNode? = null
            var unprotected: TcNode? = null
            for (el in coseEl.childElements().first { it.tagName == "signatures" }.childElements()) {
                when (el.tagName) {
                    "COSE_Signature", "COSESignature" -> list += TcSigner(
                        node(el.childElements().first { it.tagName == "ProtectedHeader" }),
                        el.childElements().firstOrNull { it.tagName == "UnprotectedHeader" }?.let(::node),
                    )
                    "ProtectedHeader" -> protected = node(el)
                    "UnprotectedHeader" -> unprotected = node(el)
                    "signature" -> {
                        list += TcSigner(protected!!, unprotected); protected = null; unprotected = null
                    }
                }
            }
            list
        }
        return CbAdesTestCase(
            code = file.nameWithoutExtension.trim(),
            structure = structure,
            tagged = coseEl.getAttribute("Tagged").equals("true", ignoreCase = true),
            payloadFile = payloadEl?.getAttribute("File")?.takeIf { it.isNotBlank() },
            detached = payloadEl?.getAttribute("Detached").equals("true", ignoreCase = true),
            signers = signers,
            documentation = root.getElementsByTagName("Documentation").item(0)?.textContent?.trim()?.replace(Regex("\\s+"), " ") ?: "",
        )
    }

    private fun node(el: Element): TcNode = TcNode(el.tagName, el.childNodes.let { nodes ->
        (0 until nodes.length).map { nodes.item(it) }.filter { it.nodeType == Node.TEXT_NODE }.joinToString("") { it.textContent }.trim()
    }, el.childElements().map(::node))

    private fun Element.childElements(): List<Element> = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

    private fun xml(file: File): Element {
        val f = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        return f.newDocumentBuilder().parse(file).documentElement
    }
}
