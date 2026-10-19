-- Módulos comerciales para Nuevo Hogar.
-- Migración aditiva e idempotente para bases que ya tienen schema_version = 2.
-- Ejecutar una vez desde phpMyAdmin seleccionando la base inmobiliaria.

CREATE TABLE IF NOT EXISTS visitas (
  id_visita INT AUTO_INCREMENT PRIMARY KEY,
  id_propiedad INT NOT NULL,
  id_cliente INT NOT NULL,
  id_agente INT NOT NULL,
  fecha_hora DATETIME NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'Programada',
  resultado TEXT NULL,
  observaciones TEXT NULL,
  fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (id_propiedad) REFERENCES propiedades(id_propiedad),
  FOREIGN KEY (id_cliente) REFERENCES usuarios(id_usuario),
  FOREIGN KEY (id_agente) REFERENCES usuarios(id_usuario),
  INDEX idx_visita_agente_fecha (id_agente, fecha_hora),
  INDEX idx_visita_cliente_fecha (id_cliente, fecha_hora)
);

CREATE TABLE IF NOT EXISTS tareas_seguimiento (
  id_tarea INT AUTO_INCREMENT PRIMARY KEY,
  id_agente INT NOT NULL,
  id_cliente INT NOT NULL,
  id_propiedad INT NULL,
  titulo VARCHAR(180) NOT NULL,
  detalle TEXT NULL,
  fecha_limite DATETIME NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'Pendiente',
  fecha_completada DATETIME NULL,
  fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (id_agente) REFERENCES usuarios(id_usuario),
  FOREIGN KEY (id_cliente) REFERENCES usuarios(id_usuario),
  FOREIGN KEY (id_propiedad) REFERENCES propiedades(id_propiedad),
  INDEX idx_tareas_agente_estado_fecha (id_agente, estado, fecha_limite)
);

CREATE TABLE IF NOT EXISTS ofertas_cliente (
  id_oferta INT AUTO_INCREMENT PRIMARY KEY,
  id_propiedad INT NOT NULL,
  id_cliente INT NOT NULL,
  id_agente INT NOT NULL,
  id_tipo_transaccion INT NOT NULL,
  monto DECIMAL(12,2) NOT NULL,
  moneda VARCHAR(3) NOT NULL,
  fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_vencimiento DATE NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'Pendiente',
  observaciones TEXT NULL,
  id_revisor INT NULL,
  fecha_revision DATETIME NULL,
  FOREIGN KEY (id_propiedad) REFERENCES propiedades(id_propiedad),
  FOREIGN KEY (id_cliente) REFERENCES usuarios(id_usuario),
  FOREIGN KEY (id_agente) REFERENCES usuarios(id_usuario),
  FOREIGN KEY (id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion),
  FOREIGN KEY (id_revisor) REFERENCES usuarios(id_usuario),
  INDEX idx_ofertas_estado_fecha (estado, fecha_vencimiento),
  INDEX idx_ofertas_propiedad (id_propiedad, fecha_creacion)
);

CREATE TABLE IF NOT EXISTS reservas (
  id_reserva INT AUTO_INCREMENT PRIMARY KEY,
  id_propiedad INT NOT NULL,
  id_cliente INT NOT NULL,
  id_agente INT NOT NULL,
  id_tipo_transaccion INT NOT NULL,
  id_oferta INT NULL,
  monto DECIMAL(12,2) NOT NULL,
  moneda VARCHAR(3) NOT NULL,
  fecha_reserva DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_limite DATETIME NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'Pendiente',
  estado_pago VARCHAR(20) NOT NULL DEFAULT 'Pendiente',
  medio_pago VARCHAR(80) NULL,
  observaciones TEXT NULL,
  id_autorizador INT NULL,
  FOREIGN KEY (id_propiedad) REFERENCES propiedades(id_propiedad),
  FOREIGN KEY (id_cliente) REFERENCES usuarios(id_usuario),
  FOREIGN KEY (id_agente) REFERENCES usuarios(id_usuario),
  FOREIGN KEY (id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion),
  FOREIGN KEY (id_oferta) REFERENCES ofertas_cliente(id_oferta),
  FOREIGN KEY (id_autorizador) REFERENCES usuarios(id_usuario),
  INDEX idx_reservas_estado_limite (estado, fecha_limite),
  INDEX idx_reservas_propiedad (id_propiedad, estado)
);

CREATE TABLE IF NOT EXISTS historial_precios (
  id_historial_precio INT AUTO_INCREMENT PRIMARY KEY,
  id_propiedad INT NOT NULL,
  id_tipo_transaccion INT NOT NULL,
  id_usuario INT NOT NULL,
  precio_anterior DECIMAL(12,2) NOT NULL,
  precio_nuevo DECIMAL(12,2) NOT NULL,
  moneda VARCHAR(3) NOT NULL,
  motivo VARCHAR(500) NULL,
  fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (id_propiedad) REFERENCES propiedades(id_propiedad),
  FOREIGN KEY (id_tipo_transaccion) REFERENCES tipos_transaccion(id_tipo_transaccion),
  FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
  INDEX idx_historial_precio_propiedad (id_propiedad, fecha)
);

