import ar.edu.nuevohogar.persistencia.dao.*;
import ar.edu.nuevohogar.backend.*;
import ar.edu.nuevohogar.backend.autenticacion.ClavesSeguras;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;

public class AuditMariaFlows {
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception {
  try(var db=new ConexionBaseDatos()) {
   check(new ConsultasSQL(db).one("SELECT DATABASE() AS nombre").text("nombre").equals("nh_auditoria_20261125"),"protección: solo copia aislada");
   String suffix=Long.toString(System.currentTimeMillis());
   String password="AuditoriaTemporal-2026!";
   String adminMail="audit-admin-"+suffix+"@example.invalid";
   db.transaction(c->ConsultasSQL.insert(c,"INSERT INTO usuarios(nombre,apellido,dni,email,password_hash,id_rol) VALUES(?,?,?,?,?,1)","Auditoria","Temporal",suffix+"1",adminMail,ClavesSeguras.hash(password)));
   var admin=new ServicioInmobiliaria(db); long start=System.nanoTime();admin.login(adminMail,password);
   System.out.printf(Locale.ROOT,"login_local_ms=%.3f%n",(System.nanoTime()-start)/1e6);
   int agent=admin.createStaffUser(Map.of("nombre","Auditoria","apellido","Agente","dni",suffix+"2","email","audit-agent-"+suffix+"@example.invalid"),password,2);
   var client=new ServicioInmobiliaria(db);
   String clientMail="audit-client-"+suffix+"@example.invalid";
   int owner=client.register(Map.of("nombre","Auditoria","apellido","Cliente","dni",suffix+"3","email",clientMail),password);
   client.login(clientMail,password);
   check(client.current().id("id_usuario")==owner,"registro y login con hash sobre MariaDB");
   Map<String,String> p=new HashMap<>(Map.of("titulo","AUDITORIA TEMPORAL "+suffix,"direccion","Prueba 123","localidad","Prueba","descripcion","Propiedad de auditoría técnica aislada","id_tipo_propiedad","1","id_propietario",""+owner,"superficie_total_m2","180","superficie_cubierta_m2","120","antiguedad_anos","4","ambientes","4"));
   p.putAll(Map.of("dormitorios","3","banos","2","capacidad","5","expensas","0"));
   var offers=List.of(new ServicioInmobiliaria.Offer(1,new BigDecimal("250000"),"USD"));
   start=System.nanoTime();int property=client.saveProperty(null,p,offers,Set.of(),false);
   System.out.printf(Locale.ROOT,"alta_propiedad_local_ms=%.3f%n",(System.nanoTime()-start)/1e6);
   p.put("titulo","AUDITORIA EDITADA "+suffix);client.saveProperty(property,p,offers,Set.of(),false);
   check(client.property(property).text("titulo").equals(p.get("titulo")),"alta y edición de propiedad, ofertas y lectura");
   Path dir=Files.createTempDirectory("nh-files-audit-");Path doc=dir.resolve("plano.pdf");Files.writeString(doc,"%PDF-1.4\n% prueba técnica de almacenamiento");
   client.attach(property,doc,"Plano");
   check(client.attachments(property,true).size()==1,"documento privado guardado y consultado");
   client.savePropertyTax(property,new ServicioInmobiliaria.PropertyTax("Auditoría",new BigDecimal("1000"),new BigDecimal("2"),"ARS","Prueba"));
   check(client.propertyTaxes(property).size()==1,"cálculo y persistencia de impuestos");
   int request=client.createOwnerRequest(p,offers,Set.of(),true,true,"Documentación técnica en trámite de prueba.");
   client.attachOwnerRequest(request,doc,"Plano");admin.assignOwnerRequest(request,agent);
   int approved=admin.approveOwnerRequest(request);
   check(admin.property(approved).id("id_estado_verificacion")==3,"asignación y aprobación de solicitud");
   check(admin.attachments(approved,true).size()==1,"documentos transferidos a propiedad aprobada");
   var guest=new ServicioInmobiliaria(db);boolean denied=false;
   try{guest.allProperties();}catch(IllegalArgumentException expected){denied=true;}
   check(denied,"acceso privado bloqueado sin sesión");
   check(!admin.activity().isEmpty(),"auditoría persistida");
   client.logout();check(!client.signedIn(),"cierre de sesión");
   System.out.println("Los registros AUDITORIA quedan únicamente en la copia técnica, no en producción.");
  }
 }
}
