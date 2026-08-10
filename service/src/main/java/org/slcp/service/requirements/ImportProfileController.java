package org.slcp.service.requirements;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slcp.service.ingestion.ImportProfile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Formatos de archivo que la plataforma sabe leer.
 *
 * <p>La lista se deriva de los perfiles instalados, no se declara aparte:
 * anadir un perfil basta para que aparezca. Una lista mantenida a mano se
 * desincroniza en cuanto alguien anade un formato y olvida actualizarla.</p>
 */
@RestController
@RequestMapping("/api/v1/import-profiles")
public class ImportProfileController {

	/** Descripcion de un formato admitido, con su ejemplo. */
	public record ProfileView(
			String id,
			String name,
			String description,
			List<String> extensions,
			List<String> fields,
			List<String> expected,
			String example) {
	}

	private final PathMatchingResourcePatternResolver resolver =
			new PathMatchingResourcePatternResolver();

	@GetMapping
	public ResponseEntity<List<ProfileView>> listar() throws IOException {
		List<ProfileView> perfiles = new ArrayList<>();

		for (Resource recurso : resolver.getResources("classpath*:profiles/*.profile")) {
			try (Reader r = new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8)) {
				ImportProfile p = ImportProfile.cargar(r);
				perfiles.add(new ProfileView(p.getId(), p.getName(), p.getDescription(),
						p.getExtensions(),
						p.getFieldMap().values().stream().distinct().sorted().toList(),
						p.getExpected(), p.getExample()));
			}
		}

		perfiles.sort(Comparator.comparing(ProfileView::name));

		// Los formatos admitidos no cambian entre despliegues (API-04).
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic())
				.body(perfiles);
	}
}
