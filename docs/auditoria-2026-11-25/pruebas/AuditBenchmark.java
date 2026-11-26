import ar.edu.nuevohogar.persistencia.dao.*;
import java.util.*;
public class AuditBenchmark {
  public static void main(String[] args) throws Exception {
    var db = new ConexionBaseDatos();
    var panel = new PanelAdministradorDAO(db);
    var properties = new PropiedadDAO(db);
    Runnable[] operations = {
      () -> { try(var c=db.open()) { if(!c.isValid(2)) throw new AssertionError(); } catch(Exception e) { throw new RuntimeException(e); } },
      () -> panel.load(),
      () -> properties.search(true,"",null,null,null,null,0,0,Set.of(),1,0),
      () -> new ConsultasSQL(db).list("SELECT id_usuario,id_rol,activo FROM usuarios")
    };
    String[] names = {"conexion","panel_administrador","catalogo","usuarios"};
    for(int op=0;op<operations.length;op++) {
      for(int i=0;i<2;i++) operations[op].run();
      for(int i=0;i<7;i++) {
        long start=System.nanoTime(); operations[op].run();
        System.out.printf(Locale.ROOT,"%s,%d,%.3f%n",names[op],i+1,(System.nanoTime()-start)/1e6);
      }
    }
  }
}
