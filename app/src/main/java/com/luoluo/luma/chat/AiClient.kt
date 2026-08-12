package com.luoluo.luma.chat

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI通信层。照抄旧JS版 06-providers.js + 08-ai-client.js 的请求格式重写，
 * 逻辑不是凭空设计的，字段名、URL拼法、流式解析规则都跟旧版对得上。
 *
 * 1b范围：只做"发消息收回复"这条主线。
 * 没做的（留到后面）：模型调度中心的usageBindings多用途路由、打字机效果、
 * thinking/reasoning内容的展示、图片消息、工具调用。
 */
object AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * 非流式调用：等完整回复回来再返回。第一刀先用这个跑通主线。
     */
    fun callNonStream(cfg: ProviderConfig, systemPrompt: String, messages: List<ChatMessage>): String {
        val url = buildUrl(cfg)
        val body = buildRequestBody(cfg, systemPrompt, messages, stream = false)

        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(cfg))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw AiClientException("HTTP ${resp.code}: ${bodyStr.take(200)}")
            }
            val json = JSONObject(bodyStr)
            return if (cfg.apiFormat == ApiFormat.ANTHROPIC) {
                extractAnthropicNonStreamText(json)
            } else {
                extractOpenAiNonStreamText(json)
            }
        }
    }

    /**
     * 流式调用，Flow版本——现在这个是主要入口，替代了之前callback版本的streamCall。
     *
     * 之前那版每收到一小段文字就单独 viewModelScope.launch(Dispatchers.Main) 去更新界面，
     * 网络快的时候会有一堆"各自独立"的小任务同时砸向主线程，Compose不能保证每个都被
     * 及时排上渲染帧——实测出现过"内容其实已经收到了，但界面一直卡在省略号，
     * 把窗口切到后台再切回来才刷出来"的问题，本质是更新没有走统一的协程调度队列。
     *
     * 用callbackFlow把同一个阻塞式网络读循环包装成Flow，调用方用.collect{}顺序接收，
     * 这样每次更新天然排在同一条协程队列里，不会互相抢主线程。
     * 网络读这部分仍然是阻塞IO，调用方要记得用 .flowOn(Dispatchers.IO) 包一层。
     */
    fun streamCallFlow(
        cfg: ProviderConfig,
        systemPrompt: String,
        messages: List<ChatMessage>
    ): Flow<String> = callbackFlow {
        val url = buildUrl(cfg)
        val body = buildRequestBody(cfg, systemPrompt, messages, stream = true)

        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(cfg))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = client.newCall(request)

        call.execute().use { resp ->
            if (!resp.isSuccessful) {
                val errText = resp.body?.string() ?: ""
                close(AiClientException("HTTP ${resp.code}: ${errText.take(200)}"))
                return@use
            }

            val source = resp.body?.source()
            if (source == null) {
                close(AiClientException("响应body为空"))
                return@use
            }

            val fullText = StringBuilder()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty() || !trimmed.startsWith("data:")) continue

                val data = trimmed.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val chunk = try {
                    JSONObject(data)
                } catch (e: Exception) {
                    continue
                }

                val deltaText = if (cfg.apiFormat == ApiFormat.ANTHROPIC) {
                    extractAnthropicStreamDelta(chunk)
                } else {
                    extractOpenAiStreamDelta(chunk)
                }

                if (deltaText != null) {
                    fullText.append(deltaText)
                    // trySend走Channel，调用方按顺序收，不会出现"各自抢主线程"的问题
                    trySend(fullText.toString())
                }
            }
        }

        close()
        awaitClose { call.cancel() }
    }

    /**
     * 流式调用：OkHttp没有内置SSE支持，这里手动按行读response body，
     * 遇到 "data: xxx" 就解析一段JSON，累加内容通过onDelta回调出去。
     * onDelta每次传入的是"目前为止累计的全文"，不是增量——方便调用方直接拿去setState显示，
     * 不用自己再维护一份累加状态。
     *
     * 这是阻塞IO调用，调用方要自己丢到Dispatchers.IO里跑（协程），不要在主线程直接调。
     *
     * ⚠️ 保留这个版本仅供参考对照，实际调用请用上面的 streamCallFlow ——
     * 这版本的问题是callback是从IO线程直接调用的，调用方如果用viewModelScope.launch(Dispatchers.Main)
     * 一个个单独发，网络快的时候容易出现更新排不上队、界面卡住不刷新的问题。
     */
    fun streamCall(
        cfg: ProviderConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        onDelta: (fullTextSoFar: String) -> Unit
    ) {
        val url = buildUrl(cfg)
        val body = buildRequestBody(cfg, systemPrompt, messages, stream = true)

        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(cfg))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errText = resp.body?.string() ?: ""
                throw AiClientException("HTTP ${resp.code}: ${errText.take(200)}")
            }

            val source = resp.body?.source() ?: throw AiClientException("响应body为空")
            val fullText = StringBuilder()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty() || !trimmed.startsWith("data:")) continue

                val data = trimmed.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val chunk = try {
                    JSONObject(data)
                } catch (e: Exception) {
                    continue // 解析失败的行直接跳过，跟旧JS版行为一致
                }

                val deltaText = if (cfg.apiFormat == ApiFormat.ANTHROPIC) {
                    extractAnthropicStreamDelta(chunk)
                } else {
                    extractOpenAiStreamDelta(chunk)
                }

                if (deltaText != null) {
                    fullText.append(deltaText)
                    onDelta(fullText.toString())
                }
            }
        }
    }

    // ── 请求构造 ──

    private fun buildUrl(cfg: ProviderConfig): String {
        val base = cfg.baseUrl.trimEnd('/')
        return if (cfg.apiFormat == ApiFormat.ANTHROPIC) "$base/messages" else "$base/chat/completions"
    }

    private fun buildHeaders(cfg: ProviderConfig): okhttp3.Headers {
        return if (cfg.apiFormat == ApiFormat.ANTHROPIC) {
            okhttp3.Headers.Builder()
                .add("Content-Type", "application/json")
                .add("x-api-key", cfg.apiKey)
                .add("anthropic-version", "2023-06-01")
                .build()
        } else {
            okhttp3.Headers.Builder()
                .add("Content-Type", "application/json")
                .add("Authorization", "Bearer ${cfg.apiKey}")
                .build()
        }
    }

    private fun buildRequestBody(
        cfg: ProviderConfig,
        systemPrompt: String,
        messages: List<ChatMessage>,
        stream: Boolean
    ): JSONObject {
        return if (cfg.apiFormat == ApiFormat.ANTHROPIC) {
            // Anthropic原生格式：system单独一个字段，不放进messages数组里；max_tokens必填
            val msgArray = JSONArray()
            messages.forEach { m ->
                msgArray.put(JSONObject().apply {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
            JSONObject().apply {
                put("model", cfg.defaultModel)
                put("max_tokens", 8192)
                put("system", systemPrompt)
                put("messages", msgArray)
                put("stream", stream)
            }
        } else {
            // OpenAI兼容格式：system prompt作为messages数组里第一条
            val msgArray = JSONArray()
            msgArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            messages.forEach { m ->
                msgArray.put(JSONObject().apply {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
            JSONObject().apply {
                put("model", cfg.defaultModel)
                put("messages", msgArray)
                put("stream", stream)
            }
        }
    }

    // ── 响应解析：非流式 ──

    private fun extractOpenAiNonStreamText(json: JSONObject): String {
        val choices = json.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val message = choices.getJSONObject(0).optJSONObject("message") ?: return ""
        return message.optString("content", "")
    }

    private fun extractAnthropicNonStreamText(json: JSONObject): String {
        val blocks = json.optJSONArray("content") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until blocks.length()) {
            val block = blocks.getJSONObject(i)
            if (block.optString("type") == "text") {
                sb.append(block.optString("text", ""))
            }
        }
        return sb.toString()
    }

    // ── 响应解析：流式单条delta ──

    private fun extractOpenAiStreamDelta(chunk: JSONObject): String? {
        val choices = chunk.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return null
        val content = delta.optString("content", "")
        return if (content.isNotEmpty()) content else null
    }

    private fun extractAnthropicStreamDelta(chunk: JSONObject): String? {
        if (chunk.optString("type") != "content_block_delta") return null
        val delta = chunk.optJSONObject("delta") ?: return null
        if (delta.optString("type") != "text_delta") return null
        val text = delta.optString("text", "")
        return if (text.isNotEmpty()) text else null
    }
}

class AiClientException(message: String) : Exception(message)
