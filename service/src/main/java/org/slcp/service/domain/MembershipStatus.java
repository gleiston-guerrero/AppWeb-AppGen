package org.slcp.service.domain;

/** Estado de una membresia. */
public enum MembershipStatus {

	/** Invitada, a la espera de que la persona complete su registro. */
	INVITED,

	/** Operativa. */
	ACTIVE,

	/** Retirada del servicio conforme a ADM-01. */
	DECOMMISSIONED
}
