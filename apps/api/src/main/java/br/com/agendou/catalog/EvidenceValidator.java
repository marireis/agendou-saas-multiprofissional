package br.com.agendou.catalog;

import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class EvidenceValidator {
 public static final int MAX=5*1024*1024;
 public record Validated(byte[] bytes,String mediaType){}
 public static Validated validate(byte[] bytes){
  if(bytes.length==0||bytes.length>MAX)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Envie um arquivo de até 5 MB.");
  try{
   if(bytes.length>=5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")){
    try(var document=Loader.loadPDF(bytes)){
     if(document.isEncrypted()||document.getNumberOfPages()<1||document.getNumberOfPages()>10)throw new IOException();
     inspect(document.getDocumentCatalog().getCOSObject(),Collections.newSetFromMap(new IdentityHashMap<>()),0);
     return new Validated(bytes,"application/pdf");
    }
   }
   try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();var reader=readers.next();
    try{String format=reader.getFormatName();if(!format.equalsIgnoreCase("PNG")&&!format.equalsIgnoreCase("JPEG"))throw new IOException();
     reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);if(width<1||height<1||(long)width*height>16_000_000)throw new IOException();
     var image=reader.read(0);var out=new ByteArrayOutputStream();try(var output=new javax.imageio.stream.MemoryCacheImageOutputStream(out)){ImageIO.write(image,"png",output);}if(out.size()>MAX)throw new IOException();return new Validated(out.toByteArray(),"image/png");
    }finally{reader.dispose();}
   }
  }catch(IOException|RuntimeException ex){throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Arquivo inválido. Use JPEG, PNG ou PDF sem senha/conteúdo ativo, até 10 páginas e 16 megapixels.");}
 }
 private static void inspect(COSBase value,Set<COSBase> seen,int depth)throws IOException{
  if(value==null||!seen.add(value))return;if(depth>100||seen.size()>100000)throw new IOException();
  if(value instanceof COSName name&&Set.of("JavaScript","Launch","SubmitForm","ImportData","Rendition","GoToR","GoToE").contains(name.getName()))throw new IOException();
  if(value instanceof COSObject object)inspect(object.getObject(),seen,depth+1);
  else if(value instanceof COSDictionary dictionary){
   for(var key:dictionary.keySet()){
    if(Set.of("JS","JavaScript","OpenAction","AA","EmbeddedFiles","EF","XFA","RichMedia","Launch").contains(key.getName()))throw new IOException();
    inspect(dictionary.getDictionaryObject(key),seen,depth+1);
   }
  }else if(value instanceof COSArray array)for(var item:array)inspect(item,seen,depth+1);
 }
}
