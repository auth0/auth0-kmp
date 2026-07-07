package com.auth0.kmp.core.primitives

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Rs256VerifyTest {

    private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    // RFC 7515 Appendix A.2
    private val header = "eyJhbGciOiJSUzI1NiJ9"
    private val payload =
        "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFt" +
            "cGxlLmNvbS9pc19yb290Ijp0cnVlfQ"
    private val nB64 =
        "ofgWCuLjybRlzo0tZWJjNiuSfb4p4fAkd_wWJcyQoTbji9k0l8W26mPddxHmfHQp" +
            "-Vaw-4qPCJrcS2mJPMEzP1Pt0Bm4d4QlL-yRT-SFd2lZS-pCgNMsD1W_YpRPEw" +
            "OWvG6b32690r2jZ47soMZo9wGzjb_7OMg0LOL-bSf63kpaSHSXndS5z5rexMdbB" +
            "YUsLA9e-KXBdQOS-UTo7WTBEMa2R2CapHg665xsmtdVMTBQY4uDZlxvb3qCo5Zw" +
            "Kh9kG4LT6_I5IhlJH7aGhyxXFvUK-DWNmoudF8NAco9_h9iaGNj8q2ethFkMLs9" +
            "1kzk2PAcDTW9gb54h4FRWyuXpoQ"
    private val eB64 = "AQAB"
    private val sigB64 =
        "cC4hiUPoj9Eetdgtv3hF80EGrhuB__dzERat0XF9g2VtQgr9PJbu3XOiZj5RZmh7" +
            "AAuHIm4Bh-0Qc_lF5YKt_O8W2Fp5jujGbds9uJdbF9CUAr7t1dnZcAcQjbKBYNX" +
            "4BAynRFdiuB--f_nZLgrnbyTyWzO75vRK5h6xBArLIARNPvkSjtQBMHlb1L07Qe" +
            "7K0GarZRmB_eSN9383LcOLn6_dO--xi12jzDwusC-eOkHWEsqtFZESc6BfI7noO" +
            "PqvhJ1phCnvWh6IeYI2w9QOYEUipUTI8np6LbgGY9Fs98rqVt5AXLIhWkWywlVm" +
            "tVrBp0igcN_IoypGlUPQGe77Rw"

    private val signedData = "$header.$payload".encodeToByteArray()
    private val signature = b64.decode(sigB64)
    private val modulus = b64.decode(nB64)
    private val exponent = b64.decode(eB64)

    @Test
    fun verify_returns_true_for_valid_rfc7515_signature() {
        assertTrue(verifyRs256(signedData, signature, modulus, exponent))
    }

    @Test
    fun verify_returns_false_for_tampered_data() {
        val tampered = signedData.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertFalse(verifyRs256(tampered, signature, modulus, exponent))
    }

    @Test
    fun verify_returns_false_for_tampered_signature() {
        val tampered = signature.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertFalse(verifyRs256(signedData, tampered, modulus, exponent))
    }

    @Test
    fun verify_returns_false_for_wrong_key() {
        val wrongModulus = modulus.copyOf().also { it[100] = (it[100] + 1).toByte() }
        assertFalse(verifyRs256(signedData, signature, wrongModulus, exponent))
    }

    @Test
    fun verify_returns_false_for_malformed_key_without_crashing() {
        assertFalse(verifyRs256(signedData, signature, byteArrayOf(0x01, 0x02), exponent))
    }
}
