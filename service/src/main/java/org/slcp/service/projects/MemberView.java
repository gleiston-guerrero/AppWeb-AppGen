package org.slcp.service.projects;

/** Integrante del equipo de un proyecto. */
public record MemberView(
		String username,
		String fullName,
		String email,
		String role,
		String roleLabel,
		String status) {
}