CREATE TABLE IF NOT EXISTS historial_asignaciones (
  id_asignacion INT AUTO_INCREMENT PRIMARY KEY,
  tipo_entidad VARCHAR(20) NOT NULL,
  id_entidad INT NOT NULL,
  agente_anterior INT NULL,
  agente_nuevo INT NULL,
  id_usuario_cambio INT NOT NULL,
  motivo VARCHAR(500) NULL,
  fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (agente_anterior) REFERENCES usuarios(id_usuario),
  FOREIGN KEY (agente_nuevo) REFERENCES usuarios(id_usuario),
  FOREIGN KEY (id_usuario_cambio) REFERENCES usuarios(id_usuario),
  INDEX idx_historial_asignacion_entidad (tipo_entidad, id_entidad, fecha)
);

CREATE TABLE IF NOT EXISTS contactos_adicionales_cliente (
  id_contacto INT AUTO_INCREMENT PRIMARY KEY,
  id_cliente INT NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  valor VARCHAR(255) NOT NULL,
  es_principal TINYINT(1) NOT NULL DEFAULT 0,
  FOREIGN KEY (id_cliente) REFERENCES clientes(id_cliente) ON DELETE CASCADE,
  INDEX idx_contactos_cliente_tipo (id_cliente, tipo)
);

CREATE TABLE IF NOT EXISTS requisitos_publicacion (
  id_requisito INT AUTO_INCREMENT PRIMARY KEY,
  id_tipo_propiedad INT NULL,
  nombre VARCHAR(120) NOT NULL,
  obligatorio TINYINT(1) NOT NULL DEFAULT 1,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  FOREIGN KEY (id_tipo_propiedad) REFERENCES tipos_propiedad(id_tipo_propiedad),
  UNIQUE KEY uq_requisito_tipo_nombre (id_tipo_propiedad, nombre)
);

CREATE TABLE IF NOT EXISTS checklist_publicacion (
  id_propiedad INT NOT NULL,
  id_requisito INT NOT NULL,
  id_documento INT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'Pendiente',
  observaciones VARCHAR(500) NULL,
  id_revisor INT NULL,
  fecha_revision DATETIME NULL,
  PRIMARY KEY (id_propiedad, id_requisito),
  FOREIGN KEY (id_propiedad) REFERENCES propiedades(id_propiedad) ON DELETE CASCADE,
  FOREIGN KEY (id_requisito) REFERENCES requisitos_publicacion(id_requisito),
  FOREIGN KEY (id_documento) REFERENCES documentos(id_documento),
  FOREIGN KEY (id_revisor) REFERENCES usuarios(id_usuario)
);

CREATE TABLE IF NOT EXISTS configuracion_alertas (
  clave VARCHAR(80) PRIMARY KEY,
  valor VARCHAR(255) NOT NULL,
  descripcion VARCHAR(255) NULL,
  actualizado_por INT NULL,
  fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (actualizado_por) REFERENCES usuarios(id_usuario)
);

CREATE TABLE IF NOT EXISTS auditoria_cambios (
  id_cambio INT AUTO_INCREMENT PRIMARY KEY,
  id_evento INT NULL,
  entidad VARCHAR(40) NOT NULL,
  id_registro INT NOT NULL,
  campo VARCHAR(80) NOT NULL,
  valor_anterior TEXT NULL,
  valor_nuevo TEXT NULL,
  motivo VARCHAR(500) NULL,
  FOREIGN KEY (id_evento) REFERENCES auditoria(id_evento),
  INDEX idx_cambio_entidad (entidad, id_registro, id_cambio)
);

INSERT IGNORE INTO configuracion_alertas (clave, valor, descripcion) VALUES
  ('dias_cliente_sin_contacto', '14', 'Días sin contacto para alertar al agente.'),
  ('dias_propiedad_sin_actividad', '30', 'Días sin actividad para alertar al responsable.'),
  ('dias_aviso_reserva', '3', 'Días antes del vencimiento para avisar al equipo.');

INSERT INTO requisitos_publicacion (id_tipo_propiedad, nombre, obligatorio)
SELECT NULL, r.nombre, 1
FROM (
  SELECT 'Autorización de comercialización' AS nombre
  UNION ALL SELECT 'Documento que acredita titularidad'
  UNION ALL SELECT 'Fotografías principales'
) AS r
WHERE NOT EXISTS (
  SELECT 1 FROM requisitos_publicacion existente
  WHERE existente.id_tipo_propiedad IS NULL AND existente.nombre = r.nombre
);

-- Las solicitudes que ya estaban pendientes también deben pasar por checklist.
INSERT INTO checklist_publicacion (id_propiedad, id_requisito)
SELECT p.id_propiedad, r.id_requisito
FROM propiedades p
JOIN requisitos_publicacion r ON r.activo = 1
  AND (r.id_tipo_propiedad IS NULL OR r.id_tipo_propiedad = p.id_tipo_propiedad)
WHERE p.id_estado_verificacion = 2
  AND NOT EXISTS (
    SELECT 1 FROM checklist_publicacion cp
    WHERE cp.id_propiedad = p.id_propiedad AND cp.id_requisito = r.id_requisito
  );

INSERT IGNORE INTO schema_version (version) VALUES (3);
