package com.ljyh.mei.data.network.netease

import java.io.IOException
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class NcblCompressionTest {
    @Test
    fun nativeLoadFailureBecomesARecoverableReportingFailure() {
        for (failure in listOf(UnsatisfiedLinkError("missing JNI"), NoClassDefFoundError("codec"))) {
            val thrown = assertThrows(IOException::class.java) {
                compressNcblBody(byteArrayOf(1)) { throw failure }
            }
            assertSame(failure, thrown.cause)
        }
    }

    @Test
    fun fatalVirtualMachineErrorsAreNotSwallowed() {
        val failure = OutOfMemoryError("test")
        val thrown = assertThrows(OutOfMemoryError::class.java) {
            compressNcblBody(byteArrayOf(1)) { throw failure }
        }
        assertSame(failure, thrown)
    }
}
