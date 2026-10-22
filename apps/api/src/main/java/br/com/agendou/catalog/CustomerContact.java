package br.com.agendou.catalog;

import java.text.Normalizer;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class CustomerContact {
 private CustomerContact(){}
 static String name(String value){String cleaned=value.trim().replaceAll("\\s+"," ");if(cleaned.length()<2||!cleaned.matches("[\\p{L}\\p{M}][\\p{L}\\p{M} '\u2019-]*"))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Informe nome e sobrenome válidos.");return cleaned;}
 static String key(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
 static String phone(String value){if(!value.matches("[+0-9 ()-]+"))throw invalidPhone();String digits=value.replaceAll("\\D","");if(digits.startsWith("55")&&(digits.length()==12||digits.length()==13))digits=digits.substring(2);if(!digits.matches("[1-9][0-9](?:[2-5][0-9]{7}|9[0-9]{8})"))throw invalidPhone();return digits;}
 private static ResponseStatusException invalidPhone(){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Informe um telefone válido com DDD (10 ou 11 dígitos).");}
 static String code(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
