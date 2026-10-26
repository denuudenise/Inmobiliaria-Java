-- Datos ficticios de demostración para la base `inmobiliaria`.
-- Agrega 30 inmuebles, 3 agentes (5 agentes totales contando los 2 existentes),
-- 15 clientes, 5 alquileres activos y operaciones/actividad para mostrar los paneles.
-- No borra ni reemplaza registros existentes. Puede importarse más de una vez.
-- Cuenta de acceso para todos los usuarios creados: DemoHogar2026!

USE inmobiliaria;
SET NAMES utf8mb4;
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS demo_personas;
CREATE TEMPORARY TABLE demo_personas (
  n INT NOT NULL PRIMARY KEY,
  tipo CHAR(1) NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  apellido VARCHAR(100) NOT NULL,
  dni VARCHAR(20) NOT NULL,
  telefono VARCHAR(30) NOT NULL,
  email VARCHAR(150) NOT NULL,
  direccion VARCHAR(255) NOT NULL,
  localidad VARCHAR(100) NOT NULL,
  nacimiento DATE NOT NULL,
  genero VARCHAR(40) NOT NULL,
  email_agente VARCHAR(150) DEFAULT NULL,
  propietario TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO demo_personas VALUES
(1,'A','Camila','Sosa','99600201','11 4100 2101','camila.sosa@demo.nuevohogar.local','Av. Maipú 1450','Vicente López','1988-04-12','Femenino',NULL,0),
(2,'A','Matías','Roldán','99600202','11 4100 2102','matias.roldan@demo.nuevohogar.local','Italia 620','San Isidro','1985-09-23','Masculino',NULL,0),
(3,'A','Florencia','Paz','99600203','11 4100 2103','florencia.paz@demo.nuevohogar.local','Av. Cabildo 2180','Belgrano, CABA','1991-02-08','Femenino',NULL,0),
(4,'C','Sofía','Martínez','99600301','11 4200 3101','sofia.martinez@demo.nuevohogar.local','Italia 114','Tigre','1990-05-17','Femenino','camila.sosa@demo.nuevohogar.local',1),
(5,'C','Tomás','Acosta','99600302','11 4200 3102','tomas.acosta@demo.nuevohogar.local','Gorriti 1860, 3° B','Palermo, CABA','1987-11-02','Masculino','matias.roldan@demo.nuevohogar.local',0),
(6,'C','Valentina','Ruiz','99600303','11 4200 3103','valentina.ruiz@demo.nuevohogar.local','Roca 820','San Fernando','1993-07-24','Femenino','florencia.paz@demo.nuevohogar.local',1),
(7,'C','Joaquín','Herrera','99600304','11 4200 3104','joaquin.herrera@demo.nuevohogar.local','Mendoza 4200, 2° A','Villa Urquiza, CABA','1984-03-19','Masculino','camila.sosa@demo.nuevohogar.local',0),
(8,'C','Martina','Vega','99600305','11 4200 3105','martina.vega@demo.nuevohogar.local','Av. Libertador 1450','Olivos','1995-12-11','Femenino','matias.roldan@demo.nuevohogar.local',1),
(9,'C','Nicolás','Benítez','99600306','11 4200 3106','nicolas.benitez@demo.nuevohogar.local','Sarmiento 320','San Isidro','1982-06-04','Masculino','florencia.paz@demo.nuevohogar.local',1),
(10,'C','Agustina','Luna','99600307','11 4200 3107','agustina.luna@demo.nuevohogar.local','Av. Santa Fe 2870, 5° C','Recoleta, CABA','1992-01-29','Femenino','camila.sosa@demo.nuevohogar.local',0),
(11,'C','Facundo','Molina','99600308','11 4200 3108','facundo.molina@demo.nuevohogar.local','Belgrano 730','Martínez','1989-10-14','Masculino','matias.roldan@demo.nuevohogar.local',1),
(12,'C','Luciana','Castro','99600309','11 4200 3109','luciana.castro@demo.nuevohogar.local','Cramer 1850, 4° A','Belgrano, CABA','1996-08-07','Femenino','florencia.paz@demo.nuevohogar.local',0),
(13,'C','Bruno','Figueroa','99600310','11 4200 3110','bruno.figueroa@demo.nuevohogar.local','Pueyrredón 940','San Martín','1986-02-26','Masculino','camila.sosa@demo.nuevohogar.local',1),
(14,'C','Catalina','Ibarra','99600311','11 4200 3111','catalina.ibarra@demo.nuevohogar.local','Las Heras 510','Tigre','1994-04-03','Femenino','matias.roldan@demo.nuevohogar.local',1),
(15,'C','Santiago','Pereyra','99600312','11 4200 3112','santiago.pereyra@demo.nuevohogar.local','Av. Mitre 620','Avellaneda','1981-12-20','Masculino','florencia.paz@demo.nuevohogar.local',0),
(16,'C','Milagros','Navarro','99600313','11 4200 3113','milagros.navarro@demo.nuevohogar.local','Rivadavia 4800, 7° D','Caballito, CABA','1997-09-16','Femenino','camila.sosa@demo.nuevohogar.local',0),
(17,'C','Federico','Arias','99600314','11 4200 3114','federico.arias@demo.nuevohogar.local','Belgrano 1280','Escobar','1983-07-09','Masculino','matias.roldan@demo.nuevohogar.local',1),
(18,'C','Julieta','Medina','99600315','11 4200 3115','julieta.medina@demo.nuevohogar.local','Calle 34 520','La Plata','1991-03-30','Femenino','florencia.paz@demo.nuevohogar.local',0);

-- Hash PBKDF2 compatible con el sistema. La misma clave de demo se usa para todos.
INSERT INTO usuarios
  (nombre,apellido,dni,telefono,email,password_hash,id_rol,activo,fecha_registro,direccion,localidad,fecha_nacimiento,genero,tipo_documento)
SELECT p.nombre,p.apellido,p.dni,p.telefono,p.email,
  'pbkdf2$600000$TkgtU2hvdy0yMDI2LVNhbHQ=$Cn1Ir0i4w1+CgSrJ5uVg/LCY2glgmZ4gGN7bSF3M+jY=',
  IF(p.tipo='A',2,3),1,DATE_SUB(NOW(),INTERVAL (p.n+3) DAY),p.direccion,p.localidad,p.nacimiento,p.genero,'DNI'
FROM demo_personas p
ON DUPLICATE KEY UPDATE email=VALUES(email);

INSERT INTO clientes (id_cliente,fecha_inicio_relacion,id_agente_asignado)
SELECT u.id_usuario,DATE_SUB(CURDATE(),INTERVAL (p.n+4) DAY),a.id_usuario
FROM demo_personas p
JOIN usuarios u ON u.email=p.email
JOIN usuarios a ON a.email=p.email_agente
WHERE p.tipo='C'
ON DUPLICATE KEY UPDATE id_cliente=VALUES(id_cliente);

INSERT INTO roles_cliente (id_cliente,tipo_rol)
SELECT u.id_usuario,'Comprador'
FROM demo_personas p JOIN usuarios u ON u.email=p.email
WHERE p.tipo='C'
ON DUPLICATE KEY UPDATE tipo_rol=VALUES(tipo_rol);

INSERT INTO roles_cliente (id_cliente,tipo_rol)
SELECT u.id_usuario,'Propietario'
FROM demo_personas p JOIN usuarios u ON u.email=p.email
WHERE p.tipo='C' AND p.propietario=1
ON DUPLICATE KEY UPDATE tipo_rol=VALUES(tipo_rol);

DROP TEMPORARY TABLE IF EXISTS demo_inmuebles;
CREATE TEMPORARY TABLE demo_inmuebles (
  n INT NOT NULL PRIMARY KEY,
  titulo VARCHAR(200) NOT NULL,
  tipo INT NOT NULL,
  transaccion INT NOT NULL,
  estado_comercial INT NOT NULL,
  direccion VARCHAR(255) NOT NULL,
  localidad VARCHAR(100) NOT NULL,
  precio DECIMAL(12,2) NOT NULL,
  moneda CHAR(3) NOT NULL,
  descripcion TEXT NOT NULL,
  superficie DECIMAL(8,2) NOT NULL,
  cubierta DECIMAL(8,2) NOT NULL,
  antiguedad INT NOT NULL,
  ambientes INT NOT NULL,
  dormitorios INT NOT NULL,
  banos INT NOT NULL,
  piso VARCHAR(10) NOT NULL,
  unidad VARCHAR(10) NOT NULL,
  expensas DECIMAL(10,2) NOT NULL,
  apto TINYINT NOT NULL,
  capacidad INT NOT NULL,
  email_propietario VARCHAR(150) NOT NULL,
  email_agente VARCHAR(150) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO demo_inmuebles VALUES
(1,'Casa de dos plantas con jardín en Martínez',1,1,1,'Azcuénaga 1450','Martínez',278000,'USD','Casa familiar luminosa, con living comedor, cocina independiente, galería, jardín y cochera cubierta. Cerca de colegios, comercios y accesos.',242,176,12,5,3,2,'PB','CASA',0,0,6,'sofia.martinez@demo.nuevohogar.local','camila.sosa@demo.nuevohogar.local'),
(2,'Departamento con balcón y vista abierta en Palermo',2,1,1,'Jorge Luis Borges 2180, 6° B','Palermo, CABA',196000,'USD','Departamento al frente con balcón, cocina equipada, amplio dormitorio con placard y espacio de guardado. Edificio con ascensor.',79,72,8,2,1,1,'6','6B',68000,0,2,'tomas.acosta@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(3,'PH reciclado con patio en Villa Urquiza',1,1,3,'Mendoza 4820, unidad 1','Villa Urquiza, CABA',174000,'USD','PH actualizado con entrada independiente, patio con parrilla, cocina integrada y dos dormitorios. Operación ficticia cerrada para la demostración.',112,84,20,3,2,1,'PB','1',0,0,4,'joaquin.herrera@demo.nuevohogar.local','camila.sosa@demo.nuevohogar.local'),
(4,'Casa con pileta y quincho en Tigre',1,1,1,'Los Ceibos 740','Tigre',315000,'USD','Vivienda con parque arbolado, pileta, quincho, galería semicubierta y cochera para dos vehículos.',320,188,9,5,3,3,'PB','CASA',0,0,7,'valentina.ruiz@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(5,'Departamento moderno cerca del río en Olivos',2,1,1,'Av. del Libertador 2450, 4° A','Olivos',225000,'USD','Unidad moderna con balcón corrido, cocina integrada, dos dormitorios y baulera. Edificio con seguridad y SUM.',101,91,4,3,2,2,'4','4A',125000,0,4,'martina.vega@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(6,'Terreno arbolado en barrio abierto de Escobar',5,1,1,'Lote 38, calle Los Aromos','Escobar',68500,'USD','Lote nivelado con arboleda añosa, servicios disponibles en la vereda y buen acceso a la ruta.',740,0,0,0,0,0,'PB','LOTE38',42000,0,1,'federico.arias@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(7,'Casa de estilo en San Isidro con terraza',1,1,1,'Belgrano 860','San Isidro',342000,'USD','Casa con detalles de época, living con hogar, comedor diario, terraza, jardín y dependencia de servicio.',285,210,35,6,4,3,'PB','CASA',0,0,8,'nicolas.benitez@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(8,'Departamento de tres ambientes en Caballito',2,1,3,'Rivadavia 4850, 7° D','Caballito, CABA',167000,'USD','Unidad luminosa con living comedor, cocina independiente, lavadero y balcón. Cierre de compraventa ficticio.',88,81,15,3,2,1,'7','7D',76000,0,3,'milagros.navarro@demo.nuevohogar.local','camila.sosa@demo.nuevohogar.local'),
(9,'Local comercial con vidriera en Belgrano',3,1,1,'Juramento 2180','Belgrano, CABA',248000,'USD','Local a la calle con vidriera amplia, salón principal, depósito y baño. Zona comercial de circulación constante.',96,96,18,2,0,1,'PB','LOCAL',0,1,1,'luciana.castro@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(10,'Casa con parque en San Fernando',1,1,1,'Constitución 1320','San Fernando',239000,'USD','Casa de cuatro ambientes con cocina comedor, jardín con árboles frutales, parrilla y espacio para dos autos.',260,152,16,4,3,2,'PB','CASA',0,0,6,'agustina.luna@demo.nuevohogar.local','camila.sosa@demo.nuevohogar.local'),
(11,'Oficina lista para trabajar en Microcentro',4,1,1,'Maipú 480, piso 6','Microcentro, CABA',118000,'USD','Oficina con recepción, dos despachos, sala de reuniones y kitchenette. Edificio con control de acceso y ascensores.',92,92,17,4,0,1,'6','6A',158000,1,8,'santiago.pereyra@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(12,'Casa tipo chalet en Beccar con cochera',1,1,3,'Juan B. Justo 680','Beccar',264000,'USD','Chalet con living comedor, cocina renovada, tres dormitorios, patio y cochera. Venta ficticia concretada para el panel.',220,160,22,4,3,2,'PB','CASA',0,0,6,'facundo.molina@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(13,'Departamento con amenities en Vicente López',2,1,2,'Av. Maipú 1250, 8° C','Vicente López',248000,'USD','Departamento con balcón y vista abierta. Edificio con pileta, gimnasio, SUM y vigilancia. Reservado mientras se completa documentación.',109,98,5,3,2,2,'8','8C',198000,0,4,'catalina.ibarra@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(14,'Cochera cubierta en Palermo Soho',6,1,1,'Gurruchaga 1420, subsuelo','Palermo, CABA',28500,'USD','Cochera fija cubierta con portón automático, acceso cómodo y vigilancia del edificio.',15,15,7,0,0,0,'SS','C12',18000,0,1,'bruno.figueroa@demo.nuevohogar.local','camila.sosa@demo.nuevohogar.local'),
(15,'Casa quinta con pileta en Luján',1,1,1,'Ruta 7 km 72, calle Las Acacias 120','Luján',189000,'USD','Casa quinta sobre lote amplio, con pileta, galería, parrilla, árboles frutales y espacio de guardado.',1100,190,14,5,3,2,'PB','CASA',0,0,8,'julieta.medina@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(16,'Casa amueblada con pileta en alquiler en Tigre',1,2,4,'Los Ceibos 812','Tigre',1350,'USD','Alquiler residencial amueblado con jardín, pileta, parrilla, galería y cochera. Contrato activo de demostración.',305,172,11,5,3,2,'PB','CASA',0,0,6,'sofia.martinez@demo.nuevohogar.local','camila.sosa@demo.nuevohogar.local'),
(17,'Departamento con balcón en alquiler en Palermo',2,2,1,'Güemes 4120, 5° A','Palermo, CABA',780000,'ARS','Departamento de dos ambientes, balcón al frente, cocina equipada y placard. A pocas cuadras del subte y espacios verdes.',61,55,6,2,1,1,'5','5A',112000,0,2,'tomas.acosta@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(18,'Casa familiar en alquiler en San Fernando',1,2,4,'Lavalle 1580','San Fernando',980,'USD','Casa con tres dormitorios, patio, parrilla y cochera. Inquilino con contrato vigente hasta el próximo año.',210,145,19,4,3,2,'PB','CASA',0,0,6,'valentina.ruiz@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(19,'Departamento amoblado temporal en Recoleta',2,3,1,'Pueyrredón 940, 9° B','Recoleta, CABA',920,'USD','Unidad equipada con balcón francés, cocina completa, ropa blanca y servicios incluidos. Disponible para estadías temporarias.',58,54,13,2,1,1,'9','9B',95000,0,2,'agustina.luna@demo.nuevohogar.local','camila.sosa@demo.nuevohogar.local'),
(20,'Casa en alquiler con jardín en Martínez',1,2,4,'Sáenz Valiente 1840','Martínez',1150,'USD','Casa de cuatro ambientes con galería semicubierta, jardín y cochera. Contrato residencial activo, mascotas a conversar.',245,168,14,4,3,2,'PB','CASA',0,0,6,'facundo.molina@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(21,'Departamento amplio en alquiler en Belgrano',2,2,1,'Cramer 1850, 4° A','Belgrano, CABA',890000,'ARS','Tres ambientes con balcón, cocina independiente, lavadero, baulera y calefacción individual.',94,86,10,3,2,2,'4','4A',135000,0,4,'luciana.castro@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(22,'Casa con parrilla en alquiler en Olivos',1,2,4,'Rosales 680','Olivos',1050,'USD','Casa luminosa con dos dormitorios, patio con parrilla y espacio guardacoche. Alquiler activo con renovación próxima.',185,124,18,4,2,2,'PB','CASA',0,0,4,'martina.vega@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(23,'Oficina en alquiler cerca de Plaza San Martín',4,2,1,'Maipú 530, piso 5','Retiro, CABA',1250000,'ARS','Oficina profesional con recepción, dos despachos, sala de reuniones y kitchenette. Edificio con control de acceso.',88,88,12,4,0,1,'5','5C',185000,1,6,'santiago.pereyra@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(24,'Casa de fin de semana en alquiler en Escobar',1,3,4,'Barrio Los Álamos, lote 92','Escobar',1450,'USD','Alquiler temporario con pileta, galería, parrilla, parque y ambientes completamente equipados. Reserva de temporada activa.',390,205,8,6,4,3,'PB','CASA',0,0,9,'federico.arias@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(25,'Local luminoso en alquiler en San Isidro',3,2,1,'Belgrano 510','San Isidro',680000,'ARS','Local comercial con frente vidriado, salón sin columnas, baño y pequeño depósito. Apto para varios rubros.',72,72,16,2,0,1,'PB','LOCAL',85000,1,1,'nicolas.benitez@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(26,'Departamento en alquiler con vista al río',2,2,4,'Av. del Libertador 1550, 8° A','Vicente López',1250,'USD','Tres ambientes con balcón aterrazado, vista al río, pileta y seguridad. Contrato de alquiler activo.',105,94,4,3,2,2,'8','8A',178000,0,4,'catalina.ibarra@demo.nuevohogar.local','matias.roldan@demo.nuevohogar.local'),
(27,'Terreno en alquiler para uso comercial en La Plata',5,2,1,'Av. 44 1860','La Plata',520000,'ARS','Lote despejado sobre avenida, con acceso a servicios y buena exposición para actividad comercial o guarda.',620,0,0,0,0,0,'PB','LOTE',0,1,1,'julieta.medina@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(28,'Departamento temporal cerca del mar en Mar del Tuyú',2,3,1,'Calle 4 820, 2° A','Mar del Tuyú',650000,'ARS','Departamento equipado a pocas cuadras de la playa, con balcón, cocina completa y espacio para guardar bicicletas.',68,62,21,3,2,1,'2','2A',45000,0,5,'valentina.ruiz@demo.nuevohogar.local','florencia.paz@demo.nuevohogar.local'),
(29,'Casa en alquiler con patio en Villa Devoto',1,2,1,'Pedro Morán 3850','Villa Devoto, CABA',1180000,'ARS','Casa de tres dormitorios con patio, terraza, cocina comedor y cochera. Buena conexión con tren, comercios y colegios.',230,158,27,5,3,2,'PB','CASA',0,0,6,'joaquin.herrera@demo.nuevohogar.local','camila.sosa@demo.nuevohogar.local'),
(30,'Departamento a estrenar con balcón en Núñez',2,1,1,'Vuelta de Obligado 3450, 3° C','Núñez, CABA',212000,'USD','Unidad a estrenar con balcón, cocina integrada, dormitorio en suite y amenities. Solicitud de publicación pendiente de revisión.',72,66,0,2,1,1,'3','3C',125000,0,2,'milagros.navarro@demo.nuevohogar.local','camila.sosa@demo.nuevohogar.local');

SET @admin_demo := (SELECT id_usuario FROM usuarios WHERE id_rol=1 AND activo=1 ORDER BY id_usuario LIMIT 1);

INSERT INTO propiedades
  (id_tipo_propiedad,id_tipo_transaccion,titulo,direccion,localidad,precio,moneda,descripcion,
   superficie_total_m2,superficie_cubierta_m2,antiguedad_anos,ambientes,dormitorios,banos,piso,
   departamento_numero,expensas,apto_profesional,id_agente_creador,id_admin_verificador,
   id_estado_verificacion,id_estado_comercial,fecha_creacion,fecha_verificacion,motivo_rechazo,
   id_propietario,id_edificio,numero_unidad,capacidad,id_solicitud)
SELECT d.tipo,d.transaccion,d.titulo,d.direccion,d.localidad,d.precio,d.moneda,d.descripcion,
       d.superficie,d.cubierta,d.antiguedad,d.ambientes,d.dormitorios,d.banos,d.piso,d.unidad,
       d.expensas,d.apto,a.id_usuario,IF(d.n=30,NULL,@admin_demo),IF(d.n=30,2,3),d.estado_comercial,
       DATE_SUB(NOW(),INTERVAL d.n DAY),IF(d.n=30,NULL,DATE_SUB(NOW(),INTERVAL (d.n-1) DAY)),NULL,
       o.id_usuario,NULL,d.unidad,d.capacidad,NULL
FROM demo_inmuebles d
JOIN usuarios a ON a.email=d.email_agente
JOIN usuarios o ON o.email=d.email_propietario
WHERE NOT EXISTS (SELECT 1 FROM propiedades existente WHERE existente.titulo=d.titulo);

INSERT INTO propiedad_ofertas (id_propiedad,id_tipo_transaccion,precio,moneda)
SELECT p.id_propiedad,d.transaccion,d.precio,d.moneda
FROM demo_inmuebles d JOIN propiedades p ON p.titulo=d.titulo
ON DUPLICATE KEY UPDATE precio=VALUES(precio),moneda=VALUES(moneda);

-- Publicaciones aprobadas para navegar por catálogo y una solicitud pendiente para mostrar revisión.
INSERT INTO publicaciones (id_propiedad,id_solicitante,id_revisor,estado,observaciones,fecha_solicitud,fecha_revision)
SELECT p.id_propiedad,p.id_propietario,IF(d.n=30,NULL,@admin_demo),IF(d.n=30,'Pendiente','Publicada'),
       IF(d.n=30,'Pendiente de revisión administrativa para la demostración.','Registro ficticio aprobado para la demostración.'),
       DATE_SUB(NOW(),INTERVAL d.n DAY),IF(d.n=30,NULL,DATE_SUB(NOW(),INTERVAL (d.n-1) DAY))
FROM demo_inmuebles d JOIN propiedades p ON p.titulo=d.titulo
WHERE NOT EXISTS (SELECT 1 FROM publicaciones x WHERE x.id_propiedad=p.id_propiedad AND x.estado IN ('Pendiente','Publicada'));

-- Características variadas para poblar filtros y fichas.
INSERT IGNORE INTO propiedad_caracteristicas (id_propiedad,id_caracteristica)
SELECT p.id_propiedad,c.id_caracteristica
FROM demo_inmuebles d
JOIN propiedades p ON p.titulo=d.titulo
JOIN caracteristicas c ON c.id_caracteristica IN (
  CASE WHEN d.tipo IN (1,2) THEN 1 ELSE 2 END,
  CASE WHEN d.tipo=1 THEN 2 ELSE 6 END,
  CASE WHEN d.tipo IN (1,5) THEN 5 ELSE 8 END
);

-- Cinco clientes con contratos vigentes asociados a cinco inmuebles en estado Alquilada.
DROP TEMPORARY TABLE IF EXISTS demo_inquilinos;
CREATE TEMPORARY TABLE demo_inquilinos (
  n INT NOT NULL PRIMARY KEY,
  email_inquilino VARCHAR(150) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO demo_inquilinos VALUES
(16,'tomas.acosta@demo.nuevohogar.local'),
(18,'joaquin.herrera@demo.nuevohogar.local'),
(20,'agustina.luna@demo.nuevohogar.local'),
(22,'luciana.castro@demo.nuevohogar.local'),
(24,'santiago.pereyra@demo.nuevohogar.local');

-- Si ya se había importado una versión anterior de este archivo, corrige esos
-- cinco contratos para que el inquilino no sea también el propietario.
UPDATE alquileres x
JOIN propiedades p ON p.id_propiedad=x.id_propiedad
JOIN demo_inmuebles d ON d.titulo=p.titulo
JOIN demo_inquilinos i ON i.n=d.n
JOIN usuarios t ON t.email=i.email_inquilino
JOIN usuarios a ON a.email=d.email_agente
SET x.id_cliente=t.id_usuario,x.id_agente=a.id_usuario,x.monto_mensual=d.precio,
    x.fecha_inicio=DATE_SUB(CURDATE(),INTERVAL (d.n-12) MONTH),
    x.fecha_fin=DATE_ADD(CURDATE(),INTERVAL 6 MONTH),x.estado='Activo'
WHERE d.n IN (16,18,20,22,24) AND x.estado='Activo'
  AND x.id_cliente=p.id_propietario;

INSERT INTO alquileres (id_propiedad,id_cliente,id_agente,monto_mensual,fecha_inicio,fecha_fin,estado)
SELECT p.id_propiedad,t.id_usuario,a.id_usuario,d.precio,
       DATE_SUB(CURDATE(),INTERVAL (d.n-12) MONTH),DATE_ADD(CURDATE(),INTERVAL 6 MONTH),'Activo'
FROM demo_inmuebles d
JOIN propiedades p ON p.titulo=d.titulo
JOIN demo_inquilinos i ON i.n=d.n
JOIN usuarios t ON t.email=i.email_inquilino
JOIN usuarios a ON a.email=d.email_agente
WHERE d.n IN (16,18,20,22,24)
  AND t.id_usuario<>p.id_propietario
  AND NOT EXISTS (SELECT 1 FROM alquileres x WHERE x.id_propiedad=p.id_propiedad AND x.estado='Activo');

-- Tres compraventas ficticias cerradas y reflejadas como propiedades Vendidas.
INSERT INTO propiedades_cerradas
  (id_propiedad,id_cliente_comprador,id_agente_cierre,precio_final,comision_total,fecha_cierre,id_tipo_transaccion,moneda)
SELECT p.id_propiedad,
       (SELECT u.id_usuario FROM usuarios u WHERE u.id_rol=3 AND u.id_usuario<>p.id_propietario ORDER BY u.id_usuario LIMIT 1),
       a.id_usuario,d.precio,ROUND(d.precio*0.03,2),DATE_SUB(NOW(),INTERVAL (d.n+2) DAY),1,d.moneda
FROM demo_inmuebles d JOIN propiedades p ON p.titulo=d.titulo JOIN usuarios a ON a.email=d.email_agente
WHERE d.n IN (3,8,12)
  AND NOT EXISTS (SELECT 1 FROM propiedades_cerradas x WHERE x.id_propiedad=p.id_propiedad);

-- Consultas e intereses para que las fichas de los agentes y clientes tengan actividad.
INSERT IGNORE INTO intereses (id_cliente,id_propiedad,estado,acciones,fecha,es_favorito)
SELECT u.id_usuario,p.id_propiedad,IF(MOD(c.n,3)=0,'Visita coordinada','Interesado'),
       IF(MOD(c.n,2)=0,'Guardada desde favoritos; consultó precio y disponibilidad.','Solicitó información sobre la propiedad.'),
       DATE_SUB(NOW(),INTERVAL c.n DAY),IF(MOD(c.n,2)=0,1,0)
FROM demo_personas c
JOIN usuarios u ON u.email=c.email
JOIN demo_inmuebles d ON d.n=MOD(c.n*2,29)+1
JOIN propiedades p ON p.titulo=d.titulo
WHERE c.tipo='C';

INSERT INTO contactos (id_cliente,id_agente,id_propiedad,tipo,fecha,descripcion,resultado,estado,fecha_respuesta)
SELECT u.id_usuario,a.id_usuario,p.id_propiedad,'Consulta',DATE_SUB(NOW(),INTERVAL c.n DAY),
       CONCAT('Consulta de demostración de ',u.nombre,' sobre ',p.titulo,'.'),
       IF(MOD(c.n,2)=0,'Se envió información y se coordinó seguimiento.','Se respondió con precio, características y disponibilidad.'),
       'Respondida',DATE_SUB(NOW(),INTERVAL GREATEST(c.n-1,0) DAY)
FROM demo_personas c JOIN usuarios u ON u.email=c.email
JOIN usuarios a ON a.email=c.email_agente
JOIN demo_inmuebles d ON d.n=MOD(c.n*3,29)+1
JOIN propiedades p ON p.titulo=d.titulo
WHERE c.tipo='C'
  AND NOT EXISTS (SELECT 1 FROM contactos x WHERE x.id_cliente=u.id_usuario AND x.id_propiedad=p.id_propiedad AND x.descripcion=CONCAT('Consulta de demostración de ',u.nombre,' sobre ',p.titulo,'.'));

INSERT INTO visitas (id_propiedad,id_cliente,id_agente,fecha_hora,estado,resultado,observaciones,fecha_creacion)
SELECT p.id_propiedad,u.id_usuario,a.id_usuario,
       DATE_ADD(NOW(),INTERVAL (c.n-8) DAY),
       IF(c.n IN (5,8,11,14,17),'Realizada','Programada'),
       IF(c.n IN (5,8,11,14,17),'La propiedad fue visitada; el cliente continúa evaluando.','Visita coordinada con el agente asignado.'),
       'Actividad ficticia de demostración.',DATE_SUB(NOW(),INTERVAL c.n DAY)
FROM demo_personas c JOIN usuarios u ON u.email=c.email
JOIN usuarios a ON a.email=c.email_agente
JOIN demo_inmuebles d ON d.n=MOD(c.n+4,29)+1
JOIN propiedades p ON p.titulo=d.titulo
WHERE c.tipo='C' AND c.n<=12
  AND NOT EXISTS (SELECT 1 FROM visitas x WHERE x.id_cliente=u.id_usuario AND x.id_propiedad=p.id_propiedad AND x.observaciones='Actividad ficticia de demostración.');

INSERT INTO tareas_seguimiento (id_agente,id_cliente,id_propiedad,titulo,detalle,fecha_limite,estado,fecha_completada)
SELECT a.id_usuario,u.id_usuario,p.id_propiedad,
       IF(MOD(c.n,2)=0,'Contactar por la visita','Enviar opciones similares'),
       CONCAT('Seguimiento de ',u.nombre,' para ',p.titulo,'. Revisar preferencias y próximo paso comercial.'),
       DATE_ADD(NOW(),INTERVAL (c.n+1) DAY),IF(MOD(c.n,4)=0,'Completada','Pendiente'),
       IF(MOD(c.n,4)=0,DATE_SUB(NOW(),INTERVAL 1 DAY),NULL)
FROM demo_personas c JOIN usuarios u ON u.email=c.email
JOIN usuarios a ON a.email=c.email_agente
JOIN demo_inmuebles d ON d.n=MOD(c.n*4,29)+1
JOIN propiedades p ON p.titulo=d.titulo
WHERE c.tipo='C'
  AND NOT EXISTS (SELECT 1 FROM tareas_seguimiento x WHERE x.id_cliente=u.id_usuario AND x.id_propiedad=p.id_propiedad AND x.detalle=CONCAT('Seguimiento de ',u.nombre,' para ',p.titulo,'. Revisar preferencias y próximo paso comercial.'));

COMMIT;

DROP TEMPORARY TABLE IF EXISTS demo_inmuebles;
DROP TEMPORARY TABLE IF EXISTS demo_inquilinos;
DROP TEMPORARY TABLE IF EXISTS demo_personas;

-- Cuentas creadas: los 3 agentes y 15 clientes del archivo comparten la clave DemoHogar2026!
