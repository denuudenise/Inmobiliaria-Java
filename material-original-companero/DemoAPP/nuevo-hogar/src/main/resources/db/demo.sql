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
INSERT INTO `caracteristicas` (`id_caracteristica`, `nombre`) VALUES
(8, 'Aire Acondicionado'),
(1, 'Balcón'),
(7, 'Calefacción'),
(2, 'Cochera'),
(6, 'Jardín'),
(3, 'Patio'),
(5, 'Pileta'),
(10, 'Seguridad 24hs'),
(9, 'SUM'),
(4, 'Terraza');
INSERT INTO `estados_comerciales` (`id_estado_comercial`, `nombre`) VALUES
(4, 'Alquilada'),
(1, 'Disponible'),
(5, 'En trámite'),
(6, 'Fuera de mercado'),
(2, 'Reservada'),
(3, 'Vendida');
INSERT INTO `estados_verificacion` (`id_estado_verificacion`, `nombre`) VALUES
(3, 'Aprobado'),
(1, 'Borrador'),
(5, 'Inactivo'),
(2, 'Pendiente de Verificación'),
(4, 'Rechazado');
INSERT INTO `roles` (`id_rol`, `nombre`) VALUES
(1, 'Administrador'),
(2, 'Agente'),
(3, 'Cliente');
INSERT INTO `tipos_propiedad` (`id_tipo_propiedad`, `nombre`) VALUES
(1, 'Casa'),
(6, 'Cochera'),
(2, 'Departamento'),
(3, 'Local'),
(4, 'Oficina'),
(5, 'Terreno');
INSERT INTO `tipos_transaccion` (`id_tipo_transaccion`, `nombre`) VALUES
(2, 'Alquiler'),
(3, 'Alquiler Temporario'),
(1, 'Venta');
ALTER TABLE preferencias_cliente ADD CONSTRAINT preferencias_cliente_ibfk_1 FOREIGN KEY (id_cliente) REFERENCES usuarios (id_usuario) ON DELETE CASCADE;
ALTER TABLE preferencias_cliente ADD CONSTRAINT preferencias_cliente_ibfk_2 FOREIGN KEY (id_tipo_transaccion) REFERENCES tipos_transaccion (id_tipo_transaccion);
ALTER TABLE preferencias_cliente ADD CONSTRAINT preferencias_cliente_ibfk_3 FOREIGN KEY (id_tipo_propiedad) REFERENCES tipos_propiedad (id_tipo_propiedad);
ALTER TABLE propiedades ADD CONSTRAINT propiedades_ibfk_1 FOREIGN KEY (id_tipo_propiedad) REFERENCES tipos_propiedad (id_tipo_propiedad);
ALTER TABLE propiedades ADD CONSTRAINT propiedades_ibfk_2 FOREIGN KEY (id_tipo_transaccion) REFERENCES tipos_transaccion (id_tipo_transaccion);
ALTER TABLE propiedades ADD CONSTRAINT propiedades_ibfk_3 FOREIGN KEY (id_estado_verificacion) REFERENCES estados_verificacion (id_estado_verificacion);
ALTER TABLE propiedades ADD CONSTRAINT propiedades_ibfk_4 FOREIGN KEY (id_estado_comercial) REFERENCES estados_comerciales (id_estado_comercial);
ALTER TABLE propiedades ADD CONSTRAINT propiedades_ibfk_5 FOREIGN KEY (id_agente_creador) REFERENCES usuarios (id_usuario);
ALTER TABLE propiedades ADD CONSTRAINT propiedades_ibfk_6 FOREIGN KEY (id_admin_verificador) REFERENCES usuarios (id_usuario);
ALTER TABLE propiedades_cerradas ADD CONSTRAINT propiedades_cerradas_ibfk_1 FOREIGN KEY (id_propiedad) REFERENCES propiedades (id_propiedad);
ALTER TABLE propiedades_cerradas ADD CONSTRAINT propiedades_cerradas_ibfk_2 FOREIGN KEY (id_cliente_comprador) REFERENCES usuarios (id_usuario);
ALTER TABLE propiedades_cerradas ADD CONSTRAINT propiedades_cerradas_ibfk_3 FOREIGN KEY (id_agente_cierre) REFERENCES usuarios (id_usuario);
ALTER TABLE propiedad_caracteristicas ADD CONSTRAINT propiedad_caracteristicas_ibfk_1 FOREIGN KEY (id_propiedad) REFERENCES propiedades (id_propiedad) ON DELETE CASCADE;
ALTER TABLE propiedad_caracteristicas ADD CONSTRAINT propiedad_caracteristicas_ibfk_2 FOREIGN KEY (id_caracteristica) REFERENCES caracteristicas (id_caracteristica);
ALTER TABLE propiedad_multimedia ADD CONSTRAINT propiedad_multimedia_ibfk_1 FOREIGN KEY (id_propiedad) REFERENCES propiedades (id_propiedad) ON DELETE CASCADE;
ALTER TABLE usuarios ADD CONSTRAINT usuarios_ibfk_1 FOREIGN KEY (id_rol) REFERENCES roles (id_rol);

