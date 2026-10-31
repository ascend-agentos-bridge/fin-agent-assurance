package com.assurance.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class CredentialVault {
    private final SecretKeySpec key;
    private final SecureRandom random=new SecureRandom();
    public CredentialVault(Path keyFile) {
        synchronized(CredentialVault.class) {
            try {
                Path path=keyFile.toAbsolutePath().normalize();
                Files.createDirectories(path.getParent());
                if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {
                    byte[] bytes=new byte[32]; random.nextBytes(bytes);
                    try(var stream=Files.newByteChannel(path,java.util.Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                        stream.write(ByteBuffer.wrap(bytes));
                    }
                    Arrays.fill(bytes,(byte)0);
                }
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)) throw new IllegalArgumentException();
                byte[] bytes=Files.readAllBytes(path);
                if(bytes.length!=32) throw new IllegalArgumentException();
                Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));
                key=new SecretKeySpec(bytes,"AES"); Arrays.fill(bytes,(byte)0);
            } catch(Exception e){throw new IllegalStateException("凭据主密钥不可用");}
        }
    }
    public String encrypt(String secret) {
        try {
            byte[] iv=new byte[12];random.nextBytes(iv);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
            byte[] plain=secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] encrypted=cipher.doFinal(plain); Arrays.fill(plain,(byte)0);
            return "v1."+Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted).array());
        } catch(Exception e){throw new IllegalStateException("凭据加密失败");}
    }
    public String decrypt(String encrypted) {
        try {
            if(!encrypted.startsWith("v1."))throw new IllegalArgumentException();
            byte[] all=Base64.getDecoder().decode(encrypted.substring(3));
            if(all.length<29)throw new IllegalArgumentException();
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(all,0,12)));
            return new String(cipher.doFinal(Arrays.copyOfRange(all,12,all.length)),java.nio.charset.StandardCharsets.UTF_8);
        } catch(Exception e){throw new IllegalArgumentException("凭据无法解密");}
    }
}
