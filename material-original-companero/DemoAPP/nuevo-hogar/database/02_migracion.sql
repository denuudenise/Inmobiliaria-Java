-- Ejecutar UNA VEZ luego de 01_original.sql sobre una copia de respaldo.
-- Compatible con MySQL 8 y MariaDB 10.4+. No elimina registros.

ALTER TABLE usuarios ADD COLUMN direccion VARCHAR(255) DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN localidad VARCHAR(100) DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN fecha_nacimiento DATE DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN genero VARCHAR(40) DEFAULT NULL;
ALTER TABLE usuarios ADD COLUMN tipo_documento VARCHAR(20) NOT NULL DEFAULT 'DNI';
ALTER TABLE propiedades MODIFY id_agente_creador INT NULL;
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
ALTER TABLE propiedades_cerradas DROP INDEX id_propiedad;
ALTER TABLE propiedades_cerradas ADD COLUMN id_tipo_transaccion INT NULL;
ALTER TABLE propiedades_cerradas ADD COLUMN moneda VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE propiedades_cerradas ADD CONSTRAINT fk_cierre_tipo FOREIGN KEY(id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion);
UPDATE propiedades_cerradas c JOIN propiedades p ON p.id_propiedad=c.id_propiedad SET c.id_tipo_transaccion=p.id_tipo_transaccion,c.moneda=p.moneda;
CREATE TABLE schema_version (version INT PRIMARY KEY, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
INSERT INTO schema_version(version) VALUES(2);
