package org.slcp.service.registration;

import java.util.List;
import java.util.UUID;
import org.slcp.service.domain.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al almacen de solo anexado.
 *
 * <p>La interfaz no expone ninguna operacion de borrado ni de actualizacion,
 * conforme a TRC-24. Las heredadas de {@code JpaRepository} que si lo harian
 * quedan prohibidas por la prueba negativa correspondiente.</p>
 */
public interface EventRecordRepository extends JpaRepository<EventRecord, UUID> {

	List<EventRecord> findBySubjectIdOrderByOccurredAtAsc(UUID subjectId);
}
