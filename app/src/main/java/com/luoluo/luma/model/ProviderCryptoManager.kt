package com.luoluo.luma.model

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 专门给provider的apiKey这类"需要一直在后台被读取、不适合每次都弹指纹"的敏感数据用的加密器。
 *
 * 跟根目录那个CryptoManager.kt是两回事、用的是两把不同的钥匙：
 * - CryptoManager那把钥匙要求每次用都验证指纹/面容——给日记/信件这种"我主动要看的时候
 *   才验证"的场景用的。
 * - 这把钥匙不要求验证——因为apiKey是每次发消息都要在背后读一次的东西，如果也要求验证，
 *   等于每发一条消息就要弹一次指纹，没法用。
 *
 * 安全性上的差别：这把钥匙依然只存在系统的Keystore保险箱里，不会被导出、不会明文躺在
 * 硬盘上——文件被别人拷走照样打不开。少的只是"每次用都要再验证一遍指纹"这一层，
 * 换来的是app能在背后正常发消息不被打断。这是当前(方案A)选定的取舍。
 */
object ProviderCryptoManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "luma_provider_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

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
            // 关键区别：不设置setUserAuthenticationRequired(true)，
            // 用这把钥匙不需要每次都弹指纹验证。
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** 明文进，打包好的密文字符串出（iv+密文，Base64拼在一起） */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ivB64 = android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP)
        val dataB64 = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
        return "$ivB64:$dataB64"
    }

    /**
     * 打包过的密文字符串进，明文出。
     * 解不开（比如数据损坏，或者是加这套机制之前存的老明文数据）会抛异常，
     * 调用方要自己接住，别让整个provider列表因为一条数据读不了就崩掉。
     */
    fun decrypt(packed: String): String {
        val parts = packed.split(":")
        require(parts.size == 2) { "加密数据格式不对" }
        val iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
        val data = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }
}