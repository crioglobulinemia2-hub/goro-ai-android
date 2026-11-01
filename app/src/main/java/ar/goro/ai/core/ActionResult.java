package ar.goro.ai.core;
public final class ActionResult {
 public enum Status { SUCCESS, NEEDS_CONFIRMATION, UNAVAILABLE, ERROR }
 public final Status status;
 public final String message;
 private ActionResult(Status status,String message){this.status=status;this.message=message;}
 public static ActionResult of(Status status,String message){if(status==null)throw new IllegalArgumentException("status required");return new ActionResult(status,message==null?"":message);}
 public boolean executed(){return status==Status.SUCCESS;}
}