ALTER TABLE usuarios ADD COLUMN direccion VARCHAR(255) DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN localidad VARCHAR(100) DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN fecha_nacimiento DATE DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN genero VARCHAR(40) DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN tipo_documento VARCHAR(20) NOT NULL DEFAULT 'DNI';
ALTER TABLE propiedades ALTER COLUMN id_agente_creador DROP NOT NULL;
CREATE TABLE edificios (
 id_edificio INT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(120) NOT NULL,
 direccion VARCHAR(255) NOT NULL, localidad VARCHAR(100) NOT NULL
);
ALTER TABLE propiedades ADD COLUMN id_propietario INT NULL;
ALTER TABLE propiedades ADD COLUMN id_edificio INT NULL;
ALTER TABLE propiedades ADD COLUMN numero_unidad VARCHAR(30) NULL;
ALTER TABLE propiedades ADD COLUMN capacidad INT NOT NULL DEFAULT 1;
ALTER TABLE propiedades ADD CONSTRAINT fk_propietario FOREIGN KEY(id_propietario) REFERENCES usuarios(id_usuario);
ALTER TABLE propiedades ADD CONSTRAINT fk_edificio FOREIGN KEY(id_edificio) REFERENCES edificios(id_edificio);
ALTER TABLE propiedades ADD CONSTRAINT uq_unidad UNIQUE(id_edificio,numero_unidad);
CREATE INDEX idx_catalogo ON propiedades(id_estado_verificacion,id_estado_comercial,localidad);
INSERT INTO tipos_propiedad(nombre) VALUES ('Depósito');
CREATE TABLE clientes (
 id_cliente INT PRIMARY KEY, fecha_inicio_relacion DATE NOT NULL,
 id_agente_asignado INT NULL,
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_agente_asignado) REFERENCES usuarios(id_usuario)
);
INSERT INTO clientes(id_cliente,fecha_inicio_relacion) SELECT id_usuario,DATE(fecha_registro) FROM usuarios WHERE id_rol=3;
CREATE TABLE roles_cliente (
 id_cliente INT NOT NULL, tipo_rol VARCHAR(25) NOT NULL,
 PRIMARY KEY(id_cliente,tipo_rol), FOREIGN KEY(id_cliente) REFERENCES clientes(id_cliente)
);
CREATE TABLE convenios (
 id_convenio INT AUTO_INCREMENT PRIMARY KEY, id_propietario INT NOT NULL,
 id_propiedad INT NULL, profesional VARCHAR(150) NULL,
 condiciones TEXT NOT NULL, fecha DATE NOT NULL,
 FOREIGN KEY(id_propietario) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad)
);
CREATE TABLE propiedad_ofertas (
 id_propiedad INT NOT NULL, id_tipo_transaccion INT NOT NULL,
 precio DECIMAL(12,2) NOT NULL CHECK(precio>0), moneda VARCHAR(3) NOT NULL,
 PRIMARY KEY(id_propiedad,id_tipo_transaccion),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion)
);
INSERT INTO propiedad_ofertas SELECT id_propiedad,id_tipo_transaccion,precio,moneda FROM propiedades;
CREATE TABLE documentos (
 id_documento INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL,
 tipo VARCHAR(40) NOT NULL, nombre VARCHAR(255) NOT NULL, archivo VARCHAR(500) NOT NULL,
 id_usuario INT NOT NULL, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario)
);
CREATE TABLE publicaciones (
 id_publicacion INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL,
 id_solicitante INT NOT NULL, id_revisor INT NULL,
 estado VARCHAR(25) NOT NULL DEFAULT 'Pendiente', observaciones TEXT NULL,
 fecha_solicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP, fecha_revision TIMESTAMP NULL,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_solicitante) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_revisor) REFERENCES usuarios(id_usuario)
);
INSERT INTO publicaciones(id_propiedad,id_solicitante,estado)
 SELECT id_propiedad,id_agente_creador,'Pendiente' FROM propiedades WHERE id_estado_verificacion=2 AND id_agente_creador IS NOT NULL;
