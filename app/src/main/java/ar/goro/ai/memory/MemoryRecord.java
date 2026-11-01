package ar.goro.ai.memory;
public final class MemoryRecord {
 public final long id,createdAt,updatedAt;
 public final String category,content;
 public MemoryRecord(long id,String category,String content,long createdAt,long updatedAt){
  if(category==null||category.trim().isEmpty())throw new IllegalArgumentException("category required");
  if(content==null||content.trim().isEmpty())throw new IllegalArgumentException("content required");
  if(content.length()>2000)throw new IllegalArgumentException("content too long");
  this.id=id;this.category=category.trim();this.content=content.trim();this.createdAt=createdAt;this.updatedAt=updatedAt;
 }
}
