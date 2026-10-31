package com.assurance.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ModelConfigController {
    private final ModelConfigService configs;private final ModelCapabilityService capabilities;
    public ModelConfigController(ModelConfigService configs,ModelCapabilityService capabilities){this.configs=configs;this.capabilities=capabilities;}
    @GetMapping("/model-config") public Map<String,Object> current(){return configs.latest().<Map<String,Object>>map(c->Map.of("configured",true,"config",c)).orElse(Map.of("configured",false));}
    @PutMapping(value="/model-config",consumes="application/json") public ModelConfigView save(@RequestBody ModelConfigInput input){return configs.save(input);}
    public record TestRequest(Long version){}
    @PostMapping(value="/model-tests",consumes="application/json") public Map<String,Object> test(@RequestBody TestRequest request) {
        long version=request.version()!=null?request.version():configs.latest().orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"请先配置模型服务")).version();
        return Map.of("version",version,"testedAt",Instant.now().toString(),"results",capabilities.test(version));
    }
}
