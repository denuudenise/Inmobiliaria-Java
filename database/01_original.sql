-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Servidor: 127.0.0.1
-- Tiempo de generación: 04-10-2026 a las 03:57:08
-- Versión del servidor: 10.4.32-MariaDB
-- Versión de PHP: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de datos: `inmobiliaria`
--

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `caracteristicas`
--

CREATE TABLE `caracteristicas` (
  `id_caracteristica` int(11) NOT NULL,
  `nombre` varchar(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Volcado de datos para la tabla `caracteristicas`
--

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

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `estados_comerciales`
--

CREATE TABLE `estados_comerciales` (
  `id_estado_comercial` int(11) NOT NULL,
  `nombre` varchar(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Volcado de datos para la tabla `estados_comerciales`
--

INSERT INTO `estados_comerciales` (`id_estado_comercial`, `nombre`) VALUES
(4, 'Alquilada'),
(1, 'Disponible'),
(5, 'En trámite'),
(6, 'Fuera de mercado'),
(2, 'Reservada'),
(3, 'Vendida');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `estados_verificacion`
--

CREATE TABLE `estados_verificacion` (
  `id_estado_verificacion` int(11) NOT NULL,
  `nombre` varchar(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Volcado de datos para la tabla `estados_verificacion`
--

INSERT INTO `estados_verificacion` (`id_estado_verificacion`, `nombre`) VALUES
(3, 'Aprobado'),
(1, 'Borrador'),
(5, 'Inactivo'),
(2, 'Pendiente de Verificación'),
(4, 'Rechazado');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `preferencias_cliente`
--

CREATE TABLE `preferencias_cliente` (
  `id_preferencia` int(11) NOT NULL,
  `id_cliente` int(11) NOT NULL,
  `id_tipo_transaccion` int(11) NOT NULL,
  `id_tipo_propiedad` int(11) DEFAULT NULL,
  `presupuesto_maximo` decimal(12,2) DEFAULT NULL,
  `zona_interes` varchar(100) DEFAULT NULL,
  `ambientes_minimos` int(11) DEFAULT NULL,
  `dormitorios_minimos` int(11) DEFAULT NULL,
  `superficie_minima_m2` decimal(8,2) DEFAULT NULL,
  `fecha_estimada_concretar` date DEFAULT NULL,
  `requiere_venta_previa` tinyint(1) DEFAULT 0,
  `fecha_creacion` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `propiedades`
--

CREATE TABLE `propiedades` (
  `id_propiedad` int(11) NOT NULL,
  `id_tipo_propiedad` int(11) NOT NULL,
  `id_tipo_transaccion` int(11) NOT NULL,
  `titulo` varchar(200) NOT NULL,
  `direccion` varchar(255) NOT NULL,
  `localidad` varchar(100) NOT NULL,
  `precio` decimal(12,2) NOT NULL,
  `moneda` enum('USD','ARS') DEFAULT 'USD',
  `descripcion` text DEFAULT NULL,
  `superficie_total_m2` decimal(8,2) NOT NULL,
  `superficie_cubierta_m2` decimal(8,2) DEFAULT NULL,
  `antiguedad_anos` int(11) DEFAULT 0,
  `ambientes` int(11) DEFAULT NULL,
  `dormitorios` int(11) DEFAULT NULL,
  `banos` int(11) DEFAULT NULL,
  `piso` varchar(10) DEFAULT NULL,
  `departamento_numero` varchar(10) DEFAULT NULL,
  `expensas` decimal(10,2) DEFAULT NULL,
  `apto_profesional` tinyint(1) DEFAULT 0,
  `id_agente_creador` int(11) NOT NULL,
  `id_admin_verificador` int(11) DEFAULT NULL,
  `id_estado_verificacion` int(11) NOT NULL DEFAULT 1,
  `id_estado_comercial` int(11) NOT NULL DEFAULT 1,
  `fecha_creacion` datetime DEFAULT current_timestamp(),
  `fecha_verificacion` datetime DEFAULT NULL,
  `motivo_rechazo` text DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `propiedades_cerradas`
--

CREATE TABLE `propiedades_cerradas` (
  `id_operacion` int(11) NOT NULL,
  `id_propiedad` int(11) NOT NULL,
  `id_cliente_comprador` int(11) NOT NULL,
  `id_agente_cierre` int(11) NOT NULL,
  `precio_final` decimal(12,2) NOT NULL,
  `comision_total` decimal(10,2) DEFAULT NULL,
  `fecha_cierre` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `propiedad_caracteristicas`
--

CREATE TABLE `propiedad_caracteristicas` (
  `id_propiedad` int(11) NOT NULL,
  `id_caracteristica` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `propiedad_multimedia`
--

CREATE TABLE `propiedad_multimedia` (
  `id_multimedia` int(11) NOT NULL,
  `id_propiedad` int(11) NOT NULL,
  `url_archivo` varchar(500) NOT NULL,
  `tipo` enum('Imagen','Video') DEFAULT 'Imagen',
  `orden` int(11) DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `roles`
--

CREATE TABLE `roles` (
  `id_rol` int(11) NOT NULL,
  `nombre` varchar(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Volcado de datos para la tabla `roles`
--

INSERT INTO `roles` (`id_rol`, `nombre`) VALUES
(1, 'Administrador'),
(2, 'Agente'),
(3, 'Cliente');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `tipos_propiedad`
--

CREATE TABLE `tipos_propiedad` (
  `id_tipo_propiedad` int(11) NOT NULL,
  `nombre` varchar(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Volcado de datos para la tabla `tipos_propiedad`
--

INSERT INTO `tipos_propiedad` (`id_tipo_propiedad`, `nombre`) VALUES
(1, 'Casa'),
(6, 'Cochera'),
(2, 'Departamento'),
(3, 'Local'),
(4, 'Oficina'),
(5, 'Terreno');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `tipos_transaccion`
--

CREATE TABLE `tipos_transaccion` (
  `id_tipo_transaccion` int(11) NOT NULL,
  `nombre` varchar(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Volcado de datos para la tabla `tipos_transaccion`
--

INSERT INTO `tipos_transaccion` (`id_tipo_transaccion`, `nombre`) VALUES
(2, 'Alquiler'),
(3, 'Alquiler Temporario'),
(1, 'Venta');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `usuarios`
--

CREATE TABLE `usuarios` (
  `id_usuario` int(11) NOT NULL,
  `nombre` varchar(100) NOT NULL,
  `apellido` varchar(100) NOT NULL,
  `dni` varchar(20) NOT NULL,
  `telefono` varchar(30) DEFAULT NULL,
  `email` varchar(150) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `id_rol` int(11) NOT NULL,
  `activo` tinyint(1) DEFAULT 1,
  `fecha_registro` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Índices para tablas volcadas
--

--
-- Indices de la tabla `caracteristicas`
--
ALTER TABLE `caracteristicas`
  ADD PRIMARY KEY (`id_caracteristica`),
  ADD UNIQUE KEY `nombre` (`nombre`);

--
-- Indices de la tabla `estados_comerciales`
--
ALTER TABLE `estados_comerciales`
  ADD PRIMARY KEY (`id_estado_comercial`),
  ADD UNIQUE KEY `nombre` (`nombre`);

--
-- Indices de la tabla `estados_verificacion`
--
ALTER TABLE `estados_verificacion`
  ADD PRIMARY KEY (`id_estado_verificacion`),
  ADD UNIQUE KEY `nombre` (`nombre`);

--
-- Indices de la tabla `preferencias_cliente`
--
ALTER TABLE `preferencias_cliente`
  ADD PRIMARY KEY (`id_preferencia`),
  ADD KEY `id_cliente` (`id_cliente`),
  ADD KEY `id_tipo_transaccion` (`id_tipo_transaccion`),
  ADD KEY `id_tipo_propiedad` (`id_tipo_propiedad`);

--
-- Indices de la tabla `propiedades`
--
ALTER TABLE `propiedades`
  ADD PRIMARY KEY (`id_propiedad`),
  ADD KEY `id_tipo_propiedad` (`id_tipo_propiedad`),
  ADD KEY `id_tipo_transaccion` (`id_tipo_transaccion`),
  ADD KEY `id_estado_verificacion` (`id_estado_verificacion`),
  ADD KEY `id_estado_comercial` (`id_estado_comercial`),
  ADD KEY `id_agente_creador` (`id_agente_creador`),
  ADD KEY `id_admin_verificador` (`id_admin_verificador`);

--
-- Indices de la tabla `propiedades_cerradas`
--
ALTER TABLE `propiedades_cerradas`
  ADD PRIMARY KEY (`id_operacion`),
  ADD UNIQUE KEY `id_propiedad` (`id_propiedad`),
  ADD KEY `id_cliente_comprador` (`id_cliente_comprador`),
  ADD KEY `id_agente_cierre` (`id_agente_cierre`);

--
-- Indices de la tabla `propiedad_caracteristicas`
--
ALTER TABLE `propiedad_caracteristicas`
  ADD PRIMARY KEY (`id_propiedad`,`id_caracteristica`),
  ADD KEY `id_caracteristica` (`id_caracteristica`);

--
-- Indices de la tabla `propiedad_multimedia`
--
ALTER TABLE `propiedad_multimedia`
  ADD PRIMARY KEY (`id_multimedia`),
  ADD KEY `id_propiedad` (`id_propiedad`);

--
-- Indices de la tabla `roles`
--
ALTER TABLE `roles`
  ADD PRIMARY KEY (`id_rol`),
  ADD UNIQUE KEY `nombre` (`nombre`);

--
-- Indices de la tabla `tipos_propiedad`
--
ALTER TABLE `tipos_propiedad`
  ADD PRIMARY KEY (`id_tipo_propiedad`),
  ADD UNIQUE KEY `nombre` (`nombre`);

--
-- Indices de la tabla `tipos_transaccion`
--
ALTER TABLE `tipos_transaccion`
  ADD PRIMARY KEY (`id_tipo_transaccion`),
  ADD UNIQUE KEY `nombre` (`nombre`);

--
-- Indices de la tabla `usuarios`
--
ALTER TABLE `usuarios`
  ADD PRIMARY KEY (`id_usuario`),
  ADD UNIQUE KEY `dni` (`dni`),
  ADD UNIQUE KEY `email` (`email`),
  ADD KEY `id_rol` (`id_rol`);

--
-- AUTO_INCREMENT de las tablas volcadas
--

--
-- AUTO_INCREMENT de la tabla `caracteristicas`
--
ALTER TABLE `caracteristicas`
  MODIFY `id_caracteristica` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=11;

--
-- AUTO_INCREMENT de la tabla `estados_comerciales`
--
ALTER TABLE `estados_comerciales`
  MODIFY `id_estado_comercial` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

--
-- AUTO_INCREMENT de la tabla `estados_verificacion`
--
ALTER TABLE `estados_verificacion`
  MODIFY `id_estado_verificacion` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

--
-- AUTO_INCREMENT de la tabla `preferencias_cliente`
--
ALTER TABLE `preferencias_cliente`
  MODIFY `id_preferencia` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `propiedades`
--
ALTER TABLE `propiedades`
  MODIFY `id_propiedad` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `propiedades_cerradas`
--
ALTER TABLE `propiedades_cerradas`
  MODIFY `id_operacion` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `propiedad_multimedia`
--
ALTER TABLE `propiedad_multimedia`
  MODIFY `id_multimedia` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `roles`
--
ALTER TABLE `roles`
  MODIFY `id_rol` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- AUTO_INCREMENT de la tabla `tipos_propiedad`
--
ALTER TABLE `tipos_propiedad`
  MODIFY `id_tipo_propiedad` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

--
-- AUTO_INCREMENT de la tabla `tipos_transaccion`
--
ALTER TABLE `tipos_transaccion`
  MODIFY `id_tipo_transaccion` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- AUTO_INCREMENT de la tabla `usuarios`
--
ALTER TABLE `usuarios`
  MODIFY `id_usuario` int(11) NOT NULL AUTO_INCREMENT;

--
-- Restricciones para tablas volcadas
--

--
-- Filtros para la tabla `preferencias_cliente`
--
ALTER TABLE `preferencias_cliente`
  ADD CONSTRAINT `preferencias_cliente_ibfk_1` FOREIGN KEY (`id_cliente`) REFERENCES `usuarios` (`id_usuario`) ON DELETE CASCADE,
  ADD CONSTRAINT `preferencias_cliente_ibfk_2` FOREIGN KEY (`id_tipo_transaccion`) REFERENCES `tipos_transaccion` (`id_tipo_transaccion`),
  ADD CONSTRAINT `preferencias_cliente_ibfk_3` FOREIGN KEY (`id_tipo_propiedad`) REFERENCES `tipos_propiedad` (`id_tipo_propiedad`);

--
-- Filtros para la tabla `propiedades`
--
ALTER TABLE `propiedades`
  ADD CONSTRAINT `propiedades_ibfk_1` FOREIGN KEY (`id_tipo_propiedad`) REFERENCES `tipos_propiedad` (`id_tipo_propiedad`),
  ADD CONSTRAINT `propiedades_ibfk_2` FOREIGN KEY (`id_tipo_transaccion`) REFERENCES `tipos_transaccion` (`id_tipo_transaccion`),
  ADD CONSTRAINT `propiedades_ibfk_3` FOREIGN KEY (`id_estado_verificacion`) REFERENCES `estados_verificacion` (`id_estado_verificacion`),
  ADD CONSTRAINT `propiedades_ibfk_4` FOREIGN KEY (`id_estado_comercial`) REFERENCES `estados_comerciales` (`id_estado_comercial`),
  ADD CONSTRAINT `propiedades_ibfk_5` FOREIGN KEY (`id_agente_creador`) REFERENCES `usuarios` (`id_usuario`),
  ADD CONSTRAINT `propiedades_ibfk_6` FOREIGN KEY (`id_admin_verificador`) REFERENCES `usuarios` (`id_usuario`);

--
-- Filtros para la tabla `propiedades_cerradas`
--
ALTER TABLE `propiedades_cerradas`
  ADD CONSTRAINT `propiedades_cerradas_ibfk_1` FOREIGN KEY (`id_propiedad`) REFERENCES `propiedades` (`id_propiedad`),
  ADD CONSTRAINT `propiedades_cerradas_ibfk_2` FOREIGN KEY (`id_cliente_comprador`) REFERENCES `usuarios` (`id_usuario`),
  ADD CONSTRAINT `propiedades_cerradas_ibfk_3` FOREIGN KEY (`id_agente_cierre`) REFERENCES `usuarios` (`id_usuario`);

--
-- Filtros para la tabla `propiedad_caracteristicas`
--
ALTER TABLE `propiedad_caracteristicas`
  ADD CONSTRAINT `propiedad_caracteristicas_ibfk_1` FOREIGN KEY (`id_propiedad`) REFERENCES `propiedades` (`id_propiedad`) ON DELETE CASCADE,
  ADD CONSTRAINT `propiedad_caracteristicas_ibfk_2` FOREIGN KEY (`id_caracteristica`) REFERENCES `caracteristicas` (`id_caracteristica`);

--
-- Filtros para la tabla `propiedad_multimedia`
--
ALTER TABLE `propiedad_multimedia`
  ADD CONSTRAINT `propiedad_multimedia_ibfk_1` FOREIGN KEY (`id_propiedad`) REFERENCES `propiedades` (`id_propiedad`) ON DELETE CASCADE;

--
-- Filtros para la tabla `usuarios`
--
ALTER TABLE `usuarios`
  ADD CONSTRAINT `usuarios_ibfk_1` FOREIGN KEY (`id_rol`) REFERENCES `roles` (`id_rol`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
