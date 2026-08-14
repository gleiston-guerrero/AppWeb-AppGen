package org.slcp.service.generation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.AiCredential;
import org.slcp.service.domain.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las credenciales, una por proveedor y proyecto. */
public interface AiCredentialRepository extends JpaRepository<AiCredential, AiCredential.Clave> {

	Optional<AiCredential> findByProjectIdAndProvider(UUID projectId, AiProvider provider);

	/** Todas las del proyecto: varias conviven, y eso permite compararlas. */
	List<AiCredential> findByProjectIdOrderByProviderAsc(UUID projectId);
}
