-- Inicialización reanudable exclusiva de H2. No sobrescribe datos existentes.
CREATE TABLE IF NOT EXISTS schema_version (version INT PRIMARY KEY, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS caracteristicas (
  id_caracteristica INT AUTO_INCREMENT PRIMARY KEY,
  nombre varchar(50) NOT NULL
, UNIQUE(nombre));
CREATE TABLE IF NOT EXISTS estados_comerciales (
  id_estado_comercial INT AUTO_INCREMENT PRIMARY KEY,
  nombre varchar(50) NOT NULL
, UNIQUE(nombre));
CREATE TABLE IF NOT EXISTS estados_verificacion (
  id_estado_verificacion INT AUTO_INCREMENT PRIMARY KEY,
  nombre varchar(50) NOT NULL
, UNIQUE(nombre));
CREATE TABLE IF NOT EXISTS preferencias_cliente (
  id_preferencia INT AUTO_INCREMENT PRIMARY KEY,
  id_cliente INT NOT NULL,
  id_tipo_transaccion INT NOT NULL,
  id_tipo_propiedad INT DEFAULT NULL,
  presupuesto_maximo decimal(12,2) DEFAULT NULL,
  zona_interes varchar(100) DEFAULT NULL,
  ambientes_minimos INT DEFAULT NULL,
  dormitorios_minimos INT DEFAULT NULL,
  superficie_minima_m2 decimal(8,2) DEFAULT NULL,
  fecha_estimada_concretar date DEFAULT NULL,
  requiere_venta_previa INT DEFAULT 0,
  fecha_creacion datetime DEFAULT current_timestamp()
);
CREATE TABLE IF NOT EXISTS propiedades (
  id_propiedad INT AUTO_INCREMENT PRIMARY KEY,
  id_tipo_propiedad INT NOT NULL,
  id_tipo_transaccion INT NOT NULL,
  titulo varchar(200) NOT NULL,
  direccion varchar(255) NOT NULL,
  localidad varchar(100) NOT NULL,
  precio decimal(12,2) NOT NULL,
  moneda VARCHAR(40) DEFAULT 'USD',
  descripcion text DEFAULT NULL,
  superficie_total_m2 decimal(8,2) NOT NULL,
  superficie_cubierta_m2 decimal(8,2) DEFAULT NULL,
  antiguedad_anos INT DEFAULT 0,
  ambientes INT DEFAULT NULL,
  dormitorios INT DEFAULT NULL,
  banos INT DEFAULT NULL,
  piso varchar(10) DEFAULT NULL,
  departamento_numero varchar(10) DEFAULT NULL,
  expensas decimal(10,2) DEFAULT NULL,
  apto_profesional INT DEFAULT 0,
  id_agente_creador INT NOT NULL,
  id_admin_verificador INT DEFAULT NULL,
  id_estado_verificacion INT NOT NULL DEFAULT 1,
  id_estado_comercial INT NOT NULL DEFAULT 1,
  fecha_creacion datetime DEFAULT current_timestamp(),
  fecha_verificacion datetime DEFAULT NULL,
  motivo_rechazo text DEFAULT NULL
);
CREATE TABLE IF NOT EXISTS propiedades_cerradas (
  id_operacion INT AUTO_INCREMENT PRIMARY KEY,
  id_propiedad INT NOT NULL,
  id_cliente_comprador INT NOT NULL,
  id_agente_cierre INT NOT NULL,
  precio_final decimal(12,2) NOT NULL,
  comision_total decimal(10,2) DEFAULT NULL,
  fecha_cierre datetime DEFAULT current_timestamp()
);
CREATE TABLE IF NOT EXISTS propiedad_caracteristicas (
  id_propiedad INT NOT NULL,
  id_caracteristica INT NOT NULL
, PRIMARY KEY(id_propiedad,id_caracteristica));
CREATE TABLE IF NOT EXISTS propiedad_multimedia (
  id_multimedia INT AUTO_INCREMENT PRIMARY KEY,
  id_propiedad INT NOT NULL,
  url_archivo varchar(500) NOT NULL,
  tipo VARCHAR(40) DEFAULT 'Imagen',
  orden INT DEFAULT 1
);
CREATE TABLE IF NOT EXISTS roles (
  id_rol INT AUTO_INCREMENT PRIMARY KEY,
  nombre varchar(50) NOT NULL
, UNIQUE(nombre));
CREATE TABLE IF NOT EXISTS tipos_propiedad (
  id_tipo_propiedad INT AUTO_INCREMENT PRIMARY KEY,
  nombre varchar(50) NOT NULL
, UNIQUE(nombre));
CREATE TABLE IF NOT EXISTS tipos_transaccion (
  id_tipo_transaccion INT AUTO_INCREMENT PRIMARY KEY,
  nombre varchar(50) NOT NULL
, UNIQUE(nombre));
CREATE TABLE IF NOT EXISTS usuarios (
  id_usuario INT AUTO_INCREMENT PRIMARY KEY,
  nombre varchar(100) NOT NULL,
  apellido varchar(100) NOT NULL,
  dni varchar(20) NOT NULL,
  telefono varchar(30) DEFAULT NULL,
  email varchar(150) NOT NULL,
  password_hash varchar(255) NOT NULL,
  id_rol INT NOT NULL,
  activo INT DEFAULT 1,
  fecha_registro datetime DEFAULT current_timestamp()
, UNIQUE(dni), UNIQUE(email));
INSERT INTO caracteristicas (id_caracteristica,nombre) SELECT seed.id_caracteristica,seed.nombre FROM (VALUES (8, 'Aire Acondicionado'),
(1, 'Balcón'),
(7, 'Calefacción'),
(2, 'Cochera'),
(6, 'Jardín'),
(3, 'Patio'),
(5, 'Pileta'),
(10, 'Seguridad 24hs'),
(9, 'SUM'),
(4, 'Terraza')) seed(id_caracteristica,nombre) WHERE NOT EXISTS (SELECT 1 FROM caracteristicas existing WHERE existing.id_caracteristica=seed.id_caracteristica);
INSERT INTO estados_comerciales (id_estado_comercial,nombre) SELECT seed.id_estado_comercial,seed.nombre FROM (VALUES (4, 'Alquilada'),
(1, 'Disponible'),
(5, 'En trámite'),
(6, 'Fuera de mercado'),
(2, 'Reservada'),
(3, 'Vendida')) seed(id_estado_comercial,nombre) WHERE NOT EXISTS (SELECT 1 FROM estados_comerciales existing WHERE existing.id_estado_comercial=seed.id_estado_comercial);
INSERT INTO estados_verificacion (id_estado_verificacion,nombre) SELECT seed.id_estado_verificacion,seed.nombre FROM (VALUES (3, 'Aprobado'),
(1, 'Borrador'),
(5, 'Inactivo'),
(2, 'Pendiente de Verificación'),
(4, 'Rechazado')) seed(id_estado_verificacion,nombre) WHERE NOT EXISTS (SELECT 1 FROM estados_verificacion existing WHERE existing.id_estado_verificacion=seed.id_estado_verificacion);
INSERT INTO roles (id_rol,nombre) SELECT seed.id_rol,seed.nombre FROM (VALUES (1, 'Administrador'),
(2, 'Agente'),
(3, 'Cliente')) seed(id_rol,nombre) WHERE NOT EXISTS (SELECT 1 FROM roles existing WHERE existing.id_rol=seed.id_rol);
INSERT INTO tipos_propiedad (id_tipo_propiedad,nombre) SELECT seed.id_tipo_propiedad,seed.nombre FROM (VALUES (1, 'Casa'),
(6, 'Cochera'),
(2, 'Departamento'),
(3, 'Local'),
(4, 'Oficina'),
(5, 'Terreno')) seed(id_tipo_propiedad,nombre) WHERE NOT EXISTS (SELECT 1 FROM tipos_propiedad existing WHERE existing.id_tipo_propiedad=seed.id_tipo_propiedad);
INSERT INTO tipos_transaccion (id_tipo_transaccion,nombre) SELECT seed.id_tipo_transaccion,seed.nombre FROM (VALUES (2, 'Alquiler'),
(3, 'Alquiler Temporario'),
(1, 'Venta')) seed(id_tipo_transaccion,nombre) WHERE NOT EXISTS (SELECT 1 FROM tipos_transaccion existing WHERE existing.id_tipo_transaccion=seed.id_tipo_transaccion);
ALTER TABLE preferencias_cliente ADD CONSTRAINT IF NOT EXISTS preferencias_cliente_ibfk_1 FOREIGN KEY (id_cliente) REFERENCES usuarios (id_usuario) ON DELETE CASCADE;
ALTER TABLE preferencias_cliente ADD CONSTRAINT IF NOT EXISTS preferencias_cliente_ibfk_2 FOREIGN KEY (id_tipo_transaccion) REFERENCES tipos_transaccion (id_tipo_transaccion);
ALTER TABLE preferencias_cliente ADD CONSTRAINT IF NOT EXISTS preferencias_cliente_ibfk_3 FOREIGN KEY (id_tipo_propiedad) REFERENCES tipos_propiedad (id_tipo_propiedad);
ALTER TABLE propiedades ADD CONSTRAINT IF NOT EXISTS propiedades_ibfk_1 FOREIGN KEY (id_tipo_propiedad) REFERENCES tipos_propiedad (id_tipo_propiedad);
ALTER TABLE propiedades ADD CONSTRAINT IF NOT EXISTS propiedades_ibfk_2 FOREIGN KEY (id_tipo_transaccion) REFERENCES tipos_transaccion (id_tipo_transaccion);
ALTER TABLE propiedades ADD CONSTRAINT IF NOT EXISTS propiedades_ibfk_3 FOREIGN KEY (id_estado_verificacion) REFERENCES estados_verificacion (id_estado_verificacion);
ALTER TABLE propiedades ADD CONSTRAINT IF NOT EXISTS propiedades_ibfk_4 FOREIGN KEY (id_estado_comercial) REFERENCES estados_comerciales (id_estado_comercial);
ALTER TABLE propiedades ADD CONSTRAINT IF NOT EXISTS propiedades_ibfk_5 FOREIGN KEY (id_agente_creador) REFERENCES usuarios (id_usuario);
ALTER TABLE propiedades ADD CONSTRAINT IF NOT EXISTS propiedades_ibfk_6 FOREIGN KEY (id_admin_verificador) REFERENCES usuarios (id_usuario);
ALTER TABLE propiedades_cerradas ADD CONSTRAINT IF NOT EXISTS propiedades_cerradas_ibfk_1 FOREIGN KEY (id_propiedad) REFERENCES propiedades (id_propiedad);
ALTER TABLE propiedades_cerradas ADD CONSTRAINT IF NOT EXISTS propiedades_cerradas_ibfk_2 FOREIGN KEY (id_cliente_comprador) REFERENCES usuarios (id_usuario);
ALTER TABLE propiedades_cerradas ADD CONSTRAINT IF NOT EXISTS propiedades_cerradas_ibfk_3 FOREIGN KEY (id_agente_cierre) REFERENCES usuarios (id_usuario);
ALTER TABLE propiedad_caracteristicas ADD CONSTRAINT IF NOT EXISTS propiedad_caracteristicas_ibfk_1 FOREIGN KEY (id_propiedad) REFERENCES propiedades (id_propiedad) ON DELETE CASCADE;
ALTER TABLE propiedad_caracteristicas ADD CONSTRAINT IF NOT EXISTS propiedad_caracteristicas_ibfk_2 FOREIGN KEY (id_caracteristica) REFERENCES caracteristicas (id_caracteristica);
ALTER TABLE propiedad_multimedia ADD CONSTRAINT IF NOT EXISTS propiedad_multimedia_ibfk_1 FOREIGN KEY (id_propiedad) REFERENCES propiedades (id_propiedad) ON DELETE CASCADE;
ALTER TABLE usuarios ADD CONSTRAINT IF NOT EXISTS usuarios_ibfk_1 FOREIGN KEY (id_rol) REFERENCES roles (id_rol);

ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS direccion VARCHAR(255) DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS localidad VARCHAR(100) DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS fecha_nacimiento DATE DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS genero VARCHAR(40) DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS tipo_documento VARCHAR(20) NOT NULL DEFAULT 'DNI';
ALTER TABLE propiedades ALTER COLUMN id_agente_creador DROP NOT NULL;
CREATE TABLE IF NOT EXISTS edificios (
 id_edificio INT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(120) NOT NULL,
 direccion VARCHAR(255) NOT NULL, localidad VARCHAR(100) NOT NULL
);
ALTER TABLE propiedades ADD COLUMN IF NOT EXISTS id_propietario INT NULL;
ALTER TABLE propiedades ADD COLUMN IF NOT EXISTS id_edificio INT NULL;
ALTER TABLE propiedades ADD COLUMN IF NOT EXISTS numero_unidad VARCHAR(30) NULL;
ALTER TABLE propiedades ADD COLUMN IF NOT EXISTS capacidad INT NOT NULL DEFAULT 1;
ALTER TABLE propiedades ADD CONSTRAINT IF NOT EXISTS fk_propietario FOREIGN KEY(id_propietario) REFERENCES usuarios(id_usuario);
ALTER TABLE propiedades ADD CONSTRAINT IF NOT EXISTS fk_edificio FOREIGN KEY(id_edificio) REFERENCES edificios(id_edificio);
ALTER TABLE propiedades ADD CONSTRAINT IF NOT EXISTS uq_unidad UNIQUE(id_edificio,numero_unidad);
CREATE INDEX IF NOT EXISTS idx_catalogo ON propiedades(id_estado_verificacion,id_estado_comercial,localidad);
INSERT INTO tipos_propiedad(nombre) SELECT 'Depósito' WHERE NOT EXISTS (SELECT 1 FROM tipos_propiedad WHERE nombre='Depósito');
CREATE TABLE IF NOT EXISTS clientes (
 id_cliente INT PRIMARY KEY, fecha_inicio_relacion DATE NOT NULL,
 id_agente_asignado INT NULL,
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_agente_asignado) REFERENCES usuarios(id_usuario)
);
INSERT INTO clientes(id_cliente,fecha_inicio_relacion) SELECT u.id_usuario,DATE(u.fecha_registro) FROM usuarios u WHERE u.id_rol=3 AND NOT EXISTS (SELECT 1 FROM clientes c WHERE c.id_cliente=u.id_usuario) AND NOT EXISTS (SELECT 1 FROM schema_version WHERE version=2);
CREATE TABLE IF NOT EXISTS roles_cliente (
 id_cliente INT NOT NULL, tipo_rol VARCHAR(25) NOT NULL,
 PRIMARY KEY(id_cliente,tipo_rol), FOREIGN KEY(id_cliente) REFERENCES clientes(id_cliente)
);
CREATE TABLE IF NOT EXISTS convenios (
 id_convenio INT AUTO_INCREMENT PRIMARY KEY, id_propietario INT NOT NULL,
 id_propiedad INT NULL, profesional VARCHAR(150) NULL,
 condiciones TEXT NOT NULL, fecha DATE NOT NULL,
 FOREIGN KEY(id_propietario) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad)
);
CREATE TABLE IF NOT EXISTS propiedad_ofertas (
 id_propiedad INT NOT NULL, id_tipo_transaccion INT NOT NULL,
 precio DECIMAL(12,2) NOT NULL CHECK(precio>0), moneda VARCHAR(3) NOT NULL,
 PRIMARY KEY(id_propiedad,id_tipo_transaccion),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion)
);
INSERT INTO propiedad_ofertas SELECT p.id_propiedad,p.id_tipo_transaccion,p.precio,p.moneda FROM propiedades p WHERE NOT EXISTS (SELECT 1 FROM propiedad_ofertas o WHERE o.id_propiedad=p.id_propiedad AND o.id_tipo_transaccion=p.id_tipo_transaccion) AND NOT EXISTS (SELECT 1 FROM schema_version WHERE version=2);
CREATE TABLE IF NOT EXISTS documentos (
 id_documento INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL,
 tipo VARCHAR(40) NOT NULL, nombre VARCHAR(255) NOT NULL, archivo VARCHAR(500) NOT NULL,
 id_usuario INT NOT NULL, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario)
);
CREATE TABLE IF NOT EXISTS publicaciones (
 id_publicacion INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL,
 id_solicitante INT NOT NULL, id_revisor INT NULL,
 estado VARCHAR(25) NOT NULL DEFAULT 'Pendiente', observaciones TEXT NULL,
 fecha_solicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP, fecha_revision TIMESTAMP NULL,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_solicitante) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_revisor) REFERENCES usuarios(id_usuario)
);
INSERT INTO publicaciones(id_propiedad,id_solicitante,estado)
 SELECT p.id_propiedad,p.id_agente_creador,'Pendiente' FROM propiedades p WHERE p.id_estado_verificacion=2 AND p.id_agente_creador IS NOT NULL AND NOT EXISTS (SELECT 1 FROM publicaciones existing WHERE existing.id_propiedad=p.id_propiedad) AND NOT EXISTS (SELECT 1 FROM schema_version WHERE version=2);
