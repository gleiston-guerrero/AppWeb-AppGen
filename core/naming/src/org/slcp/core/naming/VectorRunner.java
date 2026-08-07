package org.slcp.core.naming;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Ejecutor del oraculo de conformidad de la transformacion de nomenclatura.
 *
 * <p>Lee el archivo de vectores sellado y comprueba que la implementacion lo
 * satisface. Conforme al principio de SLCP-ADR-0002, el oraculo se define antes
 * que la implementacion y esta no puede modificarlo: este ejecutor solo lee.</p>
 *
 * <p>Se implementa sin dependencias externas de forma deliberada, para que el
 * primer incremento sea compilable y verificable con el unico JDK, sin exigir
 * resolucion de artefactos de terceros.</p>
 */
public final class VectorRunner {

	private VectorRunner() {
	}

	private static final class Result {
		int passed;
		final List<String> failures = new ArrayList<>();
	}

	public static void main(String[] args) throws IOException {
		Path vectors = Paths.get(args.length > 0 ? args[0] : "naming-vectors.tsv");
		Result result = run(vectors);

		System.out.println("Oraculo: " + vectors.toAbsolutePath());
		System.out.println("Vectores superados: " + result.passed);
		System.out.println("Vectores fallidos:  " + result.failures.size());

		for (String failure : result.failures) {
			System.out.println("  FALLO  " + failure);
		}

		if (result.failures.isEmpty()) {
			System.out.println("RESULTADO: VERDE");
		} else {
			System.out.println("RESULTADO: ROJO");
			System.exit(1);
		}
	}

	static Result run(Path vectors) throws IOException {
		Result result = new Result();
		List<String> lines = Files.readAllLines(vectors, StandardCharsets.UTF_8);

		for (String line : lines) {
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] f = line.split("\t", -1);
			if (f.length < 7) {
				result.failures.add("linea mal formada: " + line);
				continue;
			}

			String id = f[0];
			String singular = f[1];
			String plural = f[2];
			Target target = Target.valueOf(f[3]);
			Kind kind = Kind.valueOf(f[4]);
			int maxLength = Integer.parseInt(f[5]);
			String expected = f[6];

			String actual;
			try {
				actual = NamingTransform.render(singular, plural, target, kind,
						maxLength > 0 ? maxLength : target.getDefaultMaxLength());
			} catch (NamingException e) {
				actual = "ERROR:" + e.getCode();
			}

			if (expected.equals(actual)) {
				result.passed++;
			} else {
				result.failures.add(id + " esperado='" + expected + "' obtenido='" + actual + "'");
			}
		}
		return result;
	}
}
