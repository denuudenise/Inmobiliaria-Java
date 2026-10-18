-- Datos ficticios para practicar la aplicación en una base ya migrada.
-- Ejecutar en la base inmobiliaria DESPUÉS de 02_migracion.sql.
-- Puede volver a ejecutarse: no duplica estos usuarios, propiedades ni relaciones.
-- Clave de las cuentas de muestra: DemoHogar2026!

START TRANSACTION;

SET @demo_password = 'pbkdf2$600000$TnVldm9Ib2dhcjIwMjYhIQ==$dy7tU4zvIYEHhubI3NscwgwcE4poF2PJgYCXVkweQIw=';

INSERT INTO usuarios
    (nombre, apellido, dni, telefono, email, password_hash, id_rol, activo,
     direccion, localidad, tipo_documento)
SELECT 'Admin', 'Sistema', '99000000', '11 5555 1000',
       'admin.demo@nuevohogar.local', @demo_password, 1, 1,
       'Av. Maipú 1000', 'Vicente López', 'DNI'
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios WHERE email = 'admin.demo@nuevohogar.local'
);

INSERT INTO usuarios
    (nombre, apellido, dni, telefono, email, password_hash, id_rol, activo,
     direccion, localidad, tipo_documento)
SELECT 'Lucía', 'Fernández', '99000001', '11 5555 1001',
       'agente.demo@nuevohogar.local', @demo_password, 2, 1,
       'Av. Maipú 1200', 'Vicente López', 'DNI'
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios WHERE email = 'agente.demo@nuevohogar.local'
);

INSERT INTO usuarios
    (nombre, apellido, dni, telefono, email, password_hash, id_rol, activo,
     direccion, localidad, tipo_documento)
SELECT 'Juan', 'Pérez', '99000002', '11 5555 1002',
       'cliente.demo@nuevohogar.local', @demo_password, 3, 1,
       'Belgrano 450', 'San Isidro', 'DNI'
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios WHERE email = 'cliente.demo@nuevohogar.local'
);

INSERT INTO usuarios
    (nombre, apellido, dni, telefono, email, password_hash, id_rol, activo,
     direccion, localidad, tipo_documento)
SELECT 'María', 'López', '99000003', '11 5555 1003',
       'cliente2.demo@nuevohogar.local', @demo_password, 3, 1,
       'Las Heras 820', 'Palermo, CABA', 'DNI'
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios WHERE email = 'cliente2.demo@nuevohogar.local'
);

SET @agent_id = (SELECT id_usuario FROM usuarios WHERE email = 'agente.demo@nuevohogar.local' LIMIT 1);
SET @client_id = (SELECT id_usuario FROM usuarios WHERE email = 'cliente.demo@nuevohogar.local' LIMIT 1);
SET @client2_id = (SELECT id_usuario FROM usuarios WHERE email = 'cliente2.demo@nuevohogar.local' LIMIT 1);
SET @admin_id = (SELECT id_usuario FROM usuarios WHERE id_rol = 1 ORDER BY id_usuario LIMIT 1);

INSERT INTO clientes (id_cliente, fecha_inicio_relacion, id_agente_asignado)
SELECT @client_id, CURDATE(), @agent_id
WHERE @client_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM clientes WHERE id_cliente = @client_id);

INSERT INTO clientes (id_cliente, fecha_inicio_relacion, id_agente_asignado)
SELECT @client2_id, CURDATE(), @agent_id
WHERE @client2_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM clientes WHERE id_cliente = @client2_id);

UPDATE clientes SET id_agente_asignado = @agent_id WHERE id_cliente IN (@client_id, @client2_id);

INSERT IGNORE INTO roles_cliente (id_cliente, tipo_rol) VALUES
    (@client_id, 'Propietario'),
    (@client_id, 'Comprador'),
    (@client2_id, 'Comprador');

INSERT INTO propiedades
    (id_tipo_propiedad, id_tipo_transaccion, titulo, direccion, localidad,
     precio, moneda, descripcion, superficie_total_m2, superficie_cubierta_m2,
     antiguedad_anos, ambientes, dormitorios, banos, expensas, apto_profesional,
     id_agente_creador, id_admin_verificador, id_estado_verificacion,
     id_estado_comercial, id_propietario, capacidad)
