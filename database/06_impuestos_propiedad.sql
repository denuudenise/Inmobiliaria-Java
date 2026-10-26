-- Cálculos manuales por propiedad. No modifica credenciales ni registros existentes.
CREATE TABLE IF NOT EXISTS impuestos_propiedad (
 id_impuesto INT AUTO_INCREMENT PRIMARY KEY,
 id_propiedad INT NOT NULL,
 nombre VARCHAR(120) NOT NULL,
 base_imponible DECIMAL(12,2) NOT NULL,
 porcentaje DECIMAL(7,4) NOT NULL,
 importe DECIMAL(12,2) NOT NULL,
 moneda VARCHAR(3) NOT NULL,
 periodo VARCHAR(80) NOT NULL,
 id_usuario INT NOT NULL,
 fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_propiedad) REFERENCES propiedades(id_propiedad),
 FOREIGN KEY(id_usuario) REFERENCES usuarios(id_usuario),
 CHECK(base_imponible >= 0), CHECK(porcentaje >= 0 AND porcentaje <= 100), CHECK(importe >= 0)
);
