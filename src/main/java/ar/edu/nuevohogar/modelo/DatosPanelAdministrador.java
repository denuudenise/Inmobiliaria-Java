package ar.edu.nuevohogar.modelo;

import java.util.List;
import java.util.Map;

/** Datos de resumen que consume el tablero del administrador. */
public record DatosPanelAdministrador(
        long clients,
        long agents,
        long totalUsers,
        long totalProperties,
        long publishedProperties,
        long pendingProperties,
        long rejectedProperties,
        long soldProperties,
        long rentedProperties,
        long pendingRequests,
        long closedOperations,
        Map<String, Long> propertyStates,
        Map<String, Long> propertyTypes,
        Map<String, Long> requestStates,
        List<Registro> latestRequests,
        List<Registro> latestProperties,
        List<Registro> agentWorkload,
        List<Registro> recentActivity) {}
