package com.chat.uikit.chat.provider

import com.chat.base.msgcontent.WKFileContent
import com.chat.base.utils.WKCacheKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException

class WKFileProviderPathSecurityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test fun rejectsTraversalAndInvalidChannelIds() {
        val root = tempDir("chat-cache-root")
        listOf("../sibling", "..", ".", "a/b", "a\\b", "bad\u0000id", "").forEach {
            assertNull(ChatFilePathSecurity.resolve(root, 1, it, "cache.pdf"))
        }
    }

    @Test fun rejectsTypeChannelTargetAndDownloaderTempSymlinkEscapes() {
        val root = tempDir("chat-cache-root")
        val outside = tempDir("chat-cache-outside")
        val payload = File(outside, "payload").apply { writeText("outside") }

        val typeRoot = tempDir("chat-cache-type-link")
        symlink(File(typeRoot, "1"), outside)
        assertNull(ChatFilePathSecurity.resolve(typeRoot, 1, "channel", "cache.pdf"))

        val channelRoot = tempDir("chat-cache-channel-link")
        File(channelRoot, "1").mkdirs()
        symlink(File(File(channelRoot, "1"), "channel"), outside)
        assertNull(ChatFilePathSecurity.resolve(channelRoot, 1, "channel", "cache.pdf"))

        val channel = File(File(root, "1"), "channel").apply { mkdirs() }
        symlink(File(channel, "cache.pdf"), payload)
        assertNull(ChatFilePathSecurity.resolve(root, 1, "channel", "cache.pdf"))
        File(channel, "cache.pdf").delete()

        val partial = File(channel, ".partial")
        symlink(File(channel, ".partial"), outside)
        assertNull(ChatFilePathSecurity.resolve(root, 1, "channel", "cache.pdf"))
        File(channel, ".partial").delete()

        File(channel, ".partial").mkdirs()
        symlink(File(partial, "cache.pdf.part"), payload)
        assertNull(ChatFilePathSecurity.resolve(root, 1, "channel", "cache.pdf"))
        File(partial, "cache.pdf.part").delete()

        symlink(File(partial, "cache.pdf.part.copytmp"), payload)
        assertNull(ChatFilePathSecurity.resolve(root, 1, "channel", "cache.pdf"))
        assertEquals("outside", payload.readText())
    }

    @Test fun cacheResolutionIgnoresForgedLocalPathAndUsesIdentityAndSize() {
        val root = tempDir("chat-cache-root")
        val identity = WKCacheKeys.chatFileIdentity(1, "channel", "message-a")
        val expectedName = WKFileProvider.buildCacheFileNameForIdentity(identity, "pdf", "report.pdf")
        val outside = File(root, "outside.pdf").apply { writeText("1234") }
        val otherMessageIdentity = WKCacheKeys.chatFileIdentity(1, "channel", "message-b")
        val otherMessageName = WKFileProvider.buildCacheFileNameForIdentity(otherMessageIdentity, "pdf", "report.pdf")
        val otherMessageFile = File(File(File(root, "1"), "channel").apply { mkdirs() }, otherMessageName)
            .apply { writeText("same-size") }

        // Even an existing forged localPath (size zero or exact) and another message's managed
        // cache cannot stand in for this message's missing identity-derived cache.
        listOf(0L, outside.length(), otherMessageFile.length()).forEach { size ->
            val content = fileContent("report.pdf", size, outside.absolutePath)
            assertNull(ChatFileCacheResolution.resolveManaged(root, 1, "channel", identity, content))
            content.localPath = otherMessageFile.absolutePath
            assertNull(ChatFileCacheResolution.resolveManaged(root, 1, "channel", identity, content))
        }

        val expected = File(File(root, "1/channel"), expectedName).apply { writeText("1234") }
        val exactContent = fileContent("report.pdf", expected.length(), outside.absolutePath)
        assertEquals(expected, ChatFileCacheResolution.resolveManaged(root, 1, "channel", identity, exactContent))
        val mismatch = fileContent("report.pdf", expected.length() + 1, outside.absolutePath)
        assertNull(ChatFileCacheResolution.resolveManaged(root, 1, "channel", identity, mismatch))
        val unknownSize = fileContent("report.pdf", 0L, outside.absolutePath)
        assertEquals(expected, ChatFileCacheResolution.resolveManaged(root, 1, "channel", identity, unknownSize))
    }

    @Test fun legacyCleanupRejectsTraversalAndSymlinksAndDeletesOnlyRegularTarget() {
        val root = tempDir("chat-cache-root")
        val escapeDir = File(root.parentFile, "escape").apply { mkdirs() }
        val outside = File(escapeDir, "legacy.pdf").apply { writeText("keep") }
        val content = fileContent("legacy.pdf", 4L)
        WKFileProvider.deleteLegacyChatCache(root, 1, "../../escape", "message", content)
        assertEquals("keep", outside.readText())

        val channel = File(root, "1/channel").apply { mkdirs() }
        val legacy = File(channel, "legacy.pdf")
        symlink(legacy, outside)
        WKFileProvider.deleteLegacyChatCache(root, 1, "channel", "message", content)
        assertTrue(outside.exists())
        assertTrue(legacy.exists())
        legacy.delete()

        legacy.writeText("data")
        WKFileProvider.deleteLegacyChatCache(root, 1, "channel", "message", content)
        assertFalse(legacy.exists())

        val directory = File(channel, "legacy.pdf").apply { mkdirs() }
        WKFileProvider.deleteLegacyChatCache(root, 1, "channel", "message", content)
        assertTrue(directory.isDirectory)
    }

    @Test fun normalManagedPathIsStableAcrossResolution() {
        val root = tempDir("chat-cache-root")
        val first = ChatFilePathSecurity.resolve(root, 1, "channel", "abc.pdf")
        assertTrue(first!!.isAbsolute)
        assertEquals(first, ChatFilePathSecurity.resolve(root, 1, "channel", "abc.pdf"))
    }

    private fun fileContent(name: String, size: Long, localPath: String? = null) = WKFileContent().apply {
        this.name = name
        extension = "pdf"
        this.size = size
        this.localPath = localPath
    }

    private fun tempDir(prefix: String) = temporaryFolder.newFolder(prefix).canonicalFile

    private fun symlink(link: File, target: File) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (e: UnsupportedOperationException) {
            assumeNoException("Symlink tests require host filesystem symlink support", e)
        } catch (e: SecurityException) {
            assumeNoException("Symlink creation is denied by the host", e)
        } catch (e: FileAlreadyExistsException) {
            throw AssertionError("Test symlink path already exists: $link", e)
        } catch (e: FileSystemException) {
            throw AssertionError("Filesystem could not create test symlink $link -> $target", e)
        } catch (e: Exception) {
            throw AssertionError("Could not create test symlink $link -> $target", e)
        }
    }
}
