package com.assurance.core;

import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class CoreAgentFactory {
    public ReActAgent create(String id,String baseUrl,String apiKey,String modelId,Path skillRoot,List<Tool> tools) {
        var agent=new ReActAgent(AgentCard.builder().id(id).name("Credit analysis").description("Synthetic credit assistance").build());
        var config=ReActAgentConfig.builder().maxIterations(20).maxParallelToolCalls(1).streamMaxRetries(0)
            .promptTemplate(List.of(Map.of("role","system","content","Read the appropriate Skill before using its tools. Never approve or disburse a loan."))).build()
            .configureModelClient("OpenAI",apiKey,baseUrl,modelId,true).configureContextEngine(null,null,false);
        var sysOperation=SysOperationCard.builder().id("skills-"+id).mode(OperationMode.LOCAL)
            .workConfig(LocalWorkConfig.builder().workDir(skillRoot.toAbsolutePath().toString()).build()).build();
        var registered=Runner.resourceMgr().addSysOperation(sysOperation,id,id);
        if(registered.isError()) throw new IllegalStateException("Core Skill filesystem registration failed");
        config.setSysOperationId(sysOperation.getId());
        agent.configure(config);
        agent.registerSkill(skillRoot.toString(),skillRoot);
        for(Tool tool:tools) {
            tool.getCard().setId(id+"-"+tool.getCard().getName());
            var added=Runner.resourceMgr().addTool(tool,id,id);
            if(added.isError()) throw new IllegalStateException("Core tool registration failed");
            agent.getAbilityManager().add(tool.getCard());
        }
        return agent;
    }
}
