package com.assurance.config;

import com.assurance.AssuranceApplication;
import com.assurance.storage.Database;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ModelConfigTest {
    @TempDir Path temp;
    private ModelConfigService service() { return new ModelConfigService(new Database(temp.resolve("config.db")),new CredentialVault(temp.resolve("master.key"))); }
    private ModelConfigInput input(String key,String model) { return new ModelConfigInput("openai_compatible","https://example.invalid/v1",model,key,0.2,2048,60); }

    @Test void credentialsAreEncryptedAndNeverReturnedInConfiguration() throws Exception {
        var service=service();
        var view=service.save(input("sensitive-fixture-key-8899","model-a"));
        assertTrue(view.version()>0,"saving must create an immutable version");
        assertTrue(view.hasApiKey());
        assertFalse(new ObjectMapper().writeValueAsString(view).contains("sensitive-fixture-key-8899"));
        for(Path path:Files.list(temp).toList()) {
            if(Files.isRegularFile(path)) assertFalse(new String(Files.readAllBytes(path),java.nio.charset.StandardCharsets.ISO_8859_1).contains("sensitive-fixture-key-8899"));
        }
        assertEquals("sensitive-fixture-key-8899",service.snapshot(view.version()).secret());
        assertFalse(input("sensitive-fixture-key-8899","a").toString().contains("sensitive-fixture-key-8899"));
        assertFalse(service.snapshot(view.version()).toString().contains("sensitive-fixture-key-8899"));
    }
    @Test void updateAndRestartPreserveOldCredentialAndModelSnapshots() {
        var service=service();
        var old=service.save(input("old-private-fixture","model-a"));
        var newer=service.save(input("new-private-fixture","model-b"));
        assertTrue(newer.version()>old.version());
        var restarted=service();
        assertEquals("model-a",restarted.snapshot(old.version()).view().modelId());
        assertEquals("old-private-fixture",restarted.snapshot(old.version()).secret());
        assertEquals("model-b",restarted.latest().orElseThrow().modelId());
        assertEquals("new-private-fixture",restarted.snapshot(newer.version()).secret());
    }
    @Test void invalidProtocolAndMissingCredentialAreRejectedWithoutEcho() {
        var service=service();
        assertThrows(IllegalArgumentException.class,()->service.save(input("","model-a")));
        var exception=assertThrows(IllegalArgumentException.class,()->service.save(new ModelConfigInput("openai_compatible","file:///private/data","a","private-fixture",0.2,2048,60)));
        assertFalse(exception.toString().contains("private-fixture"));
        assertTrue(service.latest().isEmpty());
    }
    @Test void vaultRejectsTamperingAndKeepsMasterKeyAcrossRestart() {
        var vault=new CredentialVault(temp.resolve("key"));
        var encrypted=vault.encrypt("test-private-value");
        assertFalse(encrypted.isBlank());
        assertEquals("test-private-value",new CredentialVault(temp.resolve("key")).decrypt(encrypted));
        assertThrows(IllegalArgumentException.class,()->vault.decrypt(encrypted.substring(0,encrypted.length()-4)+"AAAA"));
    }
    @Test void backendStartsWithoutModelAndReturnsUnconfiguredState() throws Exception {
        try(var context=new SpringApplicationBuilder(AssuranceApplication.class).properties(Map.of("server.port","0","assurance.data-dir",temp.toString(),"logging.level.root","WARN")).run("--server.port=0","--assurance.data-dir="+temp)) {
            int port=((WebServerApplicationContext)context).getWebServer().getPort();
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/model-config")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode());
            assertTrue(response.body().contains("\"configured\":false"));
            assertFalse(response.body().contains("apiKey"));
        }
    }
}
