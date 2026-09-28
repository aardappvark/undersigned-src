package com.undersigned.api

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Seeker Genesis Token test, ported from the fleet's seeker-verify (SgtChecker.isValidSgt + Token2022Parser), so the
 * API and the app agree on every wallet. A mint is an SGT when its mint authority is GT2zuHV…, its MetadataPointer
 * extension names that authority and the GT22s89n… metadata address, and its TokenGroupMember extension's group is that
 * same address. Any one check failing means "not an SGT", whatever the authority.
 */
object Sgt {
    const val MINT_AUTHORITY = "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"
    const val METADATA_ADDRESS = "GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te"
    private const val EXT_METADATA_POINTER = 18
    private const val EXT_TOKEN_GROUP_MEMBER = 23

    data class Mint(val mintAuthority: ByteArray?, val metadataPointerAuthority: ByteArray?, val metadataPointerAddress: ByteArray?, val groupMemberGroup: ByteArray?)

    fun parse(data: ByteArray): Mint? {
        if (data.size < 166) return null
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val auth = if (b.getInt(0) == 1) data.copyOfRange(4, 36) else null
        if ((data[165].toInt() and 0xff) != 1) return null                 // account type 1 = mint
        var mpAuth: ByteArray? = null; var mpAddr: ByteArray? = null; var group: ByteArray? = null
        var o = 166
        while (o + 4 <= data.size) {
            val type = b.getShort(o).toInt() and 0xffff; o += 2
            if (type == 0) break
            val len = b.getShort(o).toInt() and 0xffff; o += 2
            if (o + len > data.size) break
            when (type) {
                EXT_METADATA_POINTER -> if (len >= 64) { mpAuth = data.copyOfRange(o, o + 32); mpAddr = data.copyOfRange(o + 32, o + 64) }
                EXT_TOKEN_GROUP_MEMBER -> if (len >= 64) group = data.copyOfRange(o + 32, o + 64)
            }
            o += len
        }
        return Mint(auth, mpAuth, mpAddr, group)
    }

    fun isValid(mintData: ByteArray): Boolean {
        val m = parse(mintData) ?: return false
        val authority = Base58.decode(MINT_AUTHORITY); val metadata = Base58.decode(METADATA_ADDRESS)
        return m.mintAuthority?.contentEquals(authority) == true && m.metadataPointerAuthority?.contentEquals(authority) == true &&
            m.metadataPointerAddress?.contentEquals(metadata) == true && m.groupMemberGroup?.contentEquals(metadata) == true
    }
}
