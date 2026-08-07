package org.slcp.core.naming;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Ejecutor del oraculo de conformidad de {@link IdentifierAdvisor}.
 *
 * <p>Igual que el ejecutor del modulo de transformacion, solo lee el oraculo y
 * nunca lo modifica.</p>
 */
public final class AdvisorVectorRunner {

	private AdvisorVectorRunner() {
	}

	public static void main(String[] args) throws IOException {
		Path vectors = Paths.get(args.length > 0 ? args[0] : "advisor-vectors.tsv");
		List<String> failures = new ArrayList<>();
		int passed = 0;

		for (String line : Files.readAllLines(vectors, StandardCharsets.UTF_8)) {
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] f = line.split("\t", -1);
			if (f.length < 8) {
				failures.add("linea mal formada: " + line);
				continue;
			}

			String id = f[0];
			String singular = f[1];
			String plural = f[2];
			Target target = Target.valueOf(f[3]);
			Kind kind = Kind.valueOf(f[4]);
			String context = f[5];
			String roleHint = f[6];
			String expected = f[7];

			String actual;
			if (id.startsWith("W02") || id.startsWith("W03")) {
				actual = String.join("|",
						IdentifierAdvisor.suggest(singular, plural, target, kind, context, roleHint));
			} else {
				IdentifierAdvisor.Validation v =
						IdentifierAdvisor.validate(singular, plural, target, kind);
				actual = v.isLegal() ? "LEGAL:" + v.getRendered() : v.getStatus().name();
			}

			if (expected.equals(actual)) {
				passed++;
			} else {
				failures.add(id + " esperado='" + expected + "' obtenido='" + actual + "'");
			}
		}

		System.out.println("Oraculo: " + vectors.toAbsolutePath());
		System.out.println("Vectores superados: " + passed);
		System.out.println("Vectores fallidos:  " + failures.size());
		for (String failure : failures) {
			System.out.println("  FALLO  " + failure);
		}
		if (failures.isEmpty()) {
			System.out.println("RESULTADO: VERDE");
		} else {
			System.out.println("RESULTADO: ROJO");
			System.exit(1);
		}
	}
}
