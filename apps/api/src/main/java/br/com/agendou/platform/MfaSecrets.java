package br.com.agendou.platform;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MfaSecrets {
    private final byte[] key;
    public MfaSecrets(@Value("${agendou.mfa-encryption-key:}") String encoded) {
        key=encoded.isBlank()?null:Base64.getDecoder().decode(encoded);
        if(key!=null && key.length!=32) throw new IllegalArgumentException("MFA encryption key must contain 32 bytes.");
    }
    public String encrypt(UUID user,String secret) {
        requireKey(); byte[] nonce=new byte[12]; new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce)+"."+crypt(Cipher.ENCRYPT_MODE,user,nonce,secret.getBytes(StandardCharsets.UTF_8));
    }
    public String decrypt(UUID user,String encrypted) {
        requireKey(); String[] parts=encrypted.split("\\.");
        return new String(Base64.getDecoder().decode(crypt(Cipher.DECRYPT_MODE,user,Base64.getDecoder().decode(parts[0]),Base64.getDecoder().decode(parts[1]))),StandardCharsets.UTF_8);
    }
    private String crypt(int mode,UUID user,byte[] nonce,byte[] input) {
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(user.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(cipher.doFinal(input));
        } catch(java.security.GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"MFA indisponível. Contate a operação.");
        }
    }
    private void requireKey() {
        if(key==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configure a chave de proteção do MFA na API.");
    }
}
