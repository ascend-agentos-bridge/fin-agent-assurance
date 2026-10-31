package com.assurance.config;

import com.assurance.storage.Database;
import com.assurance.support.TestModelServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ModelCapabilityTest {
    @TempDir Path temp;
    private ModelConfigService configs(){return new ModelConfigService(new Database(temp.resolve("db")),new CredentialVault(temp.resolve("key")));}
    @Test void testsActualTextToolRoundTripAndMultiChunkStreaming() throws Exception {
        try(var provider=new TestModelServer((req,n)-> {
            if(req.path("stream").asBoolean()) return TestModelServer.Response.sse("probe", " complete");
            if(n==2) return TestModelServer.tool("capability_echo",Map.of("message","assurance-probe"));
            return TestModelServer.message("probe complete");
        })) {
            var configs=configs();
            long version=configs.save(new ModelConfigInput("openai_compatible",provider.start().toString(),"test-only","test-fixture-key",0.2,512,5)).version();
            var results=new ModelCapabilityService(configs).test(version);
            assertEquals(3,results.size());
            assertTrue(results.stream().allMatch(r->r.status().equals("passed")),results.toString());
            assertEquals(4,provider.requests().size());
            assertTrue(provider.requests().get(2).toString().contains("assurance-probe"));
        }
    }
    @Test void httpSuccessWithNoToolCallDoesNotPassToolCapability() throws Exception {
        try(var provider=new TestModelServer((req,n)->req.path("stream").asBoolean()?TestModelServer.Response.sse("a","b"):TestModelServer.message("no tool call"))) {
            var configs=configs();
            long version=configs.save(new ModelConfigInput("openai_compatible",provider.start().toString(),"test-only","test-fixture-key",0.2,512,5)).version();
            var results=new ModelCapabilityService(configs).test(version);
            assertEquals(3,results.size());
            assertEquals("failed",results.stream().filter(r->r.name().equals("tools")).findFirst().orElseThrow().status());
        }
    }
}
