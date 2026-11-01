package ar.goro.ai.calendar;
public final class CalendarEvent {
 public final String id,title,start,end,timezone,description;
 public CalendarEvent(String id,String title,String start,String end,String timezone,String description){
  if(title==null||title.trim().isEmpty())throw new IllegalArgumentException("title required");
  if(start==null||end==null||timezone==null)throw new IllegalArgumentException("event times and timezone required");
  this.id=id;this.title=title.trim();this.start=start;this.end=end;this.timezone=timezone;this.description=description==null?"":description;
 }
}
