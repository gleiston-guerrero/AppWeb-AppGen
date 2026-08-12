package org.slcp.service.requirements;

import java.util.List;
import java.util.UUID;

/**
 * Consulta minima sobre entregables que necesitan los requisitos.
 *
 * <p>Se declara aqui, en el lado que la usa, y no se depende del servicio de
 * entregables completo: este ya depende de los requisitos, y hacerlo al reves
 * cerraria un ciclo entre ambos.</p>
 */
public interface DeliverableLookup {

	/** Identificadores legibles de los entregables aceptados que realizan el requisito. */
	List<String> aceptadosDe(UUID requirementId);
}
