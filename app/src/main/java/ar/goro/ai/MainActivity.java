package ar.goro.ai;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
  private TextView output;
  private EditText command;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(36, 48, 36, 28);
    root.setBackgroundColor(Color.rgb(14, 20, 32));
    TextView title = new TextView(this);
    title.setText("GORO AI"); title.setTextSize(30); title.setTextColor(Color.WHITE);
    root.addView(title);
    TextView subtitle = new TextView(this);
    subtitle.setText("Asistente personal • versión de prueba"); subtitle.setTextColor(0xff91a9c4);
    root.addView(subtitle);
    command = new EditText(this); command.setSingleLine(false);
    command.setHint("Escribí una orden..."); command.setHintTextColor(0xff9ba8b8); command.setTextColor(Color.WHITE);
    root.addView(command);
    Button execute = new Button(this); execute.setText("Ejecutar");
    root.addView(execute);
    output = new TextView(this); output.setTextColor(Color.WHITE); output.setTextSize(18);
    output.setPadding(0, 28, 0, 0); output.setText("Probá: 25 * 4, hora, alarma o temporizador.");
    root.addView(output);
    execute.setOnClickListener(v -> runCommand(command.getText().toString().trim()));
    setContentView(root);
  }
  private void runCommand(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains("alarma")) {
      Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
      intent.putExtra(AlarmClock.EXTRA_MESSAGE, "GORO AI");
      try { startActivity(intent); output.setText("Abrí la configuración de alarma del teléfono."); }
      catch (Exception e) { output.setText("No hay una aplicación compatible para configurar alarmas."); }
    } else if (lower.contains("temporizador")) {
      Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
      intent.putExtra(AlarmClock.EXTRA_MESSAGE, "GORO AI");
      try { startActivity(intent); output.setText("Abrí la configuración del temporizador."); }
      catch (Exception e) { output.setText("No hay una aplicación compatible para temporizadores."); }
    } else if (lower.contains("hora")) {
      output.setText(new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(new java.util.Date()));
    } else if (text.matches("\\s*-?\\d+(?:[.,]\\d+)?\\s*[+*/-]\\s*-?\\d+(?:[.,]\\d+)?\\s*")) {
      try {
        String clean = text.replace(',', '.').replace(" ", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)([+*/-])(-?\\d+(?:\\.\\d+)?)").matcher(clean);
        if (!m.matches()) throw new IllegalArgumentException();
        double a=Double.parseDouble(m.group(1)), b=Double.parseDouble(m.group(3));
        double result;
        switch(m.group(2)) { case "+": result=a+b; break; case "-": result=a-b; break; case "*": result=a*b; break; default: if(b==0) {output.setText("No se puede dividir por cero."); return;} result=a/b; }
        output.setText(String.valueOf(result));
      } catch(Exception e) { output.setText("No pude calcular esa expresión."); }
    } else {
      output.setText("Todavía no está conectada la IA. No ejecuté ninguna acción.");
    }
  }
}
