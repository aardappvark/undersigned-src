package com.undersigned.app.ui

import com.undersigned.app.ui.components.tagLabelRes
import com.undersigned.protocol.ReviewMemo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TagLabelTest {
    @Test fun everyProtocolTagHasItsOwnLabel() {
        val res = ReviewMemo.Tag.entries.map { t -> assertNotNull("no label for ${t.wire}", tagLabelRes(t.wire)); tagLabelRes(t.wire) }
        assertEquals("two tags share a label", res.size, res.toSet().size)
    }

    @Test fun unknownWireFallsBack() {
        assertNull(tagLabelRes("from-a-newer-client"))
    }
}
