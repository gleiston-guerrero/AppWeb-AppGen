package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo del cambio de clase de un requisito. */
class RequirementKindChangeTest {

	private static final Instant T0 = Instant.parse("2026-08-12T10:00:00Z");

	private Requirement nuevo() {
		return Requirement.crear(UUID.randomUUID(), "REQ-0001-v1", "RF-03", 12,
				RequirementKind.FUNCTIONAL, "Panel",
				"El sistema debera mostrar el panel de la explotacion.",
				"Abrir el panel y comprobar que aparecen todas las parcelas.",
				UUID.randomUUID(), T0);
	}

	@Test
	@DisplayName("Cambiar de clase se guarda")
	void cambiaLaClase() {
		Requirement r = nuevo();
		r.editar(RequirementKind.NON_FUNCTIONAL, null, null, null,
				TextOrigin.HUMAN, TextOrigin.HUMAN, T0);

		assertThat(r.getKind()).isEqualTo(RequirementKind.NON_FUNCTIONAL);
	}

	@Test
	@DisplayName("La clase nula deja la que tenia: no toda edicion la cambia")
	void claseNulaConserva() {
		Requirement r = nuevo();
		r.editar(null, "Otro nombre", null, null, TextOrigin.HUMAN, TextOrigin.HUMAN, T0);

		assertThat(r.getKind()).isEqualTo(RequirementKind.FUNCTIONAL);
		assertThat(r.getName()).isEqualTo("Otro nombre");
	}

	@Test
	@DisplayName("El identificador de origen puede sustituirse al cambiar de clase")
	void identificadorSustituible() {
		Requirement r = nuevo();
		r.renombrarOrigen("RNF-03");

		assertThat(r.getSourceId()).isEqualTo("RNF-03");
	}

	@Test
	@DisplayName("Cambiar de clase devuelve el requisito a borrador")
	void vuelveABorrador() {
		Requirement r = nuevo();
		r.transitarA(RequirementStatus.REVIEWED, T0);
		r.editar(RequirementKind.NON_FUNCTIONAL, null, null, null,
				TextOrigin.HUMAN, TextOrigin.HUMAN, T0);

		assertThat(r.getStatus()).isEqualTo(RequirementStatus.DRAFT);
		assertThat(r.getReviewedBy()).isNull();
	}

	@Test
	@DisplayName("La clase se conjetura del prefijo del identificador de origen")
	void conjeturaPorPrefijo() {
		assertThat(RequirementKind.conjeturar("RNF-02")).isEqualTo(RequirementKind.NON_FUNCTIONAL);
		assertThat(RequirementKind.conjeturar("RF-02")).isEqualTo(RequirementKind.FUNCTIONAL);
	}
}
