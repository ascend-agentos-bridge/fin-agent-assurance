package com.assurance.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/** Explicit test-only OpenAI-compatible protocol fixture; never a production fallback. */
public final class TestModelServer implements AutoCloseable {
    public record Response(int status, String contentType, List<String> chunks) {
        public static Response json(Object body) {
            try { return new Response(200, "application/json", List.of(JSON.writeValueAsString(body))); }
            catch (Exception e) { throw new IllegalArgumentException(e); }
        }
        public static Response error(int status) {
            return new Response(status, "application/json", List.of("{\"error\":{\"message\":\"test provider error\"}}"));
        }
        public static Response sse(String... content) {
            var chunks = new java.util.ArrayList<String>();
            for (String text : content) {
                try {
                    var frame = Map.of("id", "test-stream", "object", "chat.completion.chunk", "model", "test-only",
                        "choices", List.of(Map.of("index", 0, "delta", Map.of("content", text))));
                    chunks.add("data: " + JSON.writeValueAsString(frame) + "\n\n");
                } catch (Exception e) { throw new IllegalArgumentException(e); }
            }
            chunks.add("data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n");
            return new Response(200, "text/event-stream", chunks);
        }
    }
    public static final ObjectMapper JSON = new ObjectMapper();
    private final HttpServer server;
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private volatile BiFunction<JsonNode,Integer,Response> responder;

    public TestModelServer(BiFunction<JsonNode,Integer,Response> responder) throws IOException {
        this.responder = responder;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                var request = JSON.readTree(exchange.getRequestBody());
                requests.add(request);
                Response response = this.responder.apply(request, requests.size());
                exchange.getResponseHeaders().set("Content-Type", response.contentType());
                exchange.sendResponseHeaders(response.status(), 0);
                try (var output = exchange.getResponseBody()) {
                    for (String chunk : response.chunks()) {
                        output.write(chunk.getBytes(StandardCharsets.UTF_8));
                        output.flush();
                    }
                }
            } finally { exchange.close(); }
        });
    }
    public URI start() { server.start(); return baseUrl(); }
    public URI baseUrl() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"); }
    public List<JsonNode> requests() { return List.copyOf(requests); }
    public void respondWith(BiFunction<JsonNode,Integer,Response> responder) { this.responder = responder; }
    @Override public void close() { server.stop(0); }
    public static Response message(Object content) {
        return Response.json(Map.of("id", "test-completion", "object", "chat.completion", "model", "test-only",
            "choices", List.of(Map.of("index", 0, "message", Map.of("role", "assistant", "content", content), "finish_reason", "stop"))));
    }
    public static Response tool(String name, Map<String,Object> args) {
        try {
            return Response.json(Map.of("id", "test-completion", "object", "chat.completion", "model", "test-only",
                "choices", List.of(Map.of("index", 0, "finish_reason", "tool_calls", "message",
                    Map.of("role", "assistant", "tool_calls", List.of(Map.of("id", "call-" + name, "type", "function",
                        "function", Map.of("name", name, "arguments", JSON.writeValueAsString(args)))))))));
        } catch (Exception e) { throw new IllegalArgumentException(e); }
    }
}
