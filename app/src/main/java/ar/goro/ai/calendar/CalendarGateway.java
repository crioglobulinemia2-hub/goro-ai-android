package ar.goro.ai.calendar;
import java.util.List;
public interface CalendarGateway {
 interface Callback<T>{void complete(T value,String error);}
 void list(String query,String timeMin,String timeMax,Callback<List<CalendarEvent>> callback);
 void create(CalendarEvent event,Callback<CalendarEvent> callback);
 void update(CalendarEvent event,Callback<CalendarEvent> callback);
 void delete(String eventId,Callback<Boolean> callback);
}
