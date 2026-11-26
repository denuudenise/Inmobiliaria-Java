import ar.edu.nuevohogar.persistencia.dao.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class AuditDatabase {
 public static void main(String[] args) throws Exception {
  try(var db = new ConexionBaseDatos()) {
   String marker="AUDIT_ROLLBACK_"+UUID.randomUUID();
   try(var c=db.open()) {
    c.setAutoCommit(false);
    try {
     int id=ConsultasSQL.insert(c,"INSERT INTO edificios(nombre,direccion,localidad) VALUES(?,?,?)",marker,"Prueba técnica","Prueba técnica");
     ConsultasSQL.exec(c,"UPDATE edificios SET localidad=? WHERE id_edificio=?","Actualizado",id);
     if(ConsultasSQL.list(c,"SELECT id_edificio FROM edificios WHERE nombre=? AND localidad=?",marker,"Actualizado").size()!=1) throw new AssertionError();
     if(ConsultasSQL.exec(c,"DELETE FROM edificios WHERE id_edificio=?",id)!=1) throw new AssertionError();
    } finally {c.rollback();}
    c.setReadOnly(true);
   }
   try(var c=db.open()) {
    if(!c.getAutoCommit() || c.isReadOnly()) throw new AssertionError("Pool no restauró estado");
    if(!ConsultasSQL.list(c,"SELECT id_edificio FROM edificios WHERE nombre=?",marker).isEmpty()) throw new AssertionError("Quedó dato de prueba");
   }
   System.out.println("PASS CRUD transaccional, rollback, aislamiento y restauración del pool; sin registro persistente");
   try(var executor=java.util.concurrent.Executors.newFixedThreadPool(8)) {
    List<java.util.concurrent.Callable<Boolean>> jobs=new ArrayList<>();
    for(int i=0;i<16;i++)jobs.add(()->{try(var c=db.open()){return ConsultasSQL.list(c,"SELECT 1 AS n").getFirst().id("n")==1;}});
    for(var task:executor.invokeAll(jobs)) if(!task.get())throw new AssertionError();
   }
   System.out.println("PASS 16 consultas concurrentes con pool limitado a 4 conexiones");
   if(!db.mediaApiUrl.isBlank()) {
    var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    var response=http.send(HttpRequest.newBuilder(URI.create(db.mediaApiUrl+"?action=download&id=0")).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.discarding());
    System.out.println("API sin autenticación: HTTP "+response.statusCode());
    var media=new ConsultasSQL(db).list("SELECT url_archivo,tipo FROM propiedad_multimedia ORDER BY id_multimedia DESC LIMIT 3");
    for(var row:media) {
     String path=row.text("url_archivo");
     if(!path.startsWith("http"))path=db.mediaWebUrl+path;
     var request=HttpRequest.newBuilder(URI.create(path)).timeout(Duration.ofSeconds(20)).header("Range","bytes=0-1023").GET().build();
     var file=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
     try(var body=file.body()) {
      int bytes=body.readNBytes(1024).length;
      System.out.println("Multimedia "+row.text("tipo")+": HTTP "+file.statusCode()+", tipo="+file.headers().firstValue("Content-Type").orElse("sin tipo")+", bytes leídos="+bytes);
     }
    }
   }
  }
 }
}