CREATE TABLE IF NOT EXISTS contactos (
 id_contacto INT AUTO_INCREMENT PRIMARY KEY, id_cliente INT NOT NULL,
 id_agente INT NOT NULL, id_propiedad INT NULL, tipo VARCHAR(20) NOT NULL,
 fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP, descripcion TEXT NOT NULL, resultado TEXT NULL,
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_agente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad)
);
CREATE INDEX IF NOT EXISTS idx_contactos_cliente ON contactos(id_cliente,fecha);
CREATE TABLE IF NOT EXISTS intereses (
 id_cliente INT NOT NULL, id_propiedad INT NOT NULL,
 estado VARCHAR(30) NOT NULL DEFAULT 'Interesado', acciones TEXT NULL,
 fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id_cliente,id_propiedad),
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad)
);
ALTER TABLE preferencias_cliente ADD COLUMN IF NOT EXISTS moneda VARCHAR(3) NOT NULL DEFAULT 'USD';
CREATE TABLE IF NOT EXISTS preferencias_caracteristicas (
 id_preferencia INT NOT NULL, id_caracteristica INT NOT NULL,
 PRIMARY KEY(id_preferencia,id_caracteristica),
 FOREIGN KEY(id_preferencia) REFERENCES preferencias_cliente(id_preferencia),
 FOREIGN KEY(id_caracteristica) REFERENCES caracteristicas(id_caracteristica)
);
CREATE TABLE IF NOT EXISTS notificaciones (
 id_notificacion INT AUTO_INCREMENT PRIMARY KEY, id_usuario INT NOT NULL,
 mensaje VARCHAR(500) NOT NULL, leida INT NOT NULL DEFAULT 0,
 fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario)
);
CREATE TABLE IF NOT EXISTS auditoria (
 id_evento INT AUTO_INCREMENT PRIMARY KEY, id_usuario INT NOT NULL,
 accion VARCHAR(100) NOT NULL, entidad VARCHAR(40) NOT NULL, id_registro INT NOT NULL,
 detalle TEXT NULL, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario)
);
CREATE TABLE IF NOT EXISTS historial_estados (
 id_historial INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL,
 id_usuario INT NOT NULL, estado_anterior INT NOT NULL, estado_nuevo INT NOT NULL,
 motivo VARCHAR(500) NOT NULL, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(estado_anterior) REFERENCES estados_comerciales(id_estado_comercial),
 FOREIGN KEY(estado_nuevo) REFERENCES estados_comerciales(id_estado_comercial)
);
CREATE INDEX IF NOT EXISTS idx_cierre_propiedad ON propiedades_cerradas(id_propiedad);

