package io.wenyou.textquest

import io.wenyou.textquest.data.ai.AiDirector
import io.wenyou.textquest.data.engine.GameEngine
import io.wenyou.textquest.data.llm.ChatClient
import io.wenyou.textquest.data.model.*
import io.wenyou.textquest.data.repo.LocalLibrary
import io.wenyou.textquest.data.repo.ShareCode
import io.wenyou.textquest.ui.vm.PlayViewModel
import io.wenyou.textquest.ui.vm.PlayStage
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class RegressionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun aiStateChangesSurviveParsing() {
        val scene = AiDirector(ChatClient()).parseScene(
            """{"text":"门开了","choices":[{"text":"进入[to:hall]"}],"state":[{"char":"c","metric":"trust","delta":5}]}"""
        )
        assertEquals("hall", scene.choices.single().next)
        assertEquals(5.0, scene.stateEffects.single().delta, 0.0)
    }

    @Test fun proseAfterBodyMarkerIsPreserved() {
        val scene = AiDirector(ChatClient()).parseScene("我先构思场景\n【正文】\n门开了。\n----\n他走了进来。")
        assertEquals("门开了。\n\n他走了进来。", scene.text)
    }

    @Test fun shareCodesRejectTruncatedAndOversizedPayloads() {
        val bundle = AppBundle(characters = listOf(CharacterData("c", "角色")))
        assertEquals(bundle, ShareCode.decode(ShareCode.encode(bundle)))
        val legacy = Base64.getUrlEncoder().withoutPadding().encodeToString(
            AppJson.encodeToString(AppBundle.serializer(), bundle).toByteArray(Charsets.UTF_8))
        assertEquals(bundle, ShareCode.decode("WY1:$legacy"))
        fun compressed(bytes: ByteArray): ByteArray {
            val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
            try {
                val out = ByteArrayOutputStream()
                DeflaterOutputStream(out, deflater).use { it.write(bytes) }
                return out.toByteArray()
            } finally { deflater.end() }
        }
        fun code(bytes: ByteArray) = "WY2:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val valid = compressed("{}".toByteArray())
        assertEquals(AppBundle(), ShareCode.decode(code(valid)))
        assertNull(ShareCode.decode(code(valid.copyOf(valid.size - 1))))
        assertNull(ShareCode.decode(code(compressed(ByteArray(8 * 1024 * 1024 + 1) { 32 }))))
        assertEquals("", ShareCode.encode(AppBundle(characters = listOf(
            CharacterData("c", "large", background = "x".repeat(8 * 1024 * 1024))
        ))))
    }

    @Test fun qrChunksCarryBookIdentityAndRemainBackwardCompatible() {
        val first = ShareCode.qrChunks("a".repeat(900))
        val second = ShareCode.qrChunks("b".repeat(900))
        assertEquals(3, first.size)
        assertNotEquals(ShareCode.parseChunk(first[0])!!.bookId, ShareCode.parseChunk(second[0])!!.bookId)
        val parsed = first.mapNotNull(ShareCode::parseChunk)
        assertEquals("a".repeat(900), ShareCode.assembleChunks(parsed.associate { it.index to it.data }, parsed[0].total))
        assertEquals(ShareCode.QrChunk(1, 2, "old"), ShareCode.parseChunk("wyq:1/2|old"))
    }

    @Test fun albumQrSelectionAssemblesOneCompleteBookInAnyOrder() {
        val noisy = ByteArray(4000).also { java.util.Random(1).nextBytes(it) }
        val code = ShareCode.encode(AppBundle(characters = listOf(
            CharacterData("c", "角色", background = Base64.getEncoder().encodeToString(noisy))
        )))
        val chunks = ShareCode.qrChunks(code)
        assertTrue(chunks.size > 1)
        assertEquals(code, ShareCode.assembleQrTexts(chunks.reversed()))
        assertNull(ShareCode.assembleQrTexts(chunks.dropLast(1)))

        val other = ShareCode.qrChunks(ShareCode.encode(AppBundle(stories = listOf(Story("s", "剧情")))))
        assertNull(ShareCode.assembleQrTexts(other + chunks.reversed()))
    }

    @Test fun repeatedSharedImportReportsExistingContent() = runBlocking {
        val library = LocalLibrary(temp.newFolder())
        val bundle = AppBundle(
            characters = listOf(CharacterData("c", "角色", bottomRuleIds = listOf("r"))),
            stories = listOf(Story("s", "剧情", characterIds = listOf("c"))),
            bottomRules = listOf(BottomRule("r", "规则", "内容"))
        )
        assertEquals(LocalLibrary.SharedImportResult(3, 0), library.importShared(bundle))
        assertEquals(LocalLibrary.SharedImportResult(0, 3), library.importShared(bundle))
    }

    @Test fun missingCharacterDoesNotReadGlobalVariablesOrFlags() {
        val state = SessionState("s", variables = mapOf("trust" to 99.0), flags = setOf("met"))
        assertFalse(GameEngine.evaluate(state, listOf(Cond(name = "trust", value = 50.0, charId = "missing"))))
        assertFalse(GameEngine.evaluate(state, listOf(Cond(CondType.FLAG_TRUE, "met", charId = "missing"))))
        assertTrue(GameEngine.evaluate(state, listOf(Cond(CondType.FLAG_FALSE, "met", charId = "missing"))))
    }

    @Test fun responsePreservesBothReasoningAndContent() = runBlocking {
        for (body in listOf(
            "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thought\",\"content\":\"answer\"}}]}\n\ndata: [DONE]\n\n",
            """{"choices":[{"message":{"reasoning_content":"thought","content":"answer"}}]}"""
        )) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(body.toResponseBody()).build()
            }.build()
            try {
                val content = StringBuilder()
                val reasoning = StringBuilder()
                val result = ChatClient(client).streamText(
                    ApiProfile("p", "test", baseUrl = "http://localhost/v1", model = "test"), "", "",
                    onDelta = { content.append(it) }, onReasoning = { reasoning.append(it) }
                )
                assertEquals("answer", result.content)
                assertEquals("thought", result.reasoning)
                assertEquals("answer", content.toString())
                assertEquals("thought", reasoning.toString())
            } finally {
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test fun concurrentWritesPreserveEveryRecord() = runBlocking {
        val dir = temp.newFolder()
        val library = LocalLibrary(dir)
        coroutineScope {
            repeat(80) { i -> launch(Dispatchers.Default) {
                library.upsertCharacter(CharacterData("c$i", "Character $i"))
            } }
        }
        assertEquals(80, library.characters.value.size)
        assertEquals(library.characters.value, LocalLibrary(dir).characters.value)
    }

    @Test fun choiceHistoryAndLoadedNarrationRemainConsistent() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val library = LocalLibrary(temp.newFolder())
            val story = Story("s", "story", nodes = mapOf(
                "start" to StoryNode("start", text = "开场", choices = listOf(
                    ChoiceData("留下", "@self", effects = listOf(Effect(EffectType.ADD_VAR, "count", 1.0))),
                    ChoiceData("离开", "end", effects = listOf(Effect(EffectType.ROLL, "die", to = 6.0)))
                )),
                "end" to StoryNode("end", NodeKind.ENDING, text = "结局")
            ))
            library.upsertStory(story)
            val director = AiDirector(ChatClient())
            val vm = PlayViewModel("s", "new", library, director) { null }
            runCurrent()
            vm.chooseAuthored(0)
            assertEquals(listOf("开场", "留下"), vm.ui.value.session!!.history.map { it.text })
            assertEquals(1.0, vm.ui.value.session!!.variables["count"]!!, 0.0)
            val state = vm.ui.value.session!!
            library.upsertSave(SaveSlot("save", "saved", 0, 0, state))
            val loaded = PlayViewModel("s", "save", library, director) { null }
            runCurrent()
            assertEquals(state.history, loaded.ui.value.session!!.history)
            loaded.chooseAuthored(1)
            assertEquals(1, loaded.ui.value.session!!.history.count { it.text == "离开" })
            assertEquals(1, loaded.ui.value.session!!.history.count { it.kind == EntryKind.SYSTEM })
            val ending = loaded.ui.value.session!!
            library.upsertSave(SaveSlot("end", "ending", 0, 0, ending))
            val end = PlayViewModel("s", "end", library, director) { null }
            runCurrent()
            assertEquals(ending.history, end.ui.value.session!!.history)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun aiChoicesSurviveSaveAndLoadWithoutRegeneration() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val library = LocalLibrary(temp.newFolder())
            val story = Story("ai", "AI story", nodes = mapOf(
                "start" to StoryNode("start", NodeKind.AI, title = "AI node")
            ))
            library.upsertStory(story)
            val choices = listOf(ChoiceData("推门", "hall"), ChoiceData("等待", "@self"))
            val state = GameEngine.newSession(story).copy(
                history = listOf(LogEntry(EntryKind.NARRATION, text = "门出现了")),
                pendingAiChoices = choices,
                aiAwaitingChoice = true
            )
            library.upsertSave(SaveSlot("save-ai", "saved", 0, 0, state))
            val vm = PlayViewModel("ai", "save-ai", library, AiDirector(ChatClient())) { null }
            runCurrent()
            assertEquals(PlayStage.AUTHORED, vm.ui.value.stage)
            assertEquals(choices.map { it.text }, vm.ui.value.pendingAiChoices.map { it.text })
            assertEquals(state.history, vm.ui.value.session!!.history)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedWritesKeepMemoryAndExistingData() = runBlocking {
        val dir = temp.newFolder()
        val library = LocalLibrary(dir)
        val character = CharacterData("c", "original")
        library.upsertCharacter(character)
        val file = File(dir, "characters.json")
        val before = file.readText()
        try {
            library.upsertCharacter(character.copy(initial = CharacterState(metrics = mapOf("bad" to Double.NaN))))
            fail("Serialization failure must reach the caller")
        } catch (_: IOException) { }
        assertEquals(listOf(character), library.characters.value)
        assertEquals(before, file.readText())
        // A directory at the target path reliably simulates replacement failure, even as administrator.
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        File(file, "keep").writeText(before)
        try {
            library.upsertCharacter(character.copy(name = "lost"))
            fail("Write failure must reach the caller")
        } catch (_: IOException) { }
        assertEquals(listOf(character), library.characters.value)
        assertEquals(before, File(file, "keep").readText())
        assertNotNull(library.writeError.value)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".pending") })
    }

    @Test fun automaticNodeCyclesStopAndLargeDiceDoNotOverflow() = runTest {
        val roll = Effect(EffectType.ROLL, "die", to = Int.MAX_VALUE.toDouble())
        val state = GameEngine.applyEffects(SessionState("s"), listOf(roll, roll.copy(charId = "c"))).state
        assertTrue(state.variables.getValue("die") in 1.0..Int.MAX_VALUE.toDouble())
        assertTrue(state.characterStates.getValue("c").metrics.getValue("die") in 0.0..100.0)
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val library = LocalLibrary(temp.newFolder())
            library.upsertStory(Story("cycle", "cycle", nodes = mapOf(
                "start" to StoryNode("start", text = "A", endTarget = "b"),
                "b" to StoryNode("b", text = "B", endTarget = "start")
            )))
            val vm = PlayViewModel("cycle", "new", library, AiDirector(ChatClient())) { null }
            runCurrent()
            assertEquals(PlayStage.STOPPED, vm.ui.value.stage)
            assertEquals("剧情循环", vm.ui.value.stoppedTitle)
            assertEquals(2, vm.ui.value.session!!.history.size)
        } finally { Dispatchers.resetMain() }
    }
}
