package com.chat.uikit.chat.provider

import com.chat.uikit.chat.msgmodel.WKDocumentShareContent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSharePreviewRepositoryTest {
    private class FakeTransport : DocumentSharePreviewRepository.Transport {
        data class Call(val url: String, val callback: (Int, String?) -> Unit)
        val calls = mutableListOf<Call>()
        override fun get(url: String, token: String, spaceId: String, callback: (Int, String?) -> Unit) {
            calls += Call(url, callback)
        }
    }

    private fun content() = WKDocumentShareContent().apply { docId = "doc_1"; spaceId = "space_2"; kind = "doc" }
    private fun repository(
        transport: FakeTransport,
        launch: ((() -> Unit) -> Unit) = { it() },
        beforeCachedDelivery: () -> Unit = {},
        now: () -> Long = { 1L },
        session: () -> DocumentSharePreviewRepository.Session = { DocumentSharePreviewRepository.Session("u1", "t1", 7L) }
    ) =
        DocumentSharePreviewRepository(
            transport = transport,
            now = now,
            session = session,
            config = DocumentSharePreviewRepository.ConfigSource { "https://api.example.test/base" to "https://web.example.test" },
            sessionEvents = object : DocumentSharePreviewRepository.SessionEvents {
                override fun add(listener: () -> Unit) = Unit
                override fun remove(listener: () -> Unit) = Unit
            },
            launch = launch,
            beforeCachedDelivery = beforeCachedDelivery
        )

    @Test fun closeBeforeQueuedTaskStartsDoesNotCallTransport() {
        val transport = FakeTransport()
        val queued = mutableListOf<() -> Unit>()
        val repository = repository(transport, launch = { queued += it })
        repository.fetch(content()) {}
        repository.close()
        queued.single()()
        assertTrue(transport.calls.isEmpty())
    }

    @Test fun cancellingOneCoalescedSubscriberLeavesTheNextSubscriberActive() {
        val transport = FakeTransport()
        val repository = repository(transport)
        var first = 0
        var second = 0
        val subscription = repository.fetch(content()) { first++ }
        repository.fetch(content()) { result -> if (result.state == DocumentSharePreviewRepository.State.denied) second++ }
        subscription.cancel()
        transport.calls.single().callback(403, "not-json")
        assertEquals(0, first)
        assertEquals(1, second)
    }

    @Test fun unavailableHttpStatusDoesNotParseMalformedBody() {
        listOf(403 to listOf(null, "", "<html>"), 404 to listOf(null, "", "<html>"), 410 to listOf(null, "", "<html>")).forEach { (code, bodies) ->
            bodies.forEach { body ->
                val transport = FakeTransport()
                val repository = repository(transport)
                var state: DocumentSharePreviewRepository.State? = null
                repository.fetch(content()) { state = it.state }
                transport.calls.single().callback(code, body)
                assertEquals(if (code == 403) DocumentSharePreviewRepository.State.denied else DocumentSharePreviewRepository.State.unavailable, state)
                repository.close()
            }
        }
    }

    @Test fun callbackCanCancelLaterSubscriberAfterInflightRemoval() {
        val transport = FakeTransport()
        val repository = repository(transport)
        var first = 0
        var second = 0
        lateinit var secondSubscription: DocumentSharePreviewRepository.Subscription
        repository.fetch(content()) {
            first++
            secondSubscription.cancel()
        }
        secondSubscription = repository.fetch(content()) { second++ }
        transport.calls.single().callback(404, "")
        assertEquals(1, first)
        assertEquals(0, second)
        repository.close()
    }

    @Test fun invalidatedSessionDropsOldInFlightResponse() {
        val transport = FakeTransport()
        val repository = repository(transport)
        var results = 0
        repository.fetch(content()) { results++ }
        repository.invalidateSession()
        transport.calls.single().callback(404, "")
        assertEquals(0, results)
        repository.close()
    }

    @Test fun invalidatingInsideFirstSubscriberSkipsLaterSubscriber() {
        val transport = FakeTransport()
        val repository = repository(transport)
        var first = 0
        var second = 0
        repository.fetch(content()) { first++; repository.invalidateSession() }
        repository.fetch(content()) { second++ }
        transport.calls.single().callback(404, "")
        assertEquals(1, first)
        assertEquals(0, second)
        repository.close()
    }

    @Test fun invalidationBetweenCacheLookupAndDeliveryDropsCachedResult() {
        val transport = FakeTransport()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val afterFirstResult = AtomicInteger(0)
        val repository = repository(transport, beforeCachedDelivery = {
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
        })
        repository.fetch(content()) {}
        transport.calls.single().callback(404, "")

        val deliveryThread = Thread {
            repository.fetch(content()) { afterFirstResult.incrementAndGet() }
        }
        deliveryThread.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        repository.invalidateSession()
        release.countDown()
        deliveryThread.join(2000)
        assertFalse(deliveryThread.isAlive)
        assertEquals(0, afterFirstResult.get())
        repository.close()
    }

    @Test fun requestUrlUsesJvmUriAndPreservesValidatedSpaceParameter() {
        val transport = FakeTransport()
        val repository = repository(transport)
        repository.fetch(content()) {}
        assertEquals("https://api.example.test/api/v1/docs/doc_1/content?sp=space_2", transport.calls.single().url)
        repository.close()
    }

    @Test fun repeatedAttachStyleFetchesShareCacheUntilTtlExpires() {
        val transport = FakeTransport()
        var clock = 10L
        val repository = repository(transport, now = { clock })
        var deliveries = 0
        repository.fetch(content()) { deliveries++ }
        transport.calls.single().callback(404, "")
        repeat(4) { repository.fetch(content(), false) { deliveries++ } }
        assertEquals(1, transport.calls.size)
        assertEquals(5, deliveries)

        clock += 30_001
        repository.fetch(content(), false) { deliveries++ }
        assertEquals(2, transport.calls.size)
        transport.calls.last().callback(404, "")
        assertEquals(6, deliveries)
        repository.close()
    }

    @Test fun forceRefreshBypassesCacheExactlyOnceThenUpdatesIt() {
        val transport = FakeTransport()
        var clock = 100L
        val repository = repository(transport, now = { clock })
        repository.fetch(content()) {}
        transport.calls.single().callback(404, "")
        repository.fetch(content(), true) {}
        assertEquals(2, transport.calls.size)
        transport.calls.last().callback(403, "")
        var state: DocumentSharePreviewRepository.State? = null
        repository.fetch(content(), false) { state = it.state }
        assertEquals(2, transport.calls.size)
        assertEquals(DocumentSharePreviewRepository.State.denied, state)
        repository.close()
    }

    @Test fun sessionChangeNeverDeliversOrReusesPreviousSessionResult() {
        val transport = FakeTransport()
        var activeSession = DocumentSharePreviewRepository.Session("u1", "t1", 1L)
        val repository = repository(transport, session = { activeSession })
        var oldDeliveries = 0
        repository.fetch(content()) { oldDeliveries++ }
        val oldCall = transport.calls.single()
        activeSession = DocumentSharePreviewRepository.Session("u2", "t2", 2L)
        repository.invalidateSession()
        oldCall.callback(404, "")
        assertEquals(0, oldDeliveries)

        var newDeliveries = 0
        repository.fetch(content()) { newDeliveries++ }
        assertEquals(2, transport.calls.size)
        transport.calls.last().callback(404, "")
        repository.fetch(content()) { newDeliveries++ }
        assertEquals(2, transport.calls.size)
        assertEquals(2, newDeliveries)
        repository.close()
    }

    @Test fun freshCacheIsDeliveredSynchronouslyBeforeFetchReturns() {
        val transport = FakeTransport()
        val repository = repository(transport)
        repository.fetch(content()) {}
        transport.calls.single().callback(404, "")
        var insideFetch = true
        var synchronous = false
        repository.fetch(content()) { synchronous = insideFetch }
        insideFetch = false
        assertTrue(synchronous)
        assertEquals(1, transport.calls.size)
        repository.close()
    }
}
