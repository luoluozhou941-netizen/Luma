package com.luoluo.luma

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 管钥匙的地方。
 *
 * 钥匙本身只存在于系统的 Keystore 保险箱里，这个类不会、也没办法把钥匙原文
 * 导出来给别人看，只提供"拿钥匙去加密"和"拿钥匙去解密"这两个操作。
 *
 * 钥匙被设置成"必须指纹/面容验证过才能用"，每一次加密、每一次解密，
 * 都需要一次新的验证（不是验证一次之后钥匙就一直开着门）。
 */
object CryptoManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "luma_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    /** 如果钥匙还不存在，就生成一把新的；已经存在就直接用现成的。 */
    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // 每次用钥匙都要重新验证一次，不留"免验证"的时间窗口
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** 准备一个"加密用"的 Cipher，还没做指纹验证，交给 BiometricPrompt 去验证。 */
    fun prepareEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher
    }

    /** 准备一个"解密用"的 Cipher，需要之前加密时用的那串 iv（拆包时会用到）。 */
    fun prepareDecryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return cipher
    }

    /** 把"iv + 加密后的内容"打包成一个字符串，方便存进网页那边的存储里。 */
    fun packEncrypted(iv: ByteArray, encryptedBytes: ByteArray): String {
        val ivB64 = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)
        val dataB64 = android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
        return "$ivB64:$dataB64"
    }

    /** 把打包过的字符串拆开，拆成 iv 和加密内容两部分。 */
    fun unpackEncrypted(packed: String): Pair<ByteArray, ByteArray> {
        val parts = packed.split(":")
        require(parts.size == 2) { "加密数据格式不对，可能是损坏了" }
        val iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
        val data = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
        return iv to data
    }
}
