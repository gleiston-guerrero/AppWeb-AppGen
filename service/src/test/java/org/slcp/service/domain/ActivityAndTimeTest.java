package org.slcp.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Oraculo de las actividades y de la dedicacion. */
class ActivityAndTimeTest {

	private static final Instant T0 = Instant.parse("2026-08-12T10:00:00Z");
	private static final UUID PROYECTO = UUID.randomUUID();
	private static final UUID TAREA = UUID.randomUUID();
	private static final UUID QUIEN = UUID.randomUUID();

	@Test
	@DisplayName("Una actividad nace pendiente")
	void nacePendiente() {
		Activity a = Activity.crear(PROYECTO, TAREA, "ACT-0001", "Estudiar el problema", 3, QUIEN, T0);

		assertThat(a.isDone()).isFalse();
		assertThat(a.getDoneAt()).isNull();
		assertThat(a.getPlannedEffort()).isEqualTo(3);
	}

	@Test
	@DisplayName("Darla por hecha guarda cuando, y devolverla lo olvida")
	void marcarYDesmarcar() {
		Activity a = Activity.crear(PROYECTO, TAREA, "ACT-0001", "Estudiar", 3, QUIEN, T0);

		a.marcar(true, T0);
		assertThat(a.isDone()).isTrue();
		assertThat(a.getDoneAt()).isEqualTo(T0);

		a.marcar(false, T0);
		assertThat(a.isDone()).isFalse();
		assertThat(a.getDoneAt()).isNull();
	}

	@Test
	@DisplayName("El esfuerzo de la actividad ha de ser mayor que cero")
	void esfuerzoPositivo() {
		// Contar actividades sin peso repetiria dentro de la tarea el error que
		// PRG-07 evita fuera: estudiar y escribir la prueba no valen lo mismo.
		assertThatThrownBy(() -> Activity.crear(PROYECTO, TAREA, "ACT-0001", "Algo", 0, QUIEN, T0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Las horas dedicadas han de ser mayores que cero")
	void horasPositivas() {
		assertThatThrownBy(() -> TimeEntry.de(PROYECTO, TAREA, QUIEN, BigDecimal.ZERO,
				LocalDate.of(2026, 8, 12), null, T0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("No caben mas de veinticuatro horas en un dia")
	void limiteDiario() {
		assertThatThrownBy(() -> TimeEntry.de(PROYECTO, TAREA, QUIEN, new BigDecimal("30"),
				LocalDate.of(2026, 8, 12), null, T0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("veinticuatro");
	}

	@Test
	@DisplayName("Sin fecha se toma la de hoy, no se rechaza el asiento")
	void fechaPorDefecto() {
		TimeEntry e = TimeEntry.de(PROYECTO, TAREA, QUIEN, new BigDecimal("2.5"), null, null, T0);

		assertThat(e.getWorkedOn()).isEqualTo(LocalDate.now());
		assertThat(e.getHours()).isEqualByComparingTo("2.5");
	}
}
