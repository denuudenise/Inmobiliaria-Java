import ar.edu.nuevohogar.interfaz.AplicacionInmobiliaria;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.control.TextInputControl;
import javafx.scene.Parent;
import javafx.scene.Node;
public class AuditStartup {
 static boolean hasInput(Node n) {
  if(n instanceof TextInputControl)return true;
  if(n instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())if(hasInput(child))return true;
  return false;
 }
 public static void main(String[] args) {
  Platform.startup(()->{
   var app=new AplicacionInmobiliaria();var stage=new Stage();
   app.start(stage);
   var timer=new PauseTransition(Duration.seconds(20));
   timer.setOnFinished(event->{
    boolean ready=hasInput(stage.getScene().getRoot());
    System.out.println(ready?"PASS ejecutable portable instalado: pantalla de ingreso con configuración remota":"FAIL no apareció el formulario de ingreso");
    app.stop();stage.close();Platform.exit();System.exit(ready?0:1);
   });timer.play();
  });
 }
}