SELECT 1, 1, 'Casa con jardín en San Isidro', 'Belgrano 450', 'San Isidro',
       250000, 'USD', 'Casa luminosa con jardín, living comedor amplio y cochera.',
       200, 145, 8, 4, 3, 2, 0, 0,
       @agent_id, @admin_id, 3, 1, @client_id, 5
WHERE @agent_id IS NOT NULL AND @client_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM propiedades WHERE titulo = 'Casa con jardín en San Isidro');

INSERT INTO propiedades
    (id_tipo_propiedad, id_tipo_transaccion, titulo, direccion, localidad,
     precio, moneda, descripcion, superficie_total_m2, superficie_cubierta_m2,
     antiguedad_anos, ambientes, dormitorios, banos, expensas, apto_profesional,
     id_agente_creador, id_admin_verificador, id_estado_verificacion,
     id_estado_comercial, id_propietario, capacidad)
SELECT 2, 1, 'Departamento luminoso en Palermo', 'Av. Santa Fe 3200', 'Palermo, CABA',
       180000, 'USD', 'Departamento de dos ambientes con balcón y buena luz natural.',
       85, 76, 5, 2, 1, 1, 45000, 0,
       @agent_id, @admin_id, 3, 1, @client2_id, 2
WHERE @agent_id IS NOT NULL AND @client2_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM propiedades WHERE titulo = 'Departamento luminoso en Palermo');

INSERT INTO propiedades
    (id_tipo_propiedad, id_tipo_transaccion, titulo, direccion, localidad,
     precio, moneda, descripcion, superficie_total_m2, superficie_cubierta_m2,
     antiguedad_anos, ambientes, dormitorios, banos, expensas, apto_profesional,
     id_agente_creador, id_admin_verificador, id_estado_verificacion,
     id_estado_comercial, id_propietario, capacidad)
SELECT 1, 2, 'Casa con pileta en Tigre', 'Los Ceibos 740', 'Tigre',
       1200, 'USD', 'Casa amueblada con pileta, galería y espacio verde.',
       300, 170, 10, 5, 3, 2, 0, 0,
       @agent_id, @admin_id, 3, 1, @client_id, 6
WHERE @agent_id IS NOT NULL AND @client_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM propiedades WHERE titulo = 'Casa con pileta en Tigre');

SET @sale_house = (SELECT id_propiedad FROM propiedades WHERE titulo = 'Casa con jardín en San Isidro' LIMIT 1);
SET @sale_apartment = (SELECT id_propiedad FROM propiedades WHERE titulo = 'Departamento luminoso en Palermo' LIMIT 1);
SET @rental_house = (SELECT id_propiedad FROM propiedades WHERE titulo = 'Casa con pileta en Tigre' LIMIT 1);

INSERT IGNORE INTO propiedad_ofertas (id_propiedad, id_tipo_transaccion, precio, moneda) VALUES
    (@sale_house, 1, 250000, 'USD'),
    (@sale_apartment, 1, 180000, 'USD'),
    (@rental_house, 2, 1200, 'USD');

INSERT INTO publicaciones (id_propiedad, id_solicitante, id_revisor, estado, observaciones, fecha_revision)
SELECT p.id_propiedad, @agent_id, @admin_id, 'Publicada', 'Registro de demostración aprobado', CURRENT_TIMESTAMP
FROM propiedades p
WHERE p.id_propiedad IN (@sale_house, @sale_apartment, @rental_house)
  AND NOT EXISTS (
      SELECT 1 FROM publicaciones s
      WHERE s.id_propiedad = p.id_propiedad AND s.estado = 'Publicada'
  );

INSERT INTO intereses (id_cliente, id_propiedad, estado, acciones)
SELECT @client2_id, @sale_house, 'Interesado', 'Interés de ejemplo para probar el seguimiento.'
WHERE @client2_id IS NOT NULL AND @sale_house IS NOT NULL
ON DUPLICATE KEY UPDATE estado = VALUES(estado), acciones = VALUES(acciones);

COMMIT;

SELECT email, nombre, apellido, id_rol
FROM usuarios
WHERE email IN (
    'admin.demo@nuevohogar.local',
    'agente.demo@nuevohogar.local',
    'cliente.demo@nuevohogar.local',
    'cliente2.demo@nuevohogar.local'
)
ORDER BY id_rol, email;
