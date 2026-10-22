package br.com.agendou.catalog;
import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.cos.*;

class EvidenceValidatorTest {
 static byte[] png(int color)throws Exception{var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,color);var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();}
 @Test void decodesAndReencodesImage()throws Exception{var result=EvidenceValidator.validate(png(0));assertThat(result.mediaType()).isEqualTo("image/png");assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(result.bytes())).getWidth()).isEqualTo(2);}
 @Test void acceptsParsedPdf()throws Exception{try(var doc=new PDDocument()){doc.addPage(new PDPage());var out=new ByteArrayOutputStream();doc.save(out);assertThat(EvidenceValidator.validate(out.toByteArray()).mediaType()).isEqualTo("application/pdf");}}
 @Test void rejectsActivePdf()throws Exception{try(var doc=new PDDocument()){doc.addPage(new PDPage());doc.getDocumentCatalog().getCOSObject().setString(COSName.getPDFName("JS"),"alert(1)");var out=new ByteArrayOutputStream();doc.save(out);assertThatThrownBy(()->EvidenceValidator.validate(out.toByteArray())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}}
 @Test void rejectsCorruptionAndOversize(){for(byte[] bytes:new byte[][]{new byte[0],new byte[EvidenceValidator.MAX+1],"%PDF-invalid".getBytes(),"<svg/>".getBytes()})assertThatThrownBy(()->EvidenceValidator.validate(bytes)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void rejectsEncryptedPdf()throws Exception{try(var doc=new PDDocument()){doc.addPage(new PDPage());doc.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner","secret",new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));var out=new ByteArrayOutputStream();doc.save(out);assertThatThrownBy(()->EvidenceValidator.validate(out.toByteArray())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}}
}
