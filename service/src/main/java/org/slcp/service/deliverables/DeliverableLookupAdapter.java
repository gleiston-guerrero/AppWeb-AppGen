package org.slcp.service.deliverables;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slcp.service.domain.Deliverable;
import org.slcp.service.domain.DeliverableStatus;
import org.slcp.service.requirements.DeliverableLookup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Realiza para los requisitos la consulta que necesitan sobre entregables.
 *
 * <p>Existe para no cerrar un ciclo: el servicio de entregables ya depende del
 * de requisitos, y hacer que este dependiera de aquel dejaria dos componentes
 * que no pueden construirse el uno sin el otro.</p>
 */
@Component
public class DeliverableLookupAdapter implements DeliverableLookup {

	private final DeliverableRepository deliverables;

	public DeliverableLookupAdapter(DeliverableRepository deliverables) {
		this.deliverables = deliverables;
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> aceptadosDe(UUID requirementId) {
		return deliverables.entregablesDe(requirementId).stream()
				.map(deliverables::findById)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(d -> d.getStatus() == DeliverableStatus.ACCEPTED)
				.map(Deliverable::getReadableId)
				.toList();
	}
}
