package br.com.agendou.platform;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TotpTest {
    @Test void matchesRfc6238Sha1VectorsIncludingAfter2038() {
        byte[] key="12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        long[] seconds={59,1111111109L,1111111111L,1234567890L,2000000000L,20000000000L};
        String[] expected={"94287082","07081804","14050471","89005924","69279037","65353130"};
        for(int n=0;n<seconds.length;n++) assertThat(Totp.code(key,seconds[n]/30,8)).isEqualTo(expected[n]);
        assertThat(Totp.code("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",1)).isEqualTo("287082");
    }
    @Test void windowAndReplayAreBounded() {
        String secret=Totp.newSecret();
        assertThat(secret).hasSize(32);assertThat(Totp.decode(secret)).hasSize(20);
        assertThat(Totp.match(secret,Totp.code(secret,100),100,-1)).isEqualTo(100);
        assertThat(Totp.match(secret,Totp.code(secret,100),100,100)).isEqualTo(-1);
        assertThat(Totp.match(secret,Totp.code(secret,98),100,-1)).isEqualTo(-1);
    }
    @Test void encryptionBindsToUserAndNeedsConfiguredKey() {
        var crypto=new MfaSecrets(java.util.Base64.getEncoder().encodeToString(new byte[32]));UUID user=UUID.randomUUID();
        String secret=Totp.newSecret(),encrypted=crypto.encrypt(user,secret);
        assertThat(encrypted).doesNotContain(secret);assertThat(crypto.decrypt(user,encrypted)).isEqualTo(secret);
        assertThat(crypto.encrypt(user,secret)).isNotEqualTo(encrypted);
        assertThatThrownBy(()->crypto.decrypt(UUID.randomUUID(),encrypted)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->new MfaSecrets("").encrypt(user,secret)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
