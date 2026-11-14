package win.transgirls.nieve.systems.network

import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Encryption {
    fun newSecret(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun serverHash(serverId: String, secret: ByteArray, publicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(serverId.toByteArray(Charsets.ISO_8859_1))
        digest.update(secret)
        digest.update(publicKey)
        return BigInteger(digest.digest()).toString(16)
    }

    fun rsaEncrypt(publicKey: ByteArray, data: ByteArray): ByteArray {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKey))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }

    fun aes(mode: Int, secret: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/CFB8/NoPadding")
        cipher.init(mode, SecretKeySpec(secret, "AES"), IvParameterSpec(secret))
        return cipher
    }
}
