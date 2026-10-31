package com.assurance.config;
public record ModelConfigView(long version,String provider,String baseUrl,String modelId,boolean hasApiKey,double temperature,int maxTokens,int timeoutSeconds) {}
