package ar.goro.ai.gmail;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class GmailAccountStateTest {
 @Test public void changingAccountRejectsLateInboxAndToken(){
  GmailAccountState state=new GmailAccountState();state.select("one@example.test");
  long old=state.begin();assertTrue(state.authorize(old,"one@example.test","token-one"));
  assertTrue(state.publish(old,Arrays.asList("one-message")));
  state.select("two@example.test");
  assertNull(state.token());assertNull(state.messageId(1));
  assertFalse(state.authorize(old,"one@example.test","late-token"));
  assertFalse(state.publish(old,Arrays.asList("late-message")));
  long next=state.begin();assertTrue(state.authorize(next,"two@example.test","token-two"));
  assertTrue(state.publish(next,Arrays.asList("two-message")));
  assertEquals("two-message",state.messageId(1));
 }
 @Test public void actualProfileMustMatchSelectedAccount(){
  GmailAccountState state=new GmailAccountState();state.select("one@example.test");
  long id=state.begin();assertFalse(state.authorize(id,"two@example.test","token"));
  assertFalse(state.publish(id,Arrays.asList("private-message")));assertNull(state.token());
 }
 @Test public void unlinkInvalidatesAllAccess(){
  GmailAccountState state=new GmailAccountState();state.select("one@example.test");
  long id=state.begin();state.authorize(id,"one@example.test","token");
  state.publish(id,Arrays.asList("id"));state.select(null);
  assertNull(state.token());assertNull(state.account());assertNull(state.messageId(1));assertFalse(state.current(id));
 }
 @Test public void repeatedSelectionPreservesSessionButNewQueryInvalidatesOldNumbering(){
  GmailAccountState state=new GmailAccountState();state.select("one@example.test");
  long id=state.begin();state.authorize(id,"one@example.test","token");state.publish(id,Arrays.asList("id"));
  state.select("one@example.test");assertEquals("id",state.messageId(1));
  state.begin();assertNull(state.messageId(1));assertNull(state.token());assertFalse(state.current(id));
 }
 @Test public void closedSessionNeverAcceptsLateAuthorization(){
  GmailAccountState state=new GmailAccountState();state.select("one@example.test");
  long id=state.begin();state.close();
  assertFalse(state.authorize(id,"one@example.test","token"));assertNull(state.messageId(1));
 }
 @Test public void freshMessageAuthorizationPreservesStableSelection(){
  GmailAccountState state=new GmailAccountState();state.select("one@example.test");
  long inbox=state.begin();state.authorize(inbox,"one@example.test","expired-token");
  state.publish(inbox,Arrays.asList("first","chosen"));
  String selected=state.messageId(2);
  long read=state.beginMessageRead();
  assertFalse(state.current(inbox));assertNull(state.token());
  assertEquals("chosen",selected);assertEquals("chosen",state.messageId(2));
  assertTrue(state.authorize(read,"one@example.test","fresh-token"));
  assertEquals("fresh-token",state.token());assertEquals(selected,state.messageId(2));
  assertFalse(state.publish(inbox,Arrays.asList("replacement")));
  assertEquals("chosen",state.messageId(2));
 }
 @Test public void accountChangeDuringRenewalRejectsOldMessageAndToken(){
  GmailAccountState state=new GmailAccountState();state.select("one@example.test");
  long inbox=state.begin();state.authorize(inbox,"one@example.test","token");
  state.publish(inbox,Arrays.asList("private-message"));
  long read=state.beginMessageRead();state.select("two@example.test");
  assertFalse(state.current(read));assertNull(state.messageId(1));
  assertFalse(state.authorize(read,"one@example.test","fresh-token"));assertNull(state.token());
 }
}
