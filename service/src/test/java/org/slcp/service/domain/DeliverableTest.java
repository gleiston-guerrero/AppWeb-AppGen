package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo del entregable. */
class DeliverableTest {

	private static final Instant T0 = Instant.parse("2026-08-12T10:00:00Z");
	private static final UUID PROYECTO = UUID.randomUUID();
	private static final UUID QUIEN = UUID.randomUUID();

	private Deliverable nuevo() {
		return Deliverable.crear(PROYECTO, "ENT-0001-v1", "Modulo de riego",
				"Gobierna las valvulas.", "Riega una parcela y se comprueba el volumen.", QUIEN, T0);
	}

	@Test
	@DisplayName("Un entregable nace planificado y eliminable")
	void naceePlanificado() {
		Deliverable d = nuevo();

		assertThat(d.getStatus()).isEqualTo(DeliverableStatus.PLANNED);
		assertThat(d.puedeEliminarse()).isTrue();
		assertThat(d.getReadableId()).isEqualTo("ENT-0001-v1");
	}

	@Test
	@DisplayName("Aceptar deja constancia de quien y cuando")
	void aceptacionConAutor() {
		Deliverable d = nuevo();
		d.transitarA(DeliverableStatus.DELIVERED, QUIEN, T0);
		d.transitarA(DeliverableStatus.ACCEPTED, QUIEN, T0);

		assertThat(d.getAcceptedBy()).isEqualTo(QUIEN);
		assertThat(d.getAcceptedAt()).isEqualTo(T0);
	}

	@Test
	@DisplayName("WBS-08: lo entregado o aceptado no se elimina")
	void entregadoNoSeElimina() {
		Deliverable d = nuevo();
		d.transitarA(DeliverableStatus.DELIVERED, QUIEN, T0);

		assertThat(d.puedeEliminarse()).isFalse();
	}

	@Test
	@DisplayName("Modificar devuelve el entregable a su estado inicial")
	void modificarDevuelveAlInicio() {
		Deliverable d = nuevo();
		d.transitarA(DeliverableStatus.IN_PROGRESS, QUIEN, T0);
		d.editar("Otro nombre", "Otra cosa", "Otro criterio", T0);

		assertThat(d.getStatus()).isEqualTo(DeliverableStatus.PLANNED);
		assertThat(d.getName()).isEqualTo("Otro nombre");
		assertThat(d.getVersion()).isEqualTo(2);
	}

	@Test
	@DisplayName("Un entregable aceptado no se modifica")
	void aceptadoNoSeModifica() {
		Deliverable d = nuevo();
		d.transitarA(DeliverableStatus.DELIVERED, QUIEN, T0);
		d.transitarA(DeliverableStatus.ACCEPTED, QUIEN, T0);

		assertThatThrownBy(() -> d.editar("Otro", null, null, T0))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("peticion de cambio");
	}

	@Test
	@DisplayName("La aceptacion es terminal: nada sale de ella")
	void aceptacionTerminal() {
		for (DeliverableStatus destino : DeliverableStatus.values()) {
			assertThat(DeliverableStatus.ACCEPTED.puedeTransitarA(destino)).isFalse();
		}
	}

	@Test
	@DisplayName("Solo se acepta o se devuelve lo que se entrego")
	void soloSeAceptaLoEntregado() {
		assertThat(DeliverableStatus.PLANNED.puedeTransitarA(DeliverableStatus.ACCEPTED)).isFalse();
		assertThat(DeliverableStatus.IN_PROGRESS.puedeTransitarA(DeliverableStatus.ACCEPTED)).isFalse();
		assertThat(DeliverableStatus.DELIVERED.puedeTransitarA(DeliverableStatus.ACCEPTED)).isTrue();
	}

	@Test
	@DisplayName("Lo devuelto puede volver al trabajo")
	void devueltoVuelveAlTrabajo() {
		assertThat(DeliverableStatus.REJECTED.puedeTransitarA(DeliverableStatus.IN_PROGRESS)).isTrue();
	}
}
