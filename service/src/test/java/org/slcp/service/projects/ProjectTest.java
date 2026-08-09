package org.slcp.service.projects;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slcp.service.domain.MembershipStatus;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectMembership;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.ProjectStatus;

/** Oraculo del proyecto y su membresia. */
class ProjectTest {

	private static final Instant MOMENTO = Instant.parse("2026-08-08T00:00:00Z");
	private static final UUID CREADOR = UUID.randomUUID();

	@Test
	@DisplayName("FUN-07: un proyecto nace activo y con identificador legible")
	void naceActivo() {
		Project p = Project.crear("MundiPets", "Gestion de mascotas", CREADOR, 7, MOMENTO);

		assertThat(p.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
		assertThat(p.getReadableId()).isEqualTo("PRJ-0007-v1");
		assertThat(p.estaActivo()).isTrue();
	}

	@Test
	@DisplayName("El proposito ausente no rompe: queda vacio, no nulo")
	void propositoOpcional() {
		Project p = Project.crear("MundiPets", null, CREADOR, 1, MOMENTO);

		assertThat(p.getPurpose()).isEmpty();
	}

	@Test
	@DisplayName("Una membresia nace activa con el rol que se le asigna")
	void membresiaActiva() {
		Project p = Project.crear("MundiPets", "", CREADOR, 1, MOMENTO);
		ProjectMembership m = ProjectMembership.activa(p.getId(), CREADOR,
				ProjectRole.PROJECT_FACILITATOR, MOMENTO);

		assertThat(m.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
		assertThat(m.getProjectRole()).isEqualTo(ProjectRole.PROJECT_FACILITATOR);
		assertThat(m.estaActiva()).isTrue();
	}

	@Test
	@DisplayName("ADM-01: retirar una membresia la desactiva, no la borra")
	void retiroSinBorrado() {
		ProjectMembership m = ProjectMembership.activa(UUID.randomUUID(), CREADOR,
				ProjectRole.TEAM_MEMBER, MOMENTO);

		m.retirar();

		assertThat(m.estaActiva()).isFalse();
		assertThat(m.getStatus()).isEqualTo(MembershipStatus.DECOMMISSIONED);
		assertThat(m.getProjectRole()).isEqualTo(ProjectRole.TEAM_MEMBER);
	}
}
