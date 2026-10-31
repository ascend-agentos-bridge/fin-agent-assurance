package com.assurance.config;

import com.assurance.storage.Database;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Path;

@Configuration
public class ApplicationConfiguration {
    @Bean Database database(@Value("${assurance.data-dir:${ASSURANCE_DATA_DIR:data}}")String directory){return new Database(Path.of(directory).resolve("assurance.db"));}
    @Bean CredentialVault credentialVault(@Value("${assurance.master-key:${ASSURANCE_MASTER_KEY_FILE:}}")String key,@Value("${assurance.data-dir:${ASSURANCE_DATA_DIR:data}}")String directory){return new CredentialVault(key.isBlank()?Path.of(directory).resolve("secrets/master.key"):Path.of(key));}
    @Bean ModelConfigService modelConfigService(Database db,CredentialVault vault){return new ModelConfigService(db,vault);}
    @Bean ModelCapabilityService modelCapabilityService(ModelConfigService service){return new ModelCapabilityService(service);}
}
