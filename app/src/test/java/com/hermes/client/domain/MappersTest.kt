package com.hermes.client.domain

import com.hermes.client.data.network.MessageDto
import com.hermes.client.data.network.SessionDto
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    @Test fun session_dto_maps_to_domain() {
        val s = SessionDto(sessionId = "s1", title = "Hi", model = "opus", messageCount = 2).toDomain()
        assertEquals("s1", s.id)
        assertEquals("Hi", s.title)
        assertEquals(2, s.messageCount)
    }

    @Test fun message_dto_maps_role_and_text() {
        val m = MessageDto(id = 1, role = "assistant", content = "hello").toDomain()
        assertEquals(Role.ASSISTANT, m.role)
        assertEquals("hello", m.text)
        assertEquals(false, m.isStreaming)
    }

    /**
     * FastAPI dashboard row: top-level `model` is often null (overwritten / never set) and there
     * is no `provider` column. The real route lives in `model_config` JSON.
     */
    @Test fun session_resolves_model_and_provider_from_model_config() {
        val s = SessionDto(
            sessionId = "s1",
            title = "Named",
            model = null,
            provider = null,
            modelConfig = """{"model":"anthropic/claude-sonnet-5","gateway_runtime":{"provider":"deepinfra"}}""",
            billingProvider = "deepinfra",
        ).toDomain()
        assertEquals("anthropic/claude-sonnet-5", s.model)
        assertEquals("deepinfra", s.provider)
        assertEquals("Named", s.title)
    }

    /** MoA: billing_provider is the aggregator vendor; model_config.provider is the virtual "moa". */
    @Test fun session_prefers_model_config_provider_over_billing_provider() {
        val s = SessionDto(
            sessionId = "s1",
            title = "t",
            model = "coding-prod",
            provider = null,
            modelConfig = """{"model":"coding-prod","provider":"moa"}""",
            billingProvider = "anthropic",
        ).toDomain()
        assertEquals("coding-prod", s.model)
        assertEquals("moa", s.provider)
    }
}
