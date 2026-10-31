package com.assurance.config;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.*;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Calls the configured real Core model; no simulated production fallback. */
public final class ModelCapabilityService {
    public record CapabilityResult(String name,String status,String detail){}
    private final ModelConfigService configs;
    public ModelCapabilityService(ModelConfigService service){configs=service;}
    public List<CapabilityResult> test(long version) {
        var snapshot=configs.snapshot(version);
        var results=new ArrayList<CapabilityResult>();
        for(String capability:List.of("text","tools","stream")) {
            try {
                var model=model(snapshot);
                switch(capability) {
                    case "text" -> {var message=invoke(model,List.of(UserMessage.builder().content("Reply with a short capability probe acknowledgement.").build()),null); requireText(message);}
                    case "tools" -> checkTools(model);
                    case "stream" -> checkStream(model);
                    default -> throw new IllegalArgumentException();
                }
                results.add(new CapabilityResult(capability,"passed","实际调用通过"));
            } catch(Exception e){results.add(new CapabilityResult(capability,"failed","实际调用未通过："+safeType(e)));}
        }
        return List.copyOf(results);
    }
    public Model model(ModelSnapshot snapshot) {
        var v=snapshot.view();
        return new Model(ModelClientConfig.builder().clientProvider("OpenAI").apiBase(v.baseUrl()).apiKey(snapshot.secret()).timeout(v.timeoutSeconds()).maxRetries(0).verifySsl(true).build(),
            ModelRequestConfig.builder().modelName(v.modelId()).temperature(v.temperature()).maxTokens(v.maxTokens()).build());
    }
    private AssistantMessage invoke(Model model,List<?> messages,Object tools)throws Exception {
        return model.invoke(messages,tools,null,null,null,null,null,null,null,Map.of());
    }
    private void checkTools(Model model)throws Exception {
        var tool=ToolCard.builder().id("capability_echo").name("capability_echo").description("Echo a harmless protocol test value")
            .inputParams(Map.of("type","object","properties",Map.of("message",Map.of("type","string")),"required",List.of("message"))).build();
        var messages=new ArrayList<BaseMessage>();
        messages.add(UserMessage.builder().content("Call capability_echo with message assurance-probe, then acknowledge its returned value. This is an explicit capability test.").build());
        var answer=invoke(model,messages,List.of(tool.toolInfo()));
        if(answer.getToolCalls()==null||answer.getToolCalls().size()!=1)throw new IllegalArgumentException();
        var call=answer.getToolCalls().get(0);
        if(!"capability_echo".equals(call.getName())||!"assurance-probe".equals(new ObjectMapper().readTree(call.getArguments()).path("message").asText()))throw new IllegalArgumentException();
        messages.add(answer);messages.add(new ToolMessage("assurance-probe",call.getId(),"capability_echo"));
        requireText(invoke(model,messages,List.of(tool.toolInfo())));
    }
    private void checkStream(Model model)throws Exception {
        var chunks=model.stream(List.of(UserMessage.builder().content("Reply with a short two-sentence capability acknowledgement.").build()),null,null,null,null,null,null,null,null,Map.of());
        int count=0;var text=new StringBuilder();
        while(chunks.hasNext()) {
            var chunk=chunks.next();
            if(chunk.getContent() instanceof String value&&!value.isEmpty()){count++;text.append(value);}
        }
        if(count<2||text.toString().isBlank())throw new IllegalArgumentException();
    }
    private void requireText(AssistantMessage message) {
        if(message==null||!(message.getContent() instanceof String value)||value.isBlank())throw new IllegalArgumentException();
    }
    private String safeType(Exception e) {
        return e instanceof IllegalArgumentException?"能力结果不符合要求":"模型服务请求失败";
    }
}
