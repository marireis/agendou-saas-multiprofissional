package br.com.agendou.catalog;
import static org.assertj.core.api.Assertions.*;
import static br.com.agendou.catalog.PixKeyValidator.KeyType.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
class PixKeyValidatorTest {
 private final PixKeyValidator keys=new PixKeyValidator();
 @Test void normalizesEmailPhoneAndRandomKeys(){
  assertThat(keys.normalize(EMAIL," STUDIO@example.test ")).isEqualTo("studio@example.test");
  assertThat(keys.normalize(PHONE,"+55 (11) 99999-9999")).isEqualTo("+5511999999999");
  assertThat(keys.normalize(RANDOM,"123E4567-E89B-12D3-A456-426655440000")).isEqualTo("123e4567-e89b-12d3-a456-426655440000");
 }
 @Test void acceptsDocumentCheckDigitsIncludingOfficialAlphanumericExample(){
  assertThat(keys.normalize(CPF,"529.982.247-25")).isEqualTo("52998224725");
  assertThat(keys.normalize(CNPJ,"12.ABC.345/01DE-35")).isEqualTo("12ABC34501DE35");
  assertThat(keys.normalize(CNPJ,"11.222.333/0001-81")).isEqualTo("11222333000181");
 }
 @Test void rejectsWrongTypeMalformedAndInvalidCheckDigits(){
  assertThatThrownBy(()->keys.normalize(CPF,"11111111111")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->keys.normalize(CPF,"52998224724")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->keys.normalize(CNPJ,"12ABC34501DE36")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->keys.normalize(EMAIL,"person@localhost")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->keys.normalize(PHONE,"5511999999999")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->keys.normalize(RANDOM,"1-1-1-1-1")).isInstanceOf(ResponseStatusException.class);
 }
 @Test void rejectsOversizedEmailAndRedactsRequestRepresentation(){
  assertThatThrownBy(()->keys.normalize(EMAIL,"a".repeat(70)+"@example.test")).isInstanceOf(ResponseStatusException.class);
  var request=new PaymentSettingsController.Edit(0,EMAIL,"secret@example.test","Private Name","Instructions","Policy text",true,true,"Configuration reason");
  assertThat(request.toString()).doesNotContain("secret","Private Name");
 }
}
