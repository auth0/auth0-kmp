package com.auth0.kmp.core.dpop

/** Converts a DER-encoded ECDSA signature to raw `R‖S` (64 bytes) for JOSE `ES256`. */
internal fun derToRawSignature(der: ByteArray): ByteArray {
    var offset = 0
    require(der[offset++] == 0x30.toByte()) { "Expected DER SEQUENCE" }
    val (seqLen, seqLenBytes) = decodeDerLength(der, offset)
    offset += seqLenBytes
    require(seqLen + offset == der.size) { "DER length mismatch" }

    require(der[offset++] == 0x02.toByte()) { "Expected DER INTEGER for R" }
    val (rLen, rLenBytes) = decodeDerLength(der, offset)
    offset += rLenBytes
    val r = der.copyOfRange(offset, offset + rLen)
    offset += rLen

    require(der[offset++] == 0x02.toByte()) { "Expected DER INTEGER for S" }
    val (sLen, sLenBytes) = decodeDerLength(der, offset)
    offset += sLenBytes
    val s = der.copyOfRange(offset, offset + sLen)

    return trimTo32(r) + trimTo32(s)
}

private fun trimTo32(value: ByteArray): ByteArray {
    var v = value
    if (v.size > 1 && v[0] == 0x00.toByte() && (v[1].toInt() and 0x80) == 0x80) {
        v = v.copyOfRange(1, v.size)
    }
    if (v.size == 32) return v
    val out = ByteArray(32)
    v.copyInto(out, destinationOffset = 32 - v.size)
    return out
}

private fun decodeDerLength(data: ByteArray, offset: Int): Pair<Int, Int> {
    val first = data[offset].toInt() and 0xFF
    if (first and 0x80 == 0) return first to 1
    val numBytes = first and 0x7F
    var len = 0
    for (i in 0 until numBytes) {
        len = (len shl 8) or (data[offset + 1 + i].toInt() and 0xFF)
    }
    return len to (1 + numBytes)
}
