package com.assurance.config;
public record ModelConfigInput(String provider,String baseUrl,String modelId,String apiKey,double temperature,int maxTokens,int timeoutSeconds) {
    @Override public String toString(){return "ModelConfigInput[credentials redacted]";}
}