ALTER TABLE propiedades_cerradas ADD COLUMN IF NOT EXISTS id_tipo_transaccion INT NULL;
ALTER TABLE propiedades_cerradas ADD COLUMN IF NOT EXISTS moneda VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE propiedades_cerradas ADD CONSTRAINT IF NOT EXISTS fk_cierre_tipo FOREIGN KEY(id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion);




CREATE TABLE IF NOT EXISTS visitas (
 id_visita INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL,
 id_cliente INT NOT NULL, id_agente INT NOT NULL, fecha_hora TIMESTAMP NOT NULL,
 estado VARCHAR(20) NOT NULL DEFAULT 'Programada', resultado TEXT NULL,
 observaciones TEXT NULL, fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_agente) REFERENCES usuarios(id_usuario)
);
CREATE TABLE IF NOT EXISTS tareas_seguimiento (
 id_tarea INT AUTO_INCREMENT PRIMARY KEY, id_agente INT NOT NULL,
 id_cliente INT NOT NULL, id_propiedad INT NULL, titulo VARCHAR(180) NOT NULL,
 detalle TEXT NULL, fecha_limite TIMESTAMP NOT NULL,
 estado VARCHAR(20) NOT NULL DEFAULT 'Pendiente', fecha_completada TIMESTAMP NULL,
 fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_agente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad)
);
CREATE TABLE IF NOT EXISTS ofertas_cliente (
 id_oferta INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL, id_cliente INT NOT NULL,
 id_agente INT NOT NULL, id_tipo_transaccion INT NOT NULL, monto DECIMAL(12,2) NOT NULL,
 moneda VARCHAR(3) NOT NULL, fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 fecha_vencimiento DATE NULL, estado VARCHAR(20) NOT NULL DEFAULT 'Pendiente',
 observaciones TEXT NULL, id_revisor INT NULL, fecha_revision TIMESTAMP NULL,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_agente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion),
 FOREIGN KEY(id_revisor) REFERENCES usuarios(id_usuario)
);
CREATE TABLE IF NOT EXISTS reservas (
 id_reserva INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL, id_cliente INT NOT NULL,
 id_agente INT NOT NULL, id_tipo_transaccion INT NOT NULL, id_oferta INT NULL,
 monto DECIMAL(12,2) NOT NULL, moneda VARCHAR(3) NOT NULL,
 fecha_reserva TIMESTAMP DEFAULT CURRENT_TIMESTAMP, fecha_limite TIMESTAMP NOT NULL,
 estado VARCHAR(20) NOT NULL DEFAULT 'Pendiente', estado_pago VARCHAR(20) NOT NULL DEFAULT 'Pendiente',
 medio_pago VARCHAR(80) NULL, observaciones TEXT NULL, id_autorizador INT NULL,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_agente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion),
 FOREIGN KEY(id_oferta) REFERENCES ofertas_cliente(id_oferta),
 FOREIGN KEY(id_autorizador) REFERENCES usuarios(id_usuario)
);
CREATE TABLE IF NOT EXISTS historial_precios (
 id_historial_precio INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL,
 id_tipo_transaccion INT NOT NULL, id_usuario INT NOT NULL,
 precio_anterior DECIMAL(12,2) NOT NULL, precio_nuevo DECIMAL(12,2) NOT NULL,
 moneda VARCHAR(3) NOT NULL, motivo VARCHAR(500) NULL,
 fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion),
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario)
);
CREATE TABLE IF NOT EXISTS historial_asignaciones (
 id_asignacion INT AUTO_INCREMENT PRIMARY KEY, tipo_entidad VARCHAR(20) NOT NULL,
 id_entidad INT NOT NULL, agente_anterior INT NULL, agente_nuevo INT NULL,
 id_usuario_cambio INT NOT NULL, motivo VARCHAR(500) NULL, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(agente_anterior) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(agente_nuevo) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_usuario_cambio) REFERENCES usuarios(id_usuario)
);
CREATE TABLE IF NOT EXISTS requisitos_publicacion (
 id_requisito INT AUTO_INCREMENT PRIMARY KEY, id_tipo_propiedad INT NULL,
 nombre VARCHAR(120) NOT NULL, obligatorio BOOLEAN NOT NULL DEFAULT TRUE,
 activo BOOLEAN NOT NULL DEFAULT TRUE,
 FOREIGN KEY(id_tipo_propiedad) REFERENCES tipos_propiedad(id_tipo_propiedad)
);
CREATE TABLE IF NOT EXISTS checklist_publicacion (
 id_propiedad INT NOT NULL, id_requisito INT NOT NULL, id_documento INT NULL,
 estado VARCHAR(30) NOT NULL DEFAULT 'Pendiente', observaciones VARCHAR(500) NULL,
 id_revisor INT NULL, fecha_revision TIMESTAMP NULL,
 PRIMARY KEY(id_propiedad,id_requisito),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad) ON DELETE CASCADE,
 FOREIGN KEY(id_requisito) REFERENCES requisitos_publicacion(id_requisito),
 FOREIGN KEY(id_documento) REFERENCES documentos(id_documento),
 FOREIGN KEY(id_revisor) REFERENCES usuarios(id_usuario)
);
INSERT INTO requisitos_publicacion(id_tipo_propiedad,nombre,obligatorio) SELECT seed.tipo,seed.nombre,seed.obligatorio FROM (VALUES (CAST(NULL AS INT),'Autorización de comercialización',TRUE),
 (CAST(NULL AS INT),'Documento que acredita titularidad',TRUE),
 (CAST(NULL AS INT),'Fotografías principales',TRUE)) seed(tipo,nombre,obligatorio) WHERE NOT EXISTS (SELECT 1 FROM requisitos_publicacion r WHERE r.nombre=seed.nombre AND r.id_tipo_propiedad IS NULL);
