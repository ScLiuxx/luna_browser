package com.liuli.browser.web.agent

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

enum class AgentSiteMode(val id: String, val label: String, val hint: String) {
    Blocked("blocked", "禁止", "不读取页面，也不执行任何动作"),
    ReadOnly("readonly", "只读", "可总结、查找与问答，不执行点击、填写或滚动"),
    Allowed("allowed", "允许", "可执行受控动作，默认逐条确认");

    companion object {
        fun fromId(id: String?): AgentSiteMode =
            entries.firstOrNull { it.id == id } ?: Allowed
    }
}

data class AgentSitePolicy(
    val mode: AgentSiteMode = AgentSiteMode.Allowed,
    val trustLowRisk: Boolean = false
)

/**
 * P3.2 per-site policy. Only non-default entries are persisted, locally and
 * without any account identifier. Hosts are normalized to lowercase without
 * a "www." prefix so the policy survives the common redirect between them.
 */
class AgentPolicyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var policies by mutableStateOf(load())
        private set

    fun policyFor(rawHost: String?): AgentSitePolicy {
        val host = normalizeHost(rawHost) ?: return AgentSitePolicy()
        return policies[host] ?: AgentSitePolicy()
    }

    fun setMode(rawHost: String?, mode: AgentSiteMode) {
        val host = normalizeHost(rawHost) ?: return
        val updated = policies.toMutableMap()
        if (mode == AgentSiteMode.Allowed) {
            // Allowed without low-risk trust is the default; keep the map small.
            updated.remove(host)
        } else {
            updated[host] = AgentSitePolicy(mode = mode)
        }
        persist(updated)
    }

    fun setTrustLowRisk(rawHost: String?, value: Boolean) {
        val host = normalizeHost(rawHost) ?: return
        val current = policies[host] ?: AgentSitePolicy()
        if (current.mode != AgentSiteMode.Allowed) return
        val updated = policies.toMutableMap()
        if (value) {
            updated[host] = current.copy(trustLowRisk = true)
        } else {
            updated.remove(host)
        }
        persist(updated)
    }

    fun clear() = persist(emptyMap())

    private fun persist(updated: Map<String, AgentSitePolicy>) {
        policies = updated
        val json = JSONObject()
        updated.forEach { (host, policy) ->
            json.put(
                host,
                JSONObject()
                    .put("mode", policy.mode.id)
                    .put("trustLowRisk", policy.trustLowRisk)
            )
        }
        prefs.edit().putString(KEY_POLICIES, json.toString()).apply()
    }

    private fun load(): Map<String, AgentSitePolicy> {
        val raw = prefs.getString(KEY_POLICIES, null) ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, AgentSitePolicy>()
        json.keys().forEach { key ->
            val host = normalizeHost(key) ?: return@forEach
            val item = json.optJSONObject(key) ?: return@forEach
            val mode = AgentSiteMode.fromId(item.optString("mode"))
            val trust = item.optBoolean("trustLowRisk", false) && mode == AgentSiteMode.Allowed
            if (mode != AgentSiteMode.Allowed || trust) {
                result[host] = AgentSitePolicy(mode, trust)
            }
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "liuli_agent_policy"
        private const val KEY_POLICIES = "site_policies"

        /** Accepts a host or a full URL and returns a stable storage key. */
        fun normalizeHost(raw: String?): String? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return null
            val host = if (value.contains("://")) {
                runCatching { Uri.parse(value).host }.getOrNull()
            } else {
                value.substringBefore('/').substringBefore(':')
            }?.lowercase()?.trimEnd('.')
            if (host.isNullOrBlank() || host.contains(' ')) return null
            return host.removePrefix("www.").takeIf { it.isNotBlank() }
        }
    }
}
