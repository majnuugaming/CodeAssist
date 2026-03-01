package com.tyron.code.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AIAgentServiceTest {
    private lateinit var context: Context
    private lateinit var service: AIAgentService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = AIAgentService(context)
        // start with clean files directory so we know what to expect
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @After
    fun tearDown() {
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @Test
    fun `create file with no content`() = runBlocking {
        val result = service.processPrompt("create file test.txt")
        assertThat(result.isSuccess).isTrue()
        val msg = result.getOrThrow()
        assertThat(msg).contains("Created file")
        val created = File(context.filesDir, "test.txt")
        assertThat(created.exists()).isTrue()
        assertThat(created.readText()).isEmpty()
    }

    @Test
    fun `create file with content`() = runBlocking {
        val result = service.processPrompt("create file notes.md with Hello world")
        assertThat(result.isSuccess).isTrue()
        val file = File(context.filesDir, "notes.md")
        assertThat(file.exists()).isTrue()
        assertThat(file.readText()).isEqualTo("Hello world")
    }

    @Test
    fun `delete file command`() = runBlocking {
        val file = File(context.filesDir, "temp.txt")
        file.writeText("data")
        assertThat(file.exists()).isTrue()
        val result = service.processPrompt("delete file temp.txt")
        assertThat(result.isSuccess).isTrue()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `copy and paste file command`() = runBlocking {
        val src = File(context.filesDir, "a.txt")
        src.writeText("abc")
        val result = service.processPrompt("copy file a.txt to b.txt")
        assertThat(result.isSuccess).isTrue()
        val dest = File(context.filesDir, "b.txt")
        assertThat(dest.exists()).isTrue()
        assertThat(dest.readText()).isEqualTo("abc")
    }

    @Test
    fun `move file command`() = runBlocking {
        val src = File(context.filesDir, "old.txt")
        src.writeText("hey")
        val result = service.processPrompt("move file old.txt to new.txt")
        assertThat(result.isSuccess).isTrue()
        val newFile = File(context.filesDir, "new.txt")
        assertThat(newFile.exists()).isTrue()
        assertThat(newFile.readText()).isEqualTo("hey")
        assertThat(src.exists()).isFalse()
    }

    @Test
    fun `edit file command`() = runBlocking {
        val file = File(context.filesDir, "edit.txt")
        file.writeText("initial")
        val result = service.processPrompt("edit file edit.txt with updated content")
        assertThat(result.isSuccess).isTrue()
        assertThat(file.readText()).isEqualTo("updated content")
    }

    @Test
    fun `unrecognized command falls back to generateCode`() = runBlocking {
        // since generateCode will try network and fail due to missing config, we expect failure
        val result = service.processPrompt("please generate something")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("AI API not configured")
    }
}
