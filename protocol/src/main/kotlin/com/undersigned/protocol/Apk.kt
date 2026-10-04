package com.undersigned.protocol

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Reads the two facts the publisher proof needs from an APK file, with no Android tools:
 *  - the wallets its manifest names in `<meta-data android:name="undersigned.publisher" android:value="…"/>`
 *    (binary XML inside the APK), and
 *  - the SHA-256 of its signing certificate(s), from the APK Signature Scheme v2/v3 block.
 * The manifest is covered by that signature, so a wallet named there was put there by whoever holds the signing key.
 * In the shared rules module so the phone and the API read a build the same way (the phone does not have to take the
 * server's word for a store-build proof).
 */
object Apk {
    data class Facts(val declared: Set<String>, val certSha256: Set<String>)
    /** The manifest is inflated to at most this many bytes: a zip bomb must not exhaust the reader, phone or server. */
    const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024

    fun read(file: File): Facts = Facts(declaredPublishers(manifest(file)), signerCerts(file))

    /** The manifest entry, inflated to at most [MAX_MANIFEST_BYTES]: a zip bomb from a listed publisher must not take the server down. */
    fun manifest(file: File): ByteArray = ZipFile(file).use { z ->
        z.getInputStream(z.getEntry("AndroidManifest.xml") ?: error("no manifest")).use { inp ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(1 shl 16)
            while (true) { val n = inp.read(buf); if (n < 0) break; if (out.size() + n > MAX_MANIFEST_BYTES) error("manifest over ${MAX_MANIFEST_BYTES} bytes"); out.write(buf, 0, n) }
            out.toByteArray()
        }
    }

    /** Wallets named by the `undersigned.publisher` meta-data, split on , ; or space and kept only if they look like addresses. */
    fun declaredPublishers(axml: ByteArray): Set<String> =
        metaData(axml)["undersigned.publisher"]?.split(',', ';', ' ')?.map { it.trim() }?.filter { Payments.isReference(it) }?.toSet() ?: emptySet()

    // ---- Binary XML ---------------------------------------------------------------------------------------------

    private const val ATTR_NAME = 0x01010003
    private const val ATTR_VALUE = 0x01010024

    /** Every `<meta-data>` name → value (string values only) in a compiled manifest. Malformed input throws IllegalArgumentException, never an out-of-bounds or a huge allocation. */
    fun metaData(axml: ByteArray): Map<String, String> = try { metaData0(axml) } catch (e: IndexOutOfBoundsException) { throw IllegalArgumentException("malformed manifest: ${e.message}") } catch (e: java.nio.BufferUnderflowException) { throw IllegalArgumentException("malformed manifest") }

    private fun metaData0(axml: ByteArray): Map<String, String> {
        val b = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN)
        var strings: List<String> = emptyList(); var resIds = IntArray(0)
        val out = HashMap<String, String>()
        var p = 8                                                  // after the XML file header
        while (p + 8 <= axml.size) {
            val type = b.getShort(p).toInt() and 0xffff; val headerSize = b.getShort(p + 2).toInt() and 0xffff; val size = b.getInt(p + 4)
            if (size <= 0 || p + size > axml.size) break
            when (type) {
                0x0001 -> strings = stringPool(b, p)
                0x0180 -> resIds = IntArray((size - headerSize) / 4) { b.getInt(p + headerSize + it * 4) }
                0x0102 -> {
                    val name = strings.getOrNull(b.getInt(p + 20))
                    if (name == "meta-data") {
                        val attrStart = b.getShort(p + 24).toInt() and 0xffff; val attrSize = b.getShort(p + 26).toInt() and 0xffff; val count = b.getShort(p + 28).toInt() and 0xffff
                        var key: String? = null; var value: String? = null
                        for (i in 0 until count) {
                            val a = p + 16 + attrStart + i * attrSize
                            val nameIdx = b.getInt(a + 4); val raw = b.getInt(a + 8); val dataType = axml[a + 15].toInt() and 0xff; val data = b.getInt(a + 16)
                            val id = if (nameIdx in resIds.indices) resIds[nameIdx] else 0
                            val attrName = strings.getOrNull(nameIdx)
                            val str = strings.getOrNull(if (raw >= 0) raw else if (dataType == 0x03) data else -1)
                            if (id == ATTR_NAME || (id == 0 && attrName == "name")) key = str
                            if (id == ATTR_VALUE || (id == 0 && attrName == "value")) value = str
                        }
                        if (key != null && value != null) out[key] = value
                    }
                }
            }
            p += size
        }
        return out
    }

    private fun stringPool(b: ByteBuffer, p: Int): List<String> {
        val count = b.getInt(p + 8); val flags = b.getInt(p + 16); val stringsStart = b.getInt(p + 20)
        val utf8 = flags and 0x100 != 0
        require(count in 0..1_000_000) { "string pool count $count" }
        return (0 until count).map { i ->
            var o = p + stringsStart + b.getInt(p + 28 + i * 4)
            if (utf8) {
                o += if (b.get(o).toInt() and 0x80 != 0) 2 else 1        // UTF-16 length, skipped
                var len = b.get(o).toInt() and 0xff; o++
                if (len and 0x80 != 0) { len = ((len and 0x7f) shl 8) or (b.get(o).toInt() and 0xff); o++ }
                require(len >= 0 && o + len <= b.limit()) { "string past the end" }   // before any allocation
                String(ByteArray(len) { b.get(o + it) }, Charsets.UTF_8)
            } else {
                var len = b.getShort(o).toInt() and 0xffff; o += 2
                if (len and 0x8000 != 0) { len = ((len and 0x7fff) shl 16) or (b.getShort(o).toInt() and 0xffff); o += 2 }
                require(len >= 0 && o + len * 2L <= b.limit()) { "string past the end" }   // a forged 2^31 length allocated 4 GB before
                String(CharArray(len) { b.getChar(o + it * 2) })
            }
        }
    }

    // ---- APK Signature Scheme v2/v3 --------------------------------------------------------------------------------

    /** SHA-256 (hex) of every signer certificate in the v2 and v3 blocks. Empty for a v1-only APK. */
    fun signerCerts(file: File): Set<String> = RandomAccessFile(file, "r").use { f ->
        val len = f.length(); val tail = ByteArray(minOf(len, 65_557L).toInt()); f.seek(len - tail.size); f.readFully(tail)
        val tb = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
        val eocd = (tail.size - 22 downTo 0).firstOrNull { tb.getInt(it) == 0x06054b50 } ?: return emptySet()
        val cdOffset = tb.getInt(eocd + 16).toLong() and 0xffffffffL
        if (cdOffset < 32) return emptySet()
        val footer = ByteArray(24); f.seek(cdOffset - 24); f.readFully(footer)
        if (String(footer, 8, 16, Charsets.US_ASCII) != "APK Sig Block 42") return emptySet()
        val blockSize = ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN).getLong(0)
        if (blockSize < 24 || blockSize > 64L * 1024 * 1024) return emptySet()
        val block = ByteArray(blockSize.toInt() - 24); f.seek(cdOffset - blockSize); f.readFully(block)   // pairs start right after the leading size field
        val bb = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        val out = LinkedHashSet<String>()
        while (bb.remaining() >= 12) {
            val pairLen = bb.long; val id = bb.int; val valueLen = (pairLen - 4).toInt(); val start = bb.position()
            if (valueLen < 0 || valueLen > bb.remaining()) break
            if (id == 0x7109871a || id == 0xf05368c0.toInt()) runCatching { out += certsOf(ByteBuffer.wrap(block, start, valueLen).slice().order(ByteOrder.LITTLE_ENDIAN)) }
            bb.position(start + valueLen)
        }
        out
    }

    /** signers (len-prefixed) → signer → signed data → [digests, certificates, …]; the first two fields share v2 and v3's layout. */
    private fun certsOf(v: ByteBuffer): List<String> {
        val out = ArrayList<String>()
        val signers = prefixed(v)
        while (signers.hasRemaining()) {
            val signer = prefixed(signers); val signed = prefixed(signer)
            prefixed(signed)                                          // digests
            val certs = prefixed(signed)
            while (certs.hasRemaining()) { val c = prefixed(certs); val der = ByteArray(c.remaining()); c.get(der)
                out += MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) } }
        }
        return out
    }

    private fun prefixed(b: ByteBuffer): ByteBuffer {
        val n = b.int; require(n >= 0 && n <= b.remaining()) { "bad length" }
        val s = b.slice().order(ByteOrder.LITTLE_ENDIAN); s.limit(n); b.position(b.position() + n); return s
    }
}
