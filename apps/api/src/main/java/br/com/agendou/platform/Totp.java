package br.com.agendou.platform;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238: SHA-1, 6 digits, 30-second time steps. */
public final class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private Totp() {}
    public static String newSecret() {
        byte[] bytes = new byte[20]; new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder(); int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255); bits += 8;
            while (bits >= 5) { bits -= 5; result.append(ALPHABET.charAt((buffer >>> bits) & 31)); }
        }
        return result.toString();
    }
    static byte[] decode(String secret) {
        byte[] result = new byte[secret.length()*5/8]; int buffer=0,bits=0,index=0;
        for (char c : secret.toCharArray()) {
            int value=ALPHABET.indexOf(c);
            if(value<0) throw new IllegalArgumentException("Invalid Base32 secret");
            buffer=(buffer<<5)|value; bits+=5;
            if(bits>=8) {bits-=8;result[index++]=(byte)(buffer>>>bits);}
        }
        return result;
    }
    public static String code(String secret,long step) { return code(decode(secret),step,6); }
    static String code(byte[] key,long step,int digits) {
        try {
            Mac mac=Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(key,"HmacSHA1"));
            byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset=hash[hash.length-1]&15;
            int value=ByteBuffer.wrap(hash,offset,4).getInt()&0x7fffffff;
            return String.format(Locale.ROOT,"%0"+digits+"d",value%(digits==8?100000000:1000000));
        } catch(java.security.GeneralSecurityException ex) {throw new IllegalStateException(ex);}
    }
    public static long match(String secret,String supplied,long currentStep,long lastStep) {
        long match=-1;
        for(long step=currentStep-1;step<=currentStep+1;step++) {
            boolean equal=MessageDigest.isEqual(code(secret,step).getBytes(StandardCharsets.US_ASCII),supplied.getBytes(StandardCharsets.US_ASCII));
            if(equal && step>lastStep) match=step;
        }
        return match;
    }
}
