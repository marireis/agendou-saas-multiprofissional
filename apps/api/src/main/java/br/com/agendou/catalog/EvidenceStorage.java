package br.com.agendou.catalog;
public interface EvidenceStorage {
 void put(String key,byte[] bytes,String type);
 byte[] get(String key);
 void remove(String key);
}