CREATE TABLE IF NOT EXISTS contactos_adicionales_cliente (
 id_contacto INT AUTO_INCREMENT PRIMARY KEY, id_cliente INT NOT NULL,
 tipo VARCHAR(20) NOT NULL, valor VARCHAR(255) NOT NULL,
 es_principal BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(id_cliente) REFERENCES clientes(id_cliente) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS configuracion_alertas (
 clave VARCHAR(80) PRIMARY KEY, valor VARCHAR(255) NOT NULL,
 descripcion VARCHAR(255) NULL, actualizado_por INT NULL,
 fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(actualizado_por) REFERENCES usuarios(id_usuario)
);
CREATE TABLE IF NOT EXISTS auditoria_cambios (
 id_cambio INT AUTO_INCREMENT PRIMARY KEY, id_evento INT NULL,
 entidad VARCHAR(40) NOT NULL, id_registro INT NOT NULL,
 campo VARCHAR(80) NOT NULL, valor_anterior TEXT NULL, valor_nuevo TEXT NULL,
 motivo VARCHAR(500) NULL,
 FOREIGN KEY(id_evento) REFERENCES auditoria(id_evento)
);
INSERT INTO configuracion_alertas(clave,valor,descripcion) SELECT seed.clave,seed.valor,seed.descripcion FROM (VALUES ('dias_cliente_sin_contacto','14','Días sin contacto para alertar al agente.'),
 ('dias_propiedad_sin_actividad','30','Días sin actividad para alertar al responsable.'),
 ('dias_aviso_reserva','3','Días antes del vencimiento para avisar al equipo.')) seed(clave,valor,descripcion) WHERE NOT EXISTS (SELECT 1 FROM configuracion_alertas c WHERE c.clave=seed.clave);

INSERT INTO schema_version(version) SELECT 2 WHERE NOT EXISTS (SELECT 1 FROM schema_version WHERE version=2);
