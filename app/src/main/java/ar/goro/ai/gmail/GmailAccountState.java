package ar.goro.ai.gmail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Account-scoped credentials and message IDs. Late work cannot revive an old account. */
final class GmailAccountState {
 private String account;
 private String token;
 private final List<String> ids=new ArrayList<>();
 private long generation;
 private boolean closed;
 synchronized void select(String email){
  String next=email==null||email.trim().isEmpty()?null:email.trim();
  if(Objects.equals(account,next))return;
  account=next;generation++;token=null;ids.clear();
 }
 synchronized String account(){return account;}
 synchronized long generation(){return generation;}
 synchronized boolean current(long id){return !closed&&id==generation;}
 synchronized long begin(){generation++;token=null;ids.clear();return generation;}
 synchronized boolean authorize(long id,String verifiedEmail,String accessToken){
  if(!current(id)||account==null||!account.equalsIgnoreCase(verifiedEmail)
    ||accessToken==null||accessToken.isEmpty())return false;
  token=accessToken;return true;
 }
 synchronized boolean publish(long id,List<String> messages){
  if(!current(id)||token==null)return false;
  ids.clear();ids.addAll(messages);return true;
 }
 synchronized String token(){return token;}
 synchronized String messageId(int number){
  return number<1||number>ids.size()?null:ids.get(number-1);
 }
 synchronized void clearAuthorization(){generation++;token=null;ids.clear();}
 synchronized void close(){closed=true;account=null;clearAuthorization();}
}
