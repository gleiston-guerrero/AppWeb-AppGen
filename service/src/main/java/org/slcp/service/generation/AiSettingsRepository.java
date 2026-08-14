package org.slcp.service.generation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.AiFeature;
import org.slcp.service.domain.AiSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la configuracion de IA de cada proyecto. */
public interface AiSettingsRepository extends JpaRepository<AiSettings, AiSettings.Clave> {

	/** La configuracion de una funcion concreta. */
	Optional<AiSettings> findByProjectIdAndFeature(UUID projectId, AiFeature feature);

	/** Todas las del proyecto, para la pantalla de configuracion. */
	List<AiSettings> findByProjectIdOrderByFeatureAsc(UUID projectId);
}
