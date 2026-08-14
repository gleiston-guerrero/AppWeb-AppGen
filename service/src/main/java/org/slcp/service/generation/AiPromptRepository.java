package org.slcp.service.generation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.AiFeature;
import org.slcp.service.domain.AiPrompt;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las instrucciones propias de cada proyecto. */
public interface AiPromptRepository extends JpaRepository<AiPrompt, AiPrompt.Clave> {

	Optional<AiPrompt> findByProjectIdAndFeature(UUID projectId, AiFeature feature);

	List<AiPrompt> findByProjectIdOrderByFeatureAsc(UUID projectId);
}
