package com.assurance.core;

import com.assurance.support.TestModelServer;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CoreContractTest {
    @TempDir Path root;
    @Test void registeredSkillIsReadAndRealToolRunsThroughCore() throws Exception {
        Path dir = Files.createDirectories(root.resolve("materials"));
        Path doc = dir.resolve("SKILL.md");
        Files.writeString(doc, "---\nname: materials\ndescription: Validate synthetic credit documents\n---\nINDEPENDENT-SKILL-EVIDENCE: use bank_check.\n");
        var readCount = new AtomicInteger();
        var bankCount = new AtomicInteger();
        var reader = function("readFile", "Read an approved Skill document", "path", args -> {
            try { readCount.incrementAndGet(); return Files.readString(Path.of(args.get("path").toString())); }
            catch (Exception e) { throw new IllegalArgumentException(e); }
        });
        var bank = function("bank_check", "Read synthetic bank materials", "caseId", args -> {
            assertEquals("complete", args.get("caseId"));
            bankCount.incrementAndGet();
            return Map.of("mock", true, "missingMaterials", List.of(), "source", "bank-tool-executed");
        });
        try (var provider = new TestModelServer((request,n) -> switch(n) {
            case 1 -> TestModelServer.tool("readFile", Map.of("path", doc.toString()));
            case 2 -> TestModelServer.tool("bank_check", Map.of("caseId", "complete"));
            default -> TestModelServer.message("Materials complete; human approval remains required.");
        })) {
            String session = UUID.randomUUID().toString();
            var agent = new CoreAgentFactory().create("contract-"+session, provider.start().toString(), "test-key-not-real", "test-only", root, List.of(reader,bank));
            assertNotNull(agent.getSkillUtil(), "Application must enable Core Skill mechanism");
            assertTrue(agent.getSkillUtil().hasSkill(), "Application must actually register the Skill directory");
            var result = Runner.runAgent(agent, Map.of("query", "Validate case complete", "conversation_id", session), null, null);
            assertEquals(1, readCount.get());
            assertEquals(1, bankCount.get());
            assertTrue(provider.requests().get(0).path("messages").toString().contains("materials"));
            assertTrue(provider.requests().get(1).path("messages").toString().contains("INDEPENDENT-SKILL-EVIDENCE"));
            assertTrue(provider.requests().get(2).path("messages").toString().contains("bank-tool-executed"));
            assertTrue(result.toString().contains("human approval"));
            Runner.release(session);
        }
    }
    @Test void modelConsumesActualMultiChunkStream() throws Exception {
        try (var provider = new TestModelServer((request,n) -> TestModelServer.Response.sse("hello", " world"))) {
            provider.start();
            var model = model(provider);
            var chunks = model.stream(List.of(Map.of("role","user","content","say hello")), null, null,null,null,null,null,null,null,Map.of());
            var text = new StringBuilder();
            int count=0;
            while (chunks.hasNext()) { var chunk=chunks.next(); if(chunk.getContent()!=null) { text.append(chunk.getContent()); count++; } }
            assertEquals("hello world", text.toString());
            assertTrue(count >= 2);
            assertTrue(provider.requests().get(0).path("stream").asBoolean());
        }
    }
    @Test void providerFailureIsNotAFixedSuccessAnswer() throws Exception {
        try (var provider = new TestModelServer((request,n) -> TestModelServer.Response.error(429))) {
            provider.start();
            assertThrows(Exception.class, () -> model(provider).invoke(List.of(Map.of("role","user","content","analyze")),null,null,null,null,null,null,null,null,Map.of()));
        }
    }
    private Model model(TestModelServer server) {
        return new Model(ModelClientConfig.builder().clientProvider("OpenAI").apiKey("test-key-not-real")
            .apiBase(server.baseUrl().toString()).maxRetries(0).timeout(5).build(),
            ModelRequestConfig.builder().modelName("test-only").temperature(0.0).maxTokens(100).build());
    }
    private Tool function(String name,String description,String parameter,java.util.function.Function<Map<String,Object>,Object> body) {
        return new LocalFunction(ToolCard.builder().id(name).name(name).description(description)
            .inputParams(Map.of("type","object","properties",Map.of(parameter,Map.of("type","string")),"required",List.of(parameter))).build(),body);
    }
}
