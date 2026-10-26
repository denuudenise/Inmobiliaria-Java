-- Ejecutar SOLO después de copiar inmueble-01.png ... inmueble-30.png a
-- data/archivos/demo/ (o a la carpeta indicada por storage.path en config.properties).
-- Importar después de datos_demostracion_30_inmuebles.sql.
USE inmobiliaria;

INSERT INTO propiedad_multimedia (id_propiedad,url_archivo,tipo,orden)
SELECT p.id_propiedad,CONCAT('demo/inmueble-',LPAD(d.n,2,'0'),'.png'),'Imagen',1
FROM (
  SELECT 1 n,'Casa de dos plantas con jardín en Martínez' titulo UNION ALL
  SELECT 2,'Departamento con balcón y vista abierta en Palermo' UNION ALL
  SELECT 3,'PH reciclado con patio en Villa Urquiza' UNION ALL
  SELECT 4,'Casa con pileta y quincho en Tigre' UNION ALL
  SELECT 5,'Departamento moderno cerca del río en Olivos' UNION ALL
  SELECT 6,'Terreno arbolado en barrio abierto de Escobar' UNION ALL
  SELECT 7,'Casa de estilo en San Isidro con terraza' UNION ALL
  SELECT 8,'Departamento de tres ambientes en Caballito' UNION ALL
  SELECT 9,'Local comercial con vidriera en Belgrano' UNION ALL
  SELECT 10,'Casa con parque en San Fernando' UNION ALL
  SELECT 11,'Oficina lista para trabajar en Microcentro' UNION ALL
  SELECT 12,'Casa tipo chalet en Beccar con cochera' UNION ALL
  SELECT 13,'Departamento con amenities en Vicente López' UNION ALL
  SELECT 14,'Cochera cubierta en Palermo Soho' UNION ALL
  SELECT 15,'Casa quinta con pileta en Luján' UNION ALL
  SELECT 16,'Casa amueblada con pileta en alquiler en Tigre' UNION ALL
  SELECT 17,'Departamento con balcón en alquiler en Palermo' UNION ALL
  SELECT 18,'Casa familiar en alquiler en San Fernando' UNION ALL
  SELECT 19,'Departamento amoblado temporal en Recoleta' UNION ALL
  SELECT 20,'Casa en alquiler con jardín en Martínez' UNION ALL
  SELECT 21,'Departamento amplio en alquiler en Belgrano' UNION ALL
  SELECT 22,'Casa con parrilla en alquiler en Olivos' UNION ALL
  SELECT 23,'Oficina en alquiler cerca de Plaza San Martín' UNION ALL
  SELECT 24,'Casa de fin de semana en alquiler en Escobar' UNION ALL
  SELECT 25,'Local luminoso en alquiler en San Isidro' UNION ALL
  SELECT 26,'Departamento en alquiler con vista al río' UNION ALL
  SELECT 27,'Terreno en alquiler para uso comercial en La Plata' UNION ALL
  SELECT 28,'Departamento temporal cerca del mar en Mar del Tuyú' UNION ALL
  SELECT 29,'Casa en alquiler con patio en Villa Devoto' UNION ALL
  SELECT 30,'Departamento a estrenar con balcón en Núñez'
) d
JOIN propiedades p ON p.titulo=d.titulo
WHERE NOT EXISTS (
  SELECT 1 FROM propiedad_multimedia m
  WHERE m.id_propiedad=p.id_propiedad
    AND m.url_archivo=CONCAT('demo/inmueble-',LPAD(d.n,2,'0'),'.png')
);
