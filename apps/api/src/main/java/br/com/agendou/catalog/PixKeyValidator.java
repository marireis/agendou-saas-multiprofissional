package br.com.agendou.catalog;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Local format/check-digit validation only; never queries DICT or confirms ownership. */
@Component
public class PixKeyValidator {
 public enum KeyType { CPF,CNPJ,EMAIL,PHONE,RANDOM }
 public String normalize(KeyType type,String input) {
  String value=input.trim();boolean valid;
  switch(type) {
   case EMAIL -> {
    value=value.toLowerCase(Locale.ROOT);
    valid=value.length()<=77 && value.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+");
   }
   case PHONE -> {value=value.replaceAll("[ ()-]","");valid=value.matches("\\+[1-9][0-9]{1,14}");}
   case RANDOM -> {value=value.toLowerCase(Locale.ROOT);valid=value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
   case CPF -> {
    value=value.replaceAll("[. -]","");
    valid=value.matches("[0-9]{11}") && !value.matches("([0-9])\\1{10}") && cpfDigit(value,9)==value.charAt(9)-'0' && cpfDigit(value,10)==value.charAt(10)-'0';
   }
   case CNPJ -> {
    value=value.replaceAll("[./ -]","").toUpperCase(Locale.ROOT);
    valid=value.matches("[A-Z0-9]{12}[0-9]{2}") && !value.matches("([0-9])\\1{13}") && cnpjDigit(value,12)==value.charAt(12)-'0' && cnpjDigit(value,13)==value.charAt(13)-'0';
   }
   default -> throw invalid();
  }
  if(!valid)throw invalid();return value;
 }
 private int cpfDigit(String value,int length){int sum=0;for(int i=0;i<length;i++)sum+=(value.charAt(i)-'0')*(length+1-i);int remainder=sum%11;return remainder<2?0:11-remainder;}
 private int cnpjDigit(String value,int length){int sum=0,weight=2;for(int i=length-1;i>=0;i--){sum+=(value.charAt(i)-48)*weight;weight=weight==9?2:weight+1;}int remainder=sum%11;return remainder<2?0:11-remainder;}
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"A chave não corresponde ao formato selecionado. Confira os dados no seu banco.");}
}
