package br.com.agendou.catalog;

import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LogoSanitizerTest {
    private final LogoSanitizer sanitizer=new LogoSanitizer();
    static byte[] image(String format,int width,int height) throws IOException {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0,0xff00ff);
        var out=new ByteArrayOutputStream();ImageIO.write(image,format,out);image.flush();return out.toByteArray();
    }
    @Test void reencodesJpegResizesAndDropsTrailingContent() throws Exception {
        var out=new ByteArrayOutputStream();out.write(image("JPEG",1200,600));out.write("private metadata <script>".getBytes());
        byte[] clean=sanitizer.sanitize(new ByteArrayInputStream(out.toByteArray()));
        var decoded=ImageIO.read(new ByteArrayInputStream(clean));
        assertThat(decoded.getWidth()).isEqualTo(512);assertThat(decoded.getHeight()).isEqualTo(256);
        assertThat(clean).startsWith((byte)137,(byte)80,(byte)78,(byte)71);
        assertThat(new String(clean,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("private metadata");
    }
    @Test void preservesTransparentPngWithoutUpscaling() throws Exception {
        var image=new BufferedImage(20,10,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0xff123456);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);
        var decoded=ImageIO.read(new ByteArrayInputStream(sanitizer.sanitize(new ByteArrayInputStream(out.toByteArray()))));
        assertThat(decoded.getWidth()).isEqualTo(20);assertThat(decoded.getRGB(1,1)).isZero();assertThat(decoded.getRGB(0,0)).isEqualTo(0xff123456);
    }
    @Test void rejectsUnsupportedCorruptAndEmptyFiles() throws Exception {
        for(byte[] bytes:new byte[][]{new byte[0],"<svg><script/></svg>".getBytes(),image("GIF",10,10),new byte[]{(byte)137,80,78,71}})
            assertThatThrownBy(()->sanitizer.sanitize(new ByteArrayInputStream(bytes))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void rejectsSizeAndDimensionsBeforeDecoding() throws Exception {
        assertThatThrownBy(()->sanitizer.sanitize(new ByteArrayInputStream(new byte[LogoSanitizer.MAX_BYTES+1])))
            .isInstanceOfSatisfying(ResponseStatusException.class,ex->assertThat(ex.getStatusCode().value()).isEqualTo(413));
        byte[] wide=image("PNG",4097,1);
        assertThatThrownBy(()->sanitizer.sanitize(new ByteArrayInputStream(wide)))
            .isInstanceOfSatisfying(ResponseStatusException.class,ex->assertThat(ex.getStatusCode().value()).isEqualTo(422));
    }
}
