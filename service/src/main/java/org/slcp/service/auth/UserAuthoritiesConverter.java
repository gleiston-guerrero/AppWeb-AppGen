package org.slcp.service.auth;

import java.util.List;
import java.util.UUID;
import org.slcp.service.domain.User;
import org.slcp.service.registration.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resuelve las atribuciones de quien presenta el token.
 *
 * <p>Realiza SEC-04: el token no lleva roles dentro. Se consultan aqui, en cada
 * peticion, de modo que retirar el rol a alguien surte efecto de inmediato en
 * lugar de esperar a que caduque su token.</p>
 *
 * <p>El coste es una consulta por peticion autenticada. Es asumible mientras el
 * despliegue sea de una sola instancia, y si llegara a pesar se resuelve con una
 * cache de vida muy corta, no incrustando el rol en el token.</p>
 */
@Component
public class UserAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserRepository users;

	public UserAuthoritiesConverter(UserRepository users) {
		this.users = users;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		List<GrantedAuthority> atribuciones = users.findById(UUID.fromString(jwt.getSubject()))
				.filter(User::puedeIniciarSesion)
				.map(u -> List.<GrantedAuthority>of(new SimpleGrantedAuthority(u.getPlatformRole().authority())))
				.orElse(List.of());

		return new JwtAuthenticationToken(jwt, atribuciones);
	}
}
