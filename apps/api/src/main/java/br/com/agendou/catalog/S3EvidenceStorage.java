package br.com.agendou.catalog;
import io.minio.*;
import java.io.ByteArrayInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class S3EvidenceStorage implements EvidenceStorage {
 private final MinioClient client;private final String bucket;
 public S3EvidenceStorage(@Value("${agendou.storage.endpoint:http://localhost:9000}") String endpoint,@Value("${agendou.storage.access-key:agendou}") String access,@Value("${agendou.storage.secret-key:agendou-local}") String secret,@Value("${agendou.storage.bucket:agendou-evidence-private}") String bucket){
  this.bucket=bucket;client=MinioClient.builder().endpoint(endpoint).credentials(access,secret).build();client.setTimeout(10000,10000,10000);
 }
 public void put(String key,byte[] bytes,String type){try{
  if(!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())){try{client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());}catch(Exception ex){if(!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))throw ex;}}
  requirePrivate();
  client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(new ByteArrayInputStream(bytes),(long)bytes.length,-1L).contentType(type).build());
 }catch(Exception ex){throw unavailable();}}
 public byte[] get(String key){try{requirePrivate();try(var stream=client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())){return stream.readNBytes(EvidenceValidator.MAX+1);}}catch(Exception ex){throw unavailable();}}
 private void requirePrivate()throws Exception{
  try{String policy=client.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucket).build());if(policy!=null&&!policy.isBlank())throw unavailable();}
  catch(io.minio.errors.ErrorResponseException ex){if(!"NoSuchBucketPolicy".equals(ex.errorResponse().code()))throw ex;}
 }
 public void remove(String key){try{client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());}catch(Exception ex){throw unavailable();}}
 private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Armazenamento de comprovantes indisponível. Tente novamente.");}
}
