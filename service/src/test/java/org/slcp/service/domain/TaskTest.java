package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de la tarea. */
class TaskTest {

	private static final Instant T0 = Instant.parse("2026-08-12T10:00:00Z");
	private static final UUID PROYECTO = UUID.randomUUID();
	private static final UUID COMPONENTE = UUID.randomUUID();
	private static final UUID QUIEN = UUID.randomUUID();

	private Task nueva(int esfuerzo) {
		return Task.crear(PROYECTO, COMPONENTE, "TAR-0001", "Implementar el modelo",
				"Entidad y repositorio", esfuerzo, null, QUIEN, T0);
	}

	@Test
	@DisplayName("Una tarea nace pendiente y sin terminar")
	void nacePendiente() {
		Task t = nueva(5);

		assertThat(t.getStatus()).isEqualTo(TaskStatus.PENDING);
		assertThat(t.getDoneBy()).isNull();
		assertThat(t.getPlannedEffort()).isEqualTo(5);
	}

	@Test
	@DisplayName("PRG-07: el esfuerzo previsto es obligatorio y mayor que cero")
	void esfuerzoObligatorio() {
		// Sin el, el avance del componente se promediaria sin peso y terminar lo
		// trivial dejando lo dificil daria un avance enganoso.
		assertThatThrownBy(() -> nueva(0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("mayor que cero");

		assertThatThrownBy(() -> nueva(-3)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("PRG-08: terminarla deja constancia de quien y cuando")
	void terminarConAutor() {
		Task t = nueva(5);
		t.transitarA(TaskStatus.IN_PROGRESS, QUIEN, T0);
		t.transitarA(TaskStatus.DONE, QUIEN, T0);

		assertThat(t.getDoneBy()).isEqualTo(QUIEN);
		assertThat(t.getDoneAt()).isEqualTo(T0);
	}

	@Test
	@DisplayName("Reabrirla borra la constancia de terminada")
	void reabrirOlvidaLaFirma() {
		Task t = nueva(5);
		t.transitarA(TaskStatus.DONE, QUIEN, T0);
		t.transitarA(TaskStatus.IN_PROGRESS, QUIEN, T0);

		// Mantener el dato diria que alguien la dio por hecha y sigue sin estarlo.
		assertThat(t.getDoneBy()).isNull();
		assertThat(t.getDoneAt()).isNull();
	}

	@Test
	@DisplayName("Lo bloqueado no salta directamente a terminado")
	void bloqueadoNoTermina() {
		assertThat(TaskStatus.BLOCKED.puedeTransitarA(TaskStatus.DONE)).isFalse();
		assertThat(TaskStatus.BLOCKED.puedeTransitarA(TaskStatus.IN_PROGRESS)).isTrue();
	}

	@Test
	@DisplayName("El nombre se normaliza al crearla")
	void nombreNormalizado() {
		Task t = Task.crear(PROYECTO, COMPONENTE, "TAR-0002", "IMPLEMENTAR EL MODELO",
				null, 3, null, QUIEN, T0);

		assertThat(t.getName()).isEqualTo("Implementar el modelo");
	}

	@Test
	@DisplayName("Editar admite cambiar el esfuerzo, pero no anularlo")
	void editarEsfuerzo() {
		Task t = nueva(5);
		t.editar(null, null, 8, null, T0);
		assertThat(t.getPlannedEffort()).isEqualTo(8);

		assertThatThrownBy(() -> t.editar(null, null, 0, null, T0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
