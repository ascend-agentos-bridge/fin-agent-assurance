package com.assurance.config;

import com.assurance.storage.Database;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class ModelConfigService {
    private final Database db; private final CredentialVault vault;
    public ModelConfigService(Database db,CredentialVault vault) {
        this.db=db;this.vault=vault;
        db.execute("CREATE TABLE IF NOT EXISTS model_configs(version INTEGER PRIMARY KEY AUTOINCREMENT,provider TEXT NOT NULL,base_url TEXT NOT NULL,model_id TEXT NOT NULL,key_cipher TEXT NOT NULL,temperature REAL NOT NULL,max_tokens INTEGER NOT NULL,timeout_seconds INTEGER NOT NULL)");
    }
    public synchronized ModelConfigView save(ModelConfigInput input) {
        validate(input);
        String secret=input.apiKey();
        if(secret==null||secret.isBlank()) {
            var previous=latest().orElseThrow(()->new IllegalArgumentException("请提供模型服务密钥"));
            secret=snapshot(previous.version()).secret();
        }
        if(secret.length()>8192)throw new IllegalArgumentException("模型服务密钥格式无效");
        String base=input.baseUrl().trim().replaceAll("/+$","");
        try(var c=db.open()) {
            c.setAutoCommit(false);
            try(var s=c.prepareStatement("INSERT INTO model_configs(provider,base_url,model_id,key_cipher,temperature,max_tokens,timeout_seconds) VALUES(?,?,?,?,?,?,?)")) {
                s.setString(1,input.provider());s.setString(2,base);s.setString(3,input.modelId().trim());s.setString(4,vault.encrypt(secret));
                s.setDouble(5,input.temperature());s.setInt(6,input.maxTokens());s.setInt(7,input.timeoutSeconds());s.executeUpdate();
            }
            long version;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT last_insert_rowid()")){r.next();version=r.getLong(1);}
            c.commit();
            return new ModelConfigView(version,input.provider(),base,input.modelId().trim(),true,input.temperature(),input.maxTokens(),input.timeoutSeconds());
        } catch(SQLException e){throw new IllegalStateException("模型配置保存失败");}
    }
    public Optional<ModelConfigView> latest() {
        try(var c=db.open();var s=c.createStatement();var r=s.executeQuery("SELECT * FROM model_configs ORDER BY version DESC LIMIT 1")) {
            return r.next()?Optional.of(view(r)):Optional.empty();
        } catch(SQLException e){throw new IllegalStateException("模型配置读取失败");}
    }
    public ModelSnapshot snapshot(long version) {
        try(var c=db.open();var s=c.prepareStatement("SELECT * FROM model_configs WHERE version=?")) {
            s.setLong(1,version);
            try(var r=s.executeQuery()) {
                if(!r.next())throw new IllegalArgumentException("模型配置版本不存在");
                return new ModelSnapshot(view(r),vault.decrypt(r.getString("key_cipher")));
            }
        } catch(SQLException e){throw new IllegalStateException("模型配置读取失败");}
    }
    private ModelConfigView view(ResultSet r)throws SQLException {
        return new ModelConfigView(r.getLong("version"),r.getString("provider"),r.getString("base_url"),r.getString("model_id"),true,r.getDouble("temperature"),r.getInt("max_tokens"),r.getInt("timeout_seconds"));
    }
    private void validate(ModelConfigInput in) {
        if(in==null||!"openai_compatible".equals(in.provider()))throw new IllegalArgumentException("仅支持已验证的 OpenAI-compatible 协议");
        try {
            var uri=URI.create(in.baseUrl().trim());
            if(!java.util.Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPath().endsWith("/chat/completions"))throw new IllegalArgumentException();
        } catch(Exception e){throw new IllegalArgumentException("Base URL 格式无效，请填写服务根地址并保留正常 TLS 校验");}
        if(in.modelId()==null||in.modelId().isBlank()||in.modelId().length()>200||in.modelId().chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("模型 ID 无效");
        if(!Double.isFinite(in.temperature())||in.temperature()<0||in.temperature()>2||in.maxTokens()<1||in.maxTokens()>32768||in.timeoutSeconds()<1||in.timeoutSeconds()>300)throw new IllegalArgumentException("模型调用参数超出范围");
    }
}
