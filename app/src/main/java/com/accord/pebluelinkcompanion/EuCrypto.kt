package com.accord.pebluelinkcompanion

import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher

object EuCrypto {
    fun encryptPassword(password: String, n: String, e: String): String {
        val modulus = BigInteger(1, Base64.decode(n, Base64.URL_SAFE))
        val exponent = BigInteger(1, Base64.decode(e, Base64.URL_SAFE))
        val spec = RSAPublicKeySpec(modulus, exponent)
        val factory = KeyFactory.getInstance("RSA")
        val publicKey = factory.generatePublic(spec)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(password.toByteArray())
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun genRanHex(len: Int): String {
        val chars = "0123456789abcdef"
        return (1..len).map { chars.random() }.joinToString("")
    }
}
