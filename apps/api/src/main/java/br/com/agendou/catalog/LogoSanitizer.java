package br.com.agendou.catalog;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LogoSanitizer {
    public static final int MAX_BYTES=2*1024*1024;
    public byte[] sanitize(InputStream input) throws IOException {
        byte[] bytes=input.readNBytes(MAX_BYTES+1);
        if(bytes.length>MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"A logomarca deve ter no máximo 2 MB.");
        if(bytes.length==0) throw invalid();
        try(var imageInput=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(imageInput);
            if(!readers.hasNext()) throw invalid();
            var reader=readers.next();
            try {
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("PNG") && !format.equalsIgnoreCase("JPEG")) throw invalid();
                reader.setInput(imageInput,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || width>4096 || height>4096 || (long)width*height>16_000_000)
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"A imagem deve ter até 4096 pixels por lado e 16 milhões de pixels.");
                BufferedImage original=reader.read(0);
                double scale=Math.min(1,512.0/Math.max(width,height));
                BufferedImage clean=new BufferedImage(Math.max(1,(int)Math.round(width*scale)),Math.max(1,(int)Math.round(height*scale)),BufferedImage.TYPE_INT_ARGB);
                var graphics=clean.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(original,0,0,clean.getWidth(),clean.getHeight(),null);
                } finally {graphics.dispose();original.flush();}
                try(var output=new ByteArrayOutputStream()) {
                    ImageIO.write(clean,"PNG",output);clean.flush();
                    if(output.size()>MAX_BYTES) throw invalid();
                    return output.toByteArray();
                }
            } finally {reader.dispose();}
        } catch(IOException | IllegalArgumentException ex) {throw invalid();}
    }
    private ResponseStatusException invalid() {return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Envie uma imagem PNG ou JPEG válida. SVG, GIF e outros formatos não são aceitos.");}
}
