-- Compatibilidad con el flujo de solicitudes y gestión de la versión web.
-- Ejecutar una sola vez sobre una copia de seguridad de inmobiliaria.
-- El script usa ADD COLUMN IF NOT EXISTS de MariaDB 10.4+.

CREATE TABLE IF NOT EXISTS solicitudes_publicacion (
  id_solicitud INT AUTO_INCREMENT PRIMARY KEY,
  id_cliente INT NOT NULL,
  id_tipo_propiedad INT NOT NULL,
  id_tipo_transaccion INT NOT NULL,
  id_estado_verificacion INT NOT NULL DEFAULT 2,
  titulo VARCHAR(200) NOT NULL,
  direccion VARCHAR(255) NOT NULL,
  localidad VARCHAR(100) NOT NULL,
  descripcion TEXT NOT NULL,
  precio_estimado DECIMAL(12,2) NULL,
  superficie_total_m2 DECIMAL(8,2) NOT NULL,
  ambientes INT NULL,
  dormitorios INT NULL,
  banos INT NULL,
  antiguedad_anos INT NOT NULL DEFAULT 0,
  motivo_rechazo TEXT NULL,
  fecha_solicitud DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion DATETIME NULL,
  id_agente INT NULL,
  moneda VARCHAR(3) NOT NULL DEFAULT 'USD',
  faltan_planos TINYINT(1) NOT NULL DEFAULT 0,
  falta_escritura TINYINT(1) NOT NULL DEFAULT 0,
  observaciones_documentacion TEXT NULL,
  CONSTRAINT fk_solicitud_cliente_app FOREIGN KEY (id_cliente) REFERENCES usuarios(id_usuario),
  CONSTRAINT fk_solicitud_tipo_app FOREIGN KEY (id_tipo_propiedad) REFERENCES tipos_propiedad(id_tipo_propiedad),
  CONSTRAINT fk_solicitud_operacion_app FOREIGN KEY (id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion),
  CONSTRAINT fk_solicitud_agente_app FOREIGN KEY (id_agente) REFERENCES usuarios(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE solicitudes_publicacion ADD COLUMN IF NOT EXISTS id_agente INT NULL;
ALTER TABLE solicitudes_publicacion ADD COLUMN IF NOT EXISTS moneda VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE solicitudes_publicacion ADD COLUMN IF NOT EXISTS faltan_planos TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE solicitudes_publicacion ADD COLUMN IF NOT EXISTS falta_escritura TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE solicitudes_publicacion ADD COLUMN IF NOT EXISTS observaciones_documentacion TEXT NULL;

CREATE TABLE IF NOT EXISTS solicitud_multimedia (
  id_multimedia INT AUTO_INCREMENT PRIMARY KEY,
  id_solicitud INT NOT NULL,
  url_archivo VARCHAR(500) NOT NULL,
  tipo VARCHAR(20) NOT NULL DEFAULT 'Imagen',
  fecha_carga DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_solicitud_multimedia_app FOREIGN KEY (id_solicitud)
    REFERENCES solicitudes_publicacion(id_solicitud) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS solicitud_ofertas (
  id_solicitud INT NOT NULL,
  id_tipo_transaccion INT NOT NULL,
  precio DECIMAL(12,2) NOT NULL,
  moneda VARCHAR(3) NOT NULL,
  precio_minimo DECIMAL(12,2) NULL,
  PRIMARY KEY (id_solicitud,id_tipo_transaccion),
  CONSTRAINT fk_solicitud_ofertas_app FOREIGN KEY (id_solicitud)
    REFERENCES solicitudes_publicacion(id_solicitud) ON DELETE CASCADE,
  CONSTRAINT fk_solicitud_oferta_tipo_app FOREIGN KEY (id_tipo_transaccion)
    REFERENCES tipos_transaccion(id_tipo_transaccion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE solicitud_ofertas ADD COLUMN IF NOT EXISTS precio_minimo DECIMAL(12,2) NULL;

CREATE TABLE IF NOT EXISTS solicitud_caracteristicas (
  id_solicitud INT NOT NULL,
  id_caracteristica INT NOT NULL,
  PRIMARY KEY (id_solicitud,id_caracteristica),
  CONSTRAINT fk_solicitud_caracteristicas_app FOREIGN KEY (id_solicitud)
    REFERENCES solicitudes_publicacion(id_solicitud) ON DELETE CASCADE,
  CONSTRAINT fk_solicitud_caracteristica_app FOREIGN KEY (id_caracteristica)
    REFERENCES caracteristicas(id_caracteristica)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE propiedades ADD COLUMN IF NOT EXISTS id_solicitud INT NULL;
ALTER TABLE propiedades ADD COLUMN IF NOT EXISTS faltan_planos TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE propiedades ADD COLUMN IF NOT EXISTS falta_escritura TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE propiedades ADD COLUMN IF NOT EXISTS observaciones_documentacion TEXT NULL;
ALTER TABLE propiedad_ofertas ADD COLUMN IF NOT EXISTS precio_minimo DECIMAL(12,2) NULL;

ALTER TABLE documentos MODIFY id_propiedad INT NULL;
ALTER TABLE documentos ADD COLUMN IF NOT EXISTS id_solicitud INT NULL;

ALTER TABLE contactos MODIFY id_cliente INT NULL;
ALTER TABLE contactos ADD COLUMN IF NOT EXISTS nombre_contacto VARCHAR(120) NULL;
ALTER TABLE contactos ADD COLUMN IF NOT EXISTS email_contacto VARCHAR(190) NULL;
ALTER TABLE contactos ADD COLUMN IF NOT EXISTS telefono_contacto VARCHAR(40) NULL;
ALTER TABLE contactos ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'Pendiente';
ALTER TABLE contactos ADD COLUMN IF NOT EXISTS fecha_respuesta DATETIME NULL;

CREATE TABLE IF NOT EXISTS propiedad_impuestos (
  id_impuesto INT AUTO_INCREMENT PRIMARY KEY,
  id_propiedad INT NULL,
  id_solicitud INT NULL,
  concepto VARCHAR(100) NOT NULL,
  base DECIMAL(12,2) NOT NULL,
  porcentaje DECIMAL(5,2) NOT NULL,
  importe DECIMAL(12,2) NOT NULL,
  moneda VARCHAR(3) NOT NULL,
  periodo VARCHAR(80) NOT NULL,
  id_usuario INT NOT NULL,
  fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_impuesto_propiedad_app FOREIGN KEY (id_propiedad) REFERENCES propiedades(id_propiedad),
  CONSTRAINT fk_impuesto_solicitud_app FOREIGN KEY (id_solicitud) REFERENCES solicitudes_publicacion(id_solicitud),
  CONSTRAINT fk_impuesto_usuario_app FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE propiedad_impuestos MODIFY periodo VARCHAR(80) NOT NULL;

-- Conserva los cÃ¡lculos guardados por versiones anteriores de la app.
SET @copiar_impuestos_anteriores = IF(
  EXISTS (SELECT 1 FROM information_schema.tables
          WHERE table_schema=DATABASE() AND table_name='impuestos_propiedad'),
  'INSERT INTO propiedad_impuestos (id_propiedad,id_solicitud,concepto,base,porcentaje,importe,moneda,periodo,id_usuario,fecha) SELECT i.id_propiedad,NULL,i.nombre,i.base_imponible,i.porcentaje,i.importe,i.moneda,i.periodo,i.id_usuario,i.fecha FROM impuestos_propiedad i',
  'SELECT 1'
);
PREPARE copiar_impuestos_stmt FROM @copiar_impuestos_anteriores;
EXECUTE copiar_impuestos_stmt;
DEALLOCATE PREPARE copiar_impuestos_stmt;
