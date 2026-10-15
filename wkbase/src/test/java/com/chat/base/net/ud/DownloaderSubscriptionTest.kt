package com.chat.base.net.ud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * B-2 回归：订阅与终态转换必须原子。
 *
 * 不走 OkHttp、不碰主线程 Handler——直接对 Task 的订阅协议建模，断言
 * "终态之后的 trySubscribe 返回终态结果而不是静默入队"。这正是旧实现丢掉
 * 订阅者 B 的那一步。
 */
class DownloaderSubscriptionTest {

    private fun newTask(): Task = Task(
        url = "https://example.com/a.pdf",
        request = okhttp3.Request.Builder().url("https://example.com/a.pdf").build(),
        file = File("/tmp/a.pdf")
    )

    private fun cb(): Triple<OnDownload?, OnComplete?, OnFail?> =
        Triple(null, null, null)

    @Test
    fun subscribeBeforeTerminalIsQueuedAndDrainedOnce() {
        val task = newTask()
        assertNull(task.trySubscribe(cb()))
        val drained = task.markTerminalAndDrain(Task.Terminal.Success(File("/tmp/a.pdf")))
        assertEquals(1, drained.size)
        // 第二次 drain 不应再返回同一个订阅者（已清空），避免重复通知
        val again = task.markTerminalAndDrain(Task.Terminal.Success(File("/tmp/a.pdf")))
        assertEquals(0, again.size)
    }

    @Test
    fun lateSubscriberAfterSuccessGetsReplayedResult() {
        val task = newTask()
        task.markTerminalAndDrain(Task.Terminal.Success(File("/tmp/a.pdf")))
        // B 在终态之后才订阅：必须拿到终态结果，而不是被静默入队（B-2）
        val terminal = task.trySubscribe(cb())
        assertNotNull(terminal)
        assertEquals(true, terminal is Task.Terminal.Success)
    }

    @Test
    fun lateSubscriberAfterFailureGetsReplayedResult() {
        val task = newTask()
        task.markTerminalAndDrain(Task.Terminal.Failure("http 404"))
        val terminal = task.trySubscribe(cb())
        assertNotNull(terminal)
        assertEquals("http 404", (terminal as Task.Terminal.Failure).msg)
    }

    @Test
    fun lateSubscriberIsNotAddedToTheDeadList() {
        val task = newTask()
        task.markTerminalAndDrain(Task.Terminal.Failure("boom"))
        task.trySubscribe(cb())
        // 迟到订阅者不得落进那个再也不被读取的列表
        assertEquals(0, task.callbacks.size)
    }
}
