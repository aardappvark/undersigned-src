package com.undersigned.protocol

/**
 * The non-opinion half of the trust panel: facts the DEVICE can verify about an installed app,
 * computed on-device from PackageManager, never uploaded. Displayed next to the ledger score
 * so a user sees "what people signed" and "what the phone can prove" side by side.
 *
 * Nothing here is advice and nothing is a verdict; the UI shows the fact and the delta, e.g.
 * "signing certificate changed since v12" — a fact — not "this app is unsafe".
 */
data class SafetyFacts(
    val packageName: String,
    val versionCode: Long,
    /** SHA-256 of the signing certificate; a change between versions is the single strongest on-device warning sign. */
    val signerSha256: String,
    val dangerousPermissions: List<String>,
    /** Present when a previous snapshot of this package exists on this device. */
    val delta: Delta? = null,
) {
    data class Delta(
        val signerChanged: Boolean,
        val permissionsAdded: List<String>,
        val permissionsRemoved: List<String>,
    )
}
