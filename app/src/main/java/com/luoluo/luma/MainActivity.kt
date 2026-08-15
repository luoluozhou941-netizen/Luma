package com.luoluo.luma

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.luoluo.luma.chat.ChatScreen
import com.luoluo.luma.role.RoleManager
import com.luoluo.luma.role.RoleScreen
import com.luoluo.luma.ui.theme.LumaTheme
import java.io.File
import java.io.FileOutputStream

/**
 * 1a步：只验证Compose工具链能不能跑起来，界面先放一个占位文字。
 *
 * WebView那套（LumaJsBridge、指纹加解密相关方法）先原样留在这里没删，
 * 但这一步不再初始化webView、也不会显示WebView了，assets/luma/整个文件夹已经删掉。
 * 这些方法目前是"挂着但没人调用"的死代码，等以后要迁移加密功能到Compose版本时再处理，
 * 现在留着是为了不丢失原来的实现细节，方便对照。
 */
class MainActivity : FragmentActivity() {

    // 原来是 lateinit，现在没有WebView可用了，改成可空类型，
    // 防止onDestroy这些生命周期方法访问到未初始化的属性而崩溃。
    private var webView: WebView? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    class LumaJsBridge(private val activity: MainActivity) {
        @JavascriptInterface
        fun exportData(jsonData: String, fileName: String) {
            activity.runOnUiThread {
                try {
                    val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: activity.filesDir
                    dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { it.write(jsonData.toByteArray(Charsets.UTF_8)) }
                } catch (e: Exception) {
                    // 死代码阶段，先不处理
                }
            }
        }

        @JavascriptInterface
        fun requestEncrypt(plainText: String, requestId: String) {
            activity.runOnUiThread {
                activity.showBiometricThenEncrypt(plainText, requestId)
            }
        }

        @JavascriptInterface
        fun requestDecrypt(packedCipherText: String, requestId: String) {
            activity.runOnUiThread {
                activity.showBiometricThenDecrypt(packedCipherText, requestId)
            }
        }
    }

    private fun showBiometricThenEncrypt(plainText: String, requestId: String) {
        try {
            val cipher = CryptoManager.prepareEncryptCipher()
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authedCipher = result.cryptoObject!!.cipher!!
                            val encryptedBytes = authedCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                            val packed = CryptoManager.packEncrypted(authedCipher.iv, encryptedBytes)
                            sendCryptoResultToJs(requestId, success = true, payload = packed)
                        } catch (e: Exception) {
                            sendCryptoResultToJs(requestId, success = false, payload = "加密失败: ${e.message}")
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        sendCryptoResultToJs(requestId, success = false, payload = "验证未通过: $errString")
                    }

                    override fun onAuthenticationFailed() {}
                }
            )
            prompt.authenticate(biometricPromptInfo(), cryptoObject)
        } catch (e: Exception) {
            sendCryptoResultToJs(requestId, success = false, payload = "准备加密时出错: ${e.message}")
        }
    }

    private fun showBiometricThenDecrypt(packedCipherText: String, requestId: String) {
        try {
            val (iv, encryptedBytes) = CryptoManager.unpackEncrypted(packedCipherText)
            val cipher = CryptoManager.prepareDecryptCipher(iv)
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authedCipher = result.cryptoObject!!.cipher!!
                            val plainBytes = authedCipher.doFinal(encryptedBytes)
                            sendCryptoResultToJs(requestId, success = true, payload = String(plainBytes, Charsets.UTF_8))
                        } catch (e: Exception) {
                            sendCryptoResultToJs(requestId, success = false, payload = "解密失败: ${e.message}")
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        sendCryptoResultToJs(requestId, success = false, payload = "验证未通过: $errString")
                    }

                    override fun onAuthenticationFailed() {}
                }
            )
            prompt.authenticate(biometricPromptInfo(), cryptoObject)
        } catch (e: Exception) {
            sendCryptoResultToJs(requestId, success = false, payload = "准备解密时出错: ${e.message}")
        }
    }

    private fun biometricPromptInfo(): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("验证身份")
            .setSubtitle("需要验证一下才能继续")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

    private fun sendCryptoResultToJs(requestId: String, success: Boolean, payload: String) {
        runOnUiThread {
            val safeRequestId = org.json.JSONObject.quote(requestId)
            val safePayload = org.json.JSONObject.quote(payload)
            val js = "window.LumaCryptoCallback && window.LumaCryptoCallback($safeRequestId, $success, $safePayload)"
            webView?.evaluateJavascript(js, null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35上系统强制edge-to-edge，内容默认会画到状态栏/导航栏底下。
        // 这一句负责让状态栏图标颜色跟着主题自动适配深浅色；
        // 真正"内容自己让出空间不被遮挡"那部分在下面LumaApp()的Modifier里用safeDrawingPadding()做。
        enableEdgeToEdge()

        // 文件选择器的launcher先留着注册，虽然1a阶段用不上，1b/1c做导入功能时会用到。
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val uris = if (data?.clipData != null) {
                    Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
                } else {
                    data?.data?.let { arrayOf(it) } ?: arrayOf()
                }
                fileChooserCallback?.onReceiveValue(uris)
            } else {
                fileChooserCallback?.onReceiveValue(null)
            }
            fileChooserCallback = null
        }

        setContent {
            LumaTheme {
                LumaApp()
            }
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}

/**
 * 1e：这里做了个最简单的"导航"——就是个布尔开关切两个Composable，
 * 不是引入正式的Navigation库。角色管理这一个跳转场景用不上那么重的东西，
 * 真要上Navigation-Compose，等界面多到需要正式路由系统的时候再加。
 */
@Composable
fun LumaApp() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            // 关键这行：内容自动避开状态栏顶部、导航栏/手势条底部、挖孔屏这些系统占用区域，
            // 不加这个，内容会一直顶到屏幕最边缘，跟系统状态栏/导航栏的图标撞在一起。
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        val context = LocalContext.current
        var showRoleManager by remember { mutableStateOf(false) }
        var activeRoleId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            activeRoleId = RoleManager.getActiveRoleId(context)
        }

        if (showRoleManager) {
            RoleScreen(onDone = {
                activeRoleId = RoleManager.getActiveRoleId(context)
                showRoleManager = false
            })
        } else {
            activeRoleId?.let { roleId ->
                ChatScreen(
                    roleId = roleId,
                    onOpenRoleManager = { showRoleManager = true }
                )
            }
        }
    }
}