CREATE TABLE contactos (
 id_contacto INT AUTO_INCREMENT PRIMARY KEY, id_cliente INT NOT NULL,
 id_agente INT NOT NULL, id_propiedad INT NULL, tipo VARCHAR(20) NOT NULL,
 fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP, descripcion TEXT NOT NULL, resultado TEXT NULL,
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_agente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad)
);
CREATE INDEX idx_contactos_cliente ON contactos(id_cliente,fecha);
CREATE TABLE intereses (
 id_cliente INT NOT NULL, id_propiedad INT NOT NULL,
 estado VARCHAR(30) NOT NULL DEFAULT 'Interesado', acciones TEXT NULL,
 fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id_cliente,id_propiedad),
 FOREIGN KEY(id_cliente) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad)
);
ALTER TABLE preferencias_cliente ADD COLUMN moneda VARCHAR(3) NOT NULL DEFAULT 'USD';
CREATE TABLE preferencias_caracteristicas (
 id_preferencia INT NOT NULL, id_caracteristica INT NOT NULL,
 PRIMARY KEY(id_preferencia,id_caracteristica),
 FOREIGN KEY(id_preferencia) REFERENCES preferencias_cliente(id_preferencia),
 FOREIGN KEY(id_caracteristica) REFERENCES caracteristicas(id_caracteristica)
);
CREATE TABLE notificaciones (
 id_notificacion INT AUTO_INCREMENT PRIMARY KEY, id_usuario INT NOT NULL,
 mensaje VARCHAR(500) NOT NULL, leida INT NOT NULL DEFAULT 0,
 fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario)
);
CREATE TABLE auditoria (
 id_evento INT AUTO_INCREMENT PRIMARY KEY, id_usuario INT NOT NULL,
 accion VARCHAR(100) NOT NULL, entidad VARCHAR(40) NOT NULL, id_registro INT NOT NULL,
 detalle TEXT NULL, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario)
);
CREATE TABLE historial_estados (
 id_historial INT AUTO_INCREMENT PRIMARY KEY, id_propiedad INT NOT NULL,
 id_usuario INT NOT NULL, estado_anterior INT NOT NULL, estado_nuevo INT NOT NULL,
 motivo VARCHAR(500) NOT NULL, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario),
 FOREIGN KEY(estado_anterior) REFERENCES estados_comerciales(id_estado_comercial),
 FOREIGN KEY(estado_nuevo) REFERENCES estados_comerciales(id_estado_comercial)
);
CREATE INDEX idx_cierre_propiedad ON propiedades_cerradas(id_propiedad);

ALTER TABLE propiedades_cerradas ADD COLUMN id_tipo_transaccion INT NULL;
ALTER TABLE propiedades_cerradas ADD COLUMN moneda VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE propiedades_cerradas ADD CONSTRAINT fk_cierre_tipo FOREIGN KEY(id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion);

CREATE TABLE schema_version (version INT PRIMARY KEY, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
INSERT INTO schema_version(version) VALUES(2);
