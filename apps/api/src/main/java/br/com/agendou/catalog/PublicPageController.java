package br.com.agendou.catalog;

import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public/pages")
public class PublicPageController {
 private final JdbcTemplate jdbc;
 public PublicPageController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping("/{slug}") @Transactional(readOnly=true)
 public ResponseEntity<String> page(@PathVariable String slug){
  String page=jdbc.queryForObject("SELECT public.read_public_page(?)::text",String.class,slug);
  if(page==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Página indisponível.");
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(page);
 }
 @GetMapping("/{slug}/logo") @Transactional(readOnly=true)
 public ResponseEntity<byte[]> logo(@PathVariable String slug){
  byte[] logo=jdbc.queryForObject("SELECT public.read_public_logo(?)",byte[].class,slug);
  if(logo==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Página indisponível.");
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options","nosniff").body(logo);
 }
}
