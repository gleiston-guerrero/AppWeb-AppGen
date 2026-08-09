package org.slcp.service.projects;

import java.time.Instant;
import java.util.List;

/**
 * Proyecto tal como lo ve quien consulta.
 *
 * <p>Incluye sus propios roles en el, que es lo que la interfaz necesita para
 * decidir que mostrar. La autorizacion sigue resolviendose en el servicio.</p>
 */
public record ProjectView(
		String readableId,
		String name,
		String purpose,
		String status,
		Instant createdAt,
		List<String> myRoles,
		int teamSize) {
}
