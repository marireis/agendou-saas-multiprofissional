package br.com.agendou.catalog;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CustomerContactTest {
 @Test void phoneNormalizesFormattingAndCountryCode(){assertThat(CustomerContact.phone("+55 (11) 99999-0000")).isEqualTo("11999990000");assertThat(CustomerContact.phone("(55) 99999-0000")).isEqualTo("55999990000");assertThat(CustomerContact.phone("11 3333-4444")).isEqualTo("1133334444");}
 @Test void phoneRejectsMissingAreaCodeAndInvalidCharacters(){for(String value:new String[]{"999990000","119123","abcdefghijk","001999990000","11999990000x"})assertThatThrownBy(()->CustomerContact.phone(value)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void namesNormalizeWithoutChangingDisplay(){assertThat(CustomerContact.key("  José   da Silva ")).isEqualTo("jose da silva");assertThat(CustomerContact.name(" José ")).isEqualTo("José");assertThatThrownBy(()->CustomerContact.name("1234")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void accessCodeIsRandomAndOnlyHashIsPersistent(){String first=CustomerContact.code(),second=CustomerContact.code();assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);assertThat(CustomerContact.hash(first)).hasSize(64).doesNotContain(first);}
}
