package org.slcp.service.requirements;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.slcp.service.domain.EventRecord;
import org.slcp.service.domain.Project;
import org.slcp.service.domain.ProjectRole;
import org.slcp.service.domain.Requirement;
import org.slcp.service.domain.RequirementDecision;
import org.slcp.service.domain.RequirementKind;
import org.slcp.service.domain.RequirementStatus;
import org.slcp.service.domain.TextOrigin;
import org.slcp.service.domain.User;
import org.slcp.service.ingestion.CriterionSuggester;
import org.slcp.service.ingestion.ExtractionReport;
import org.slcp.service.ingestion.ImportProfile;
import org.slcp.service.ingestion.ParsedRequirement;
import org.slcp.service.ingestion.RequirementSource;
import org.slcp.service.ingestion.RequirementLinter;
import org.slcp.service.ingestion.DomainClassifier;
import org.slcp.service.ingestion.FieldValidator;
import org.slcp.service.ingestion.DomainCoherence;
import org.slcp.service.ingestion.StatementSimilarity;
import org.slcp.service.ingestion.StatementSuggester;
import org.slcp.service.projects.ProjectAccessException;
import org.slcp.service.projects.ProjectService;
import org.slcp.service.registration.EventRecordRepository;
import org.slcp.service.registration.UserRepository;
import org.slcp.service.requirements.RequirementContracts.AcceptHeldRequest;
import org.slcp.service.requirements.RequirementContracts.HeldGroup;
import org.slcp.service.requirements.RequirementContracts.HeldSuspect;
import org.slcp.service.requirements.RequirementContracts.HeldRequirement;
import org.slcp.service.requirements.RequirementContracts.CheckResult;
import org.slcp.service.requirements.RequirementContracts.DomainAlert;
import org.slcp.service.requirements.RequirementContracts.DuplicateView;
import org.slcp.service.requirements.RequirementContracts.FieldIssue;
import org.slcp.service.requirements.RequirementContracts.FindingView;
import org.slcp.service.requirements.RequirementContracts.ImportRequest;
import org.slcp.service.requirements.RequirementContracts.ImportResult;
import org.slcp.service.requirements.RequirementContracts.RenumberedView;
import org.slcp.service.requirements.RequirementContracts.RequirementRequest;
import org.slcp.service.requirements.RequirementContracts.RequirementSummary;
import org.slcp.service.requirements.RequirementContracts.RequirementView;
import org.slcp.service.requirements.RequirementContracts.SuggestionView;
import org.slcp.service.requirements.RequirementContracts.SuspectedView;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requisitos de un proyecto: alta, importacion, validacion y sugerencia.
 *
 * <p>La validacion no se almacena. Los hallazgos y las propuestas se calculan al
 * consultar, de modo que un cambio en las reglas se refleja de inmediato sin
 * alterar el estado de ningun requisito, conforme a Q-65. Guardarlos los dejaria
 * obsoletos en silencio, que es el modo de fallo que este proyecto persigue
 * desde el principio.</p>
 */
@Service
public class RequirementService {

	private final RequirementRepository requirements;
	private final ProjectService projects;
	private final UserRepository users;
	private final EventRecordRepository events;
	private final CriterionSuggester suggester;
	private final RequirementDecisionRepository decisiones;
	private final DeliverableLookup deliverables;
	private final ResourceLoader resources;
	private final Clock clock;

	public RequirementService(RequirementRepository requirements, ProjectService projects,
			UserRepository users, EventRecordRepository events, CriterionSuggester suggester,
			RequirementDecisionRepository decisiones, DeliverableLookup deliverables,
			ResourceLoader resources, Clock clock) {
		this.requirements = requirements;
		this.projects = projects;
		this.users = users;
		this.events = events;
		this.suggester = suggester;
		this.decisiones = decisiones;
		this.deliverables = deliverables;
		this.resources = resources;
		this.clock = clock;
	}

	// =================================================================
	// Consulta
	// =================================================================

	@Transactional(readOnly = true)
	public List<RequirementView> listar(String projectReadableId, UUID solicitante) {
		Project proyecto = exigirMembresia(projectReadableId, solicitante);
		RequirementLinter linter = cargarLinter();

		return requirements.findByProjectIdOrderByReadableIdAsc(proyecto.getId()).stream()
				.map(r -> vista(r, linter))
				.toList();
	}

	@Transactional(readOnly = true)
	public RequirementSummary resumen(String projectReadableId, UUID solicitante) {
		List<RequirementView> vistas = listar(projectReadableId, solicitante);

		return new RequirementSummary(
				vistas.size(),
				vistas.stream().filter(RequirementView::conforming).count(),
				vistas.stream().filter(v -> !v.findings().isEmpty()).count(),
				vistas.stream().filter(v -> v.verification() == null || v.verification().isBlank()).count(),
				vistas.stream().filter(v -> "APPROVED".equals(v.status())).count(),
				vistas.stream().filter(v -> "SUGGESTED".equals(v.statementOrigin())
						|| "SUGGESTED".equals(v.verificationOrigin())).count());
	}

	// =================================================================
	// Alta y edicion
	// =================================================================

	/** Crea un requisito escrito a mano. Exige ser miembro del equipo (ROL-02). */
	@Transactional
	public RequirementView crear(String projectReadableId, RequirementRequest peticion, UUID autor) {
		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Instant momento = Instant.now(clock);

		Requirement requisito = Requirement.crear(proyecto.getId(),
				siguienteIdentificador(proyecto.getId()),
				peticion.sourceId(), null,
				tipoDe(peticion.kind(), peticion.sourceId()),
				peticion.name(), peticion.statement(), peticion.verification(),
				peticion.actor(),
				autor, momento);

		requirements.save(requisito);
		registrar("REQUIREMENT_CREATED", proyecto.getId(), autor, requisito.getReadableId(), momento);

		return vista(requisito, cargarLinter());
	}

	/**
	 * Modifica un requisito.
	 *
	 * @param aceptaSugerencia si el texto procede de una propuesta de la
	 *                         plataforma, para poder marcarlo conforme a ANA-16
	 */
	@Transactional
	public RequirementView editar(String projectReadableId, String readableId,
			RequirementRequest peticion, boolean aceptaSugerencia, UUID autor) {

		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Requirement requisito = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		TextOrigin origen = aceptaSugerencia ? TextOrigin.SUGGESTED : TextOrigin.HUMAN;
		// Cambiar de clase arrastra el identificador de origen: su prefijo dice si
		// el requisito es funcional o no, y dejarlo como estaba haria que la
		// etiqueta contradijera al requisito.
		RequirementKind clase = tipoDe(peticion.kind(), requisito.getSourceId());
		if (clase != requisito.getKind() && requisito.getSourceId() != null
				&& !requisito.getSourceId().isBlank()) {

			requisito.renombrarOrigen(origenPara(proyecto.getId(), clase, requisito));
		}

		// El identificador legible lleva la version: modificar lo lleva a la
		// siguiente, de modo que quien vea REQ-0007-v2 sepa sin preguntar que ese
		// requisito ya cambio desde que se redacto.
		requisito.renumerarVersion();

		// El actor puede corregirse: se importa del documento y no siempre viene, o
		// viene mal. Sin poder tocarlo, un diagrama de casos de uso incompleto no
		// tendria arreglo.
		requisito.asignarActor(peticion.actor());

		requisito.editar(clase, peticion.name(), peticion.statement(), peticion.verification(),
				origen, origen, momento);

		registrar(aceptaSugerencia ? "REQUIREMENT_SUGGESTION_ACCEPTED" : "REQUIREMENT_EDITED",
				proyecto.getId(), autor, requisito.getReadableId(), momento);

		return vista(requisito, cargarLinter());
	}

	/**
	 * Cambia el estado.
	 *
	 * <p>La aprobacion de un requisito ocurre en dos etapas y las hacen roles
	 * distintos: el facilitador revisa y el propietario del producto aprueba
	 * (RQM-05). Concentrar ambas en una persona dejaria la aprobacion sin
	 * contraste, que es justamente lo que la doble etapa evita.</p>
	 */
	@Transactional
	public RequirementView transitar(String projectReadableId, String readableId,
			String destino, UUID autor) {

		RequirementStatus estado = RequirementStatus.valueOf(destino);
		ProjectRole exigido = rolQueDecide(estado);

		Project proyecto = exigirRol(projectReadableId, autor, exigido);
		Requirement requisito = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		// RQM-05: quien reviso no puede ser quien aprueba. Se comprueba sobre la
		// misma persona y el mismo requisito, no sobre el rol: en un equipo pequeno
		// una persona puede tener ambos roles, y precisamente por eso hace falta
		// impedir que recorra sola las dos etapas.
		if (estado == RequirementStatus.APPROVED
				&& autor.equals(requisito.getReviewedBy())) {
			throw new RequirementException(
					"Usted reviso este requisito, de modo que no puede aprobarlo. La revision y la "
							+ "aprobacion son dos etapas y las hacen dos personas distintas");
		}

		// Rectificar una aprobacion es legitimo mientras nadie haya construido y
		// entregado sobre ella. Un entregable aceptado dio por buena una decision
		// que ahora se retira, y esa aceptacion quedaria sin fundamento.
		if (requisito.getStatus() == RequirementStatus.APPROVED
				&& estado != RequirementStatus.APPROVED) {
			comprobarQueNoHayTrabajoAceptado(requisito);
		}

		if (estado == RequirementStatus.REVIEWED) {
			requisito.registrarRevision(autor);
		}

		// La decision se conserva con la version sobre la que se tomo y con el texto
		// tal como estaba: si el requisito cambia despues, la decision seguiria
		// apuntando a un texto que quien decidio no leyo.
		decisiones.save(RequirementDecision.de(requisito.getId(), requisito.getVersion(),
				estado.name(), autor,
				users.findById(autor).map(User::getUsername).orElse("desconocido"),
				requisito.getStatement(), momento));

		requisito.transitarA(estado, momento);
		registrar("REQUIREMENT_" + estado.name(), proyecto.getId(), autor,
				requisito.getReadableId(), momento);

		return vista(requisito, cargarLinter());
	}

	/**
	 * Comprueba un enunciado antes de darlo de alta.
	 *
	 * <p>Se informa de a que se parece y de si trata del asunto del proyecto, y no
	 * se impide nada: la decision es de quien redacta, que sabe si su requisito es
	 * el mismo que otro o solo se le parece.</p>
	 */
	@Transactional(readOnly = true)
	public CheckResult comprobar(String projectReadableId, String enunciado, UUID solicitante) {
		Project proyecto = projects.exigirAccesoPublico(projectReadableId, solicitante);
		List<Requirement> existentes = requirements
				.findByProjectIdOrderByReadableIdAsc(proyecto.getId());

		List<SuspectedView> parecidos = new ArrayList<>();
		for (Requirement r : existentes) {
			double s = StatementSimilarity.entre(enunciado, r.getStatement());
			if (s >= StatementSimilarity.UMBRAL_SOSPECHA) {
				parecidos.add(new SuspectedView(r.getReadableId(), r.getSourceId(),
						r.getReadableId(), redondear(s), r.getStatement()));
			}
		}
		parecidos.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));

		DomainCoherence.Veredicto dominio = DomainCoherence.examinarUno(
				existentes.stream().map(Requirement::getStatement).toList(), enunciado);

		return new CheckResult(parecidos, avisoDe(dominio),
				parecidos.isEmpty() && !dominio.aviso());
	}

	/** Convierte los grupos del clasificador en lo que viaja a la interfaz. */
	private List<HeldGroup> gruposDe(List<DomainClassifier.Grupo> grupos,
			ExtractionReport informe) {

		List<HeldGroup> salida = new ArrayList<>();
		for (DomainClassifier.Grupo g : grupos) {
			List<HeldRequirement> lista = new ArrayList<>();
			for (int indice : g.indices()) {
				ParsedRequirement parsed = informe.requirements().get(indice);
				lista.add(new HeldRequirement(parsed.sourceId(),
						RequirementKind.conjeturar(parsed.sourceId()).name(),
						parsed.get("name"), enunciadoDe(parsed), parsed.get("verification"),
						parsed.get("actor")));
			}
			salida.add(new HeldGroup(g.etiqueta(), g.terminos(), lista));
		}
		return salida;
	}

	/**
	 * Da de alta requisitos retenidos, tras decidirlo una persona.
	 *
	 * <p>Corresponde a quien produce, que es tanto el miembro del equipo como el
	 * facilitador --- este lo es tambien de su proyecto. Se comprueban duplicados
	 * e identificadores igual que al importar: la decision fue sobre el dominio, no
	 * sobre lo demas.</p>
	 */
	@Transactional
	public ImportResult aceptarRetenidos(String projectReadableId,
			AcceptHeldRequest peticion, UUID autor) {

		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Instant momento = Instant.now(clock);

		List<Requirement> existentes = requirements
				.findByProjectIdOrderByReadableIdAsc(proyecto.getId());
		Set<String> origenesUsados = existentes.stream()
				.map(Requirement::getSourceId)
				.filter(id -> id != null && !id.isBlank())
				.collect(Collectors.toCollection(HashSet::new));

		int secuencia = requirements.mayorNumero(proyecto.getId());
		// Los campos que llegan no se dan por buenos: que vengan en el archivo no
		// significa que sean correctos, y todo lo que se calcule sobre ellos
		// heredaria el error sin avisar.
		List<FieldIssue> reparos = new ArrayList<>();

		List<DuplicateView> duplicados = new ArrayList<>();
		List<RenumberedView> renumerados = new ArrayList<>();
		int importados = 0;

		for (HeldRequirement retenido : peticion.requirements()) {
			Requirement parecido = null;
			double semejanza = 0.0;
			for (Requirement existente : existentes) {
				double s = StatementSimilarity.entre(retenido.statement(), existente.getStatement());
				if (s > semejanza) {
					semejanza = s;
					parecido = existente;
				}
			}
			if (parecido != null && semejanza >= StatementSimilarity.UMBRAL_DUPLICADO) {
				duplicados.add(new DuplicateView(retenido.sourceId(), parecido.getReadableId(),
						parecido.getSourceId(), redondear(semejanza), parecido.getStatement()));
				continue;
			}

			String origenFinal = retenido.sourceId();
			if (origenFinal != null && !origenFinal.isBlank() && origenesUsados.contains(origenFinal)) {
				origenFinal = siguienteLibre(origenFinal, origenesUsados);
				renumerados.add(new RenumberedView(retenido.sourceId(), origenFinal,
						retenido.statement()));
			}
			if (origenFinal != null && !origenFinal.isBlank()) {
				origenesUsados.add(origenFinal);
			}

			secuencia++;
			Requirement requisito = Requirement.crear(proyecto.getId(),
					String.format("REQ-%04d-v1", secuencia),
					origenFinal, null,
					RequirementKind.valueOf(retenido.kind()),
					retenido.name(), retenido.statement(), retenido.verification(),
					retenido.actor(),
					autor, momento);

			requirements.save(requisito);
			existentes.add(requisito);
			importados++;
		}

		registrar("REQUIREMENTS_ACCEPTED", proyecto.getId(), autor,
				importados + " retenidos aceptados", momento);

		String mensaje = importados + " requisitos dados de alta en borrador."
				+ (duplicados.isEmpty() ? "" : " " + duplicados.size()
						+ " se omitieron por decir lo mismo que otros ya presentes.");

		return new ImportResult(peticion.requirements().size(), importados, duplicados.size(),
				duplicados, renumerados, List.of(),
				new DomainAlert(false, 1.0, List.of(), List.of(), "Aceptados por decision expresa"),
				List.of(), List.of(), List.of(), Map.of(), List.of(), mensaje);
	}

	private DomainAlert avisoDe(DomainCoherence.Veredicto v) {
		return new DomainAlert(v.aviso(), v.coincidencia(), v.terminosCompartidos(),
				v.terminosDeLoQueLlega(), v.explicacion());
	}

	/**
	 * Elimina un requisito.
	 *
	 * <p>Solo mientras nada se haya decidido sobre el. Uno revisado o aprobado se
	 * anula, no se borra: la anulacion lo retira de lo exigible y conserva su
	 * historia, y borrarlo destruiria la constancia de quien decidio que.</p>
	 */
	@Transactional
	public void eliminar(String projectReadableId, String readableId, UUID autor) {
		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Requirement requisito = buscar(proyecto.getId(), readableId);
		Instant momento = Instant.now(clock);

		if (!requisito.puedeEliminarse()) {
			throw new RequirementException(
					"Este requisito fue revisado o aprobado y no puede eliminarse. Anulelo si deja "
							+ "de exigirse: la anulacion lo retira conservando su historia");
		}

		registrar("REQUIREMENT_DELETED", proyecto.getId(), autor, requisito.getReadableId(), momento);
		requirements.delete(requisito);
	}

	// =================================================================
	// Importacion
	// =================================================================

	/**
	 * Carga un documento completo.
	 *
	 * <p>Lo importado nace en borrador y con sus carencias reportadas. Nada se
	 * completa ni se corrige al importar: TRC-13 distingue lo que el documento
	 * dice de lo que alguien supone que quiso decir.</p>
	 */
	@Transactional
	public ImportResult importar(String projectReadableId, ImportRequest peticion, UUID autor) {
		Project proyecto = exigirRol(projectReadableId, autor, ProjectRole.TEAM_MEMBER);
		Instant momento = Instant.now(clock);

		ExtractionReport informe;
		try {
			ImportProfile perfil = cargarPerfil(peticion.profileId());
			// El lector lo elige el perfil: un JSON se analiza como JSON y un CSV por
			// columnas. Forzar el de lineas sobre todos funcionaria con los archivos
			// bien formateados y fallaria con los compactos.
			informe = RequirementSource.of(perfil).extraer(new StringReader(peticion.content()));
		} catch (IOException e) {
			throw new RequirementException("No se pudo leer el documento: " + e.getMessage());
		}

		int secuencia = requirements.mayorNumero(proyecto.getId());
		List<Requirement> existentes = requirements.findByProjectIdOrderByReadableIdAsc(proyecto.getId());

		// Los identificadores de origen ya usados, para no repetirlos al renumerar.
		Set<String> origenesUsados = existentes.stream()
				.map(Requirement::getSourceId)
				.filter(id -> id != null && !id.isBlank())
				.collect(Collectors.toCollection(HashSet::new));

		// Se examina el conjunto entero antes de importar nada: el dominio es una
		// propiedad del documento, no de cada requisito por separado.
		DomainCoherence.Veredicto dominio = DomainCoherence.examinar(
				existentes.stream().map(Requirement::getStatement).toList(),
				informe.requirements().stream().map(this::enunciadoDe)
						.filter(e -> !e.isBlank()).toList());

		// Se reparte antes de dar nada de alta. Lo ajeno no entra ni como borrador:
		// un requisito de otro sistema en la lista contamina cuanto se calcule
		// despues --- la validacion, los duplicados, el propio vocabulario del
		// proyecto --- y quien lo encuentre semanas mas tarde no sabra si sobraba o
		// si el proyecto crecio.
		List<String> enunciadosEntrantes = informe.requirements().stream()
				.map(this::enunciadoDe).toList();

		DomainClassifier.Reparto reparto = DomainClassifier.repartir(
				existentes.stream().map(Requirement::getStatement).toList(),
				enunciadosEntrantes);

		Set<Integer> aImportar = new HashSet<>(reparto.propios());

		// Los campos que llegan no se dan por buenos: que vengan en el archivo no
		// significa que sean correctos, y todo lo que se calcule sobre ellos
		// heredaria el error sin avisar.
		List<FieldIssue> reparos = new ArrayList<>();

		List<DuplicateView> duplicados = new ArrayList<>();
		List<RenumberedView> renumerados = new ArrayList<>();
		List<HeldSuspect> sospechas = new ArrayList<>();
		int importados = 0;

		for (int indice = 0; indice < informe.requirements().size(); indice++) {
			if (!aImportar.contains(indice)) {
				continue;
			}
			ParsedRequirement parsed = informe.requirements().get(indice);
			String sourceId = parsed.sourceId();
			String enunciado = enunciadoDe(parsed);

			for (FieldValidator.Reparo r : FieldValidator.revisar(parsed.get("name"), enunciado,
					parsed.get("verification"), parsed.get("actor"), parsed.get("priority"),
					sourceId)) {

				reparos.add(new FieldIssue(sourceId == null ? enunciado.substring(0,
						Math.min(40, enunciado.length())) : sourceId,
						r.campo(), r.valor(), r.motivo(), r.grave()));
			}

			// Se compara por lo que el requisito dice, no por como se llama. El
			// identificador lo pone quien redacta el documento: dos documentos
			// distintos numeran desde uno, y el mismo requisito puede llegar con
			// otro numero. Decidir por el identificador descartaria requisitos
			// nuevos por llevar un numero ya usado, y admitiria dos veces el mismo
			// por venir numerado distinto.
			Requirement parecido = null;
			double semejanza = 0.0;

			for (Requirement existente : existentes) {
				double s = StatementSimilarity.entre(enunciado, existente.getStatement());
				if (s > semejanza) {
					semejanza = s;
					parecido = existente;
				}
			}

			if (parecido != null && semejanza >= StatementSimilarity.UMBRAL_DUPLICADO) {
				duplicados.add(new DuplicateView(sourceId, parecido.getReadableId(),
						parecido.getSourceId(), redondear(semejanza), parecido.getStatement()));
				continue;
			}

			// Ni identico ni claramente distinto: no entra. Solo se da de alta sin
			// preguntar lo que es del dominio y no se parece a nada; en la franja
			// intermedia, decidir solo significa equivocarse a veces, y equivocarse
			// hacia "es el mismo" pierde un requisito sin que nadie se entere.
			if (parecido != null && semejanza >= StatementSimilarity.UMBRAL_SOSPECHA) {
				sospechas.add(new HeldSuspect(
						new HeldRequirement(sourceId,
								RequirementKind.conjeturar(sourceId).name(),
								parsed.get("name"), enunciado, parsed.get("verification"),
								parsed.get("actor")),
						parecido.getReadableId(), parecido.getSourceId(),
						redondear(semejanza), parecido.getStatement()));
				continue;
			}

			// El identificador de origen es solo una etiqueta. Si ya esta tomado por
			// otro requisito, se le asigna el siguiente libre de su familia y se
			// informa: el requisito entra igualmente, porque dice algo distinto.
			String origenFinal = sourceId;
			if (sourceId != null && !sourceId.isBlank() && origenesUsados.contains(sourceId)) {
				origenFinal = siguienteLibre(sourceId, origenesUsados);
				renumerados.add(new RenumberedView(sourceId, origenFinal, enunciado));
			}
			if (origenFinal != null && !origenFinal.isBlank()) {
				origenesUsados.add(origenFinal);
			}

			secuencia++;
			Requirement requisito = Requirement.crear(proyecto.getId(),
					String.format("REQ-%04d-v1", secuencia),
					origenFinal, parsed.sourceLine(),
					RequirementKind.conjeturar(origenFinal),
					parsed.get("name"),
					enunciado,
					parsed.get("verification"),
					// El actor viene en el documento y se conserva: sin el no puede
					// dibujarse un diagrama de casos de uso, y la especificacion ya
					// contestaba esa pregunta.
					parsed.get("actor"),
					autor, momento);

			requirements.save(requisito);
			existentes.add(requisito);
			importados++;

		}

		registrar("REQUIREMENTS_IMPORTED", proyecto.getId(), autor,
				importados + " de " + informe.total(), momento);

		StringBuilder mensaje = new StringBuilder();
		mensaje.append(importados).append(" requisitos importados de ")
				.append(informe.total()).append(" encontrados.");

		if (!duplicados.isEmpty()) {
			mensaje.append(" ").append(duplicados.size())
					.append(" se omitieron por decir lo mismo que otros ya presentes.");
		}
		if (!renumerados.isEmpty()) {
			mensaje.append(" ").append(renumerados.size())
					.append(" llegaban con un identificador ya usado por otro requisito distinto y "
							+ "se les asigno el siguiente libre.");
		}
		if (!sospechas.isEmpty()) {
			mensaje.append(" ").append(sospechas.size())
					.append(" no se dieron de alta por parecerse a otros ya presentes: decida si "
							+ "son el mismo requisito.");
		}
		int retenidos = reparto.transversales().stream().mapToInt(g -> g.indices().size()).sum()
				+ reparto.ajenos().stream().mapToInt(g -> g.indices().size()).sum();

		if (retenidos > 0) {
			mensaje.append(" ").append(retenidos)
					.append(" no se dieron de alta por no parecer de este proyecto: quedan a la "
							+ "espera de que usted decida.");
		}
		long graves = reparos.stream().filter(FieldIssue::severe).count();
		if (!reparos.isEmpty()) {
			mensaje.append(" ").append(reparos.size())
					.append(" campos llegaron con un valor que conviene revisar")
					.append(graves > 0 ? ", " + graves + " de ellos graves." : ".");
		}

		if (dominio.aviso()) {
			mensaje.append(" AVISO: este documento apenas comparte vocabulario con lo que ya hay "
					+ "en el proyecto; compruebe que corresponde a este sistema.");
		}
		mensaje.append(" Todos quedan en borrador y con sus carencias reportadas.");

		List<HeldGroup> transversales = gruposDe(reparto.transversales(), informe);
		List<HeldGroup> ajenos = gruposDe(reparto.ajenos(), informe);

		return new ImportResult(informe.total(), importados, duplicados.size(), duplicados,
				renumerados, sospechas, avisoDe(dominio), transversales, ajenos, reparos,
				informe.missingByField(), informe.unknownLabels(), mensaje.toString());
	}

	// =================================================================
	// Interno
	// =================================================================

	/**
	 * Enunciado del requisito.
	 *
	 * <p>Los tipos que no traen descripcion la tienen en otro campo: las historias
	 * de usuario en su enunciado, los casos de uso en su flujo principal. Tomar
	 * solo la descripcion dejaria sin enunciado a mas de un tercio del documento.</p>
	 */
	private String enunciadoDe(ParsedRequirement parsed) {
		for (String campo : List.of("description", "statement", "mainFlow", "name")) {
			if (parsed.tiene(campo)) {
				return parsed.get(campo);
			}
		}
		return "";
	}

	private RequirementView vista(Requirement r, RequirementLinter linter) {
		List<FindingView> hallazgos = linter.examinar(r.getStatement()).stream()
				.map(h -> new FindingView(h.regla(), h.caracteristica().getEtiqueta(),
						h.gravedad().name(), h.evidencia(), h.explicacion()))
				.toList();

		// Redacciones alternativas del enunciado, solo si tiene algo que corregir.
		List<SuggestionView> redacciones = new StatementSuggester(linter)
				.proponer(r.getStatement()).stream()
				.map(p -> new SuggestionView(p.texto(), p.fundamento(), p.exigeDecision()))
				.toList();

		List<SuggestionView> propuestas = List.of();
		if (!r.tieneCriterio() && r.getKind().exigeCriterio()) {
			boolean magnitud = r.getKind() == RequirementKind.NON_FUNCTIONAL;
			propuestas = suggester.proponer(r.getStatement(), magnitud).stream()
					.map(p -> new SuggestionView(p.texto(), p.fundamento(), p.exigeDecision()))
					.toList();
		}

		boolean conforme = hallazgos.stream().noneMatch(h -> "DEFECTO".equals(h.severity()))
				&& !r.incompleto();

		return new RequirementView(r.getReadableId(), r.getSourceId(), r.getSourceLine(),
				r.getKind().name(), r.getKind().getEtiqueta(), r.getName(), r.getStatement(),
				r.getVerification(), r.getActor(), r.getStatus().name(), r.getVersion(),
				r.getReviewedBy() == null ? null : r.getReviewedBy().toString(),
				r.getStatementOrigin().name(), r.getVerificationOrigin().name(),
				conforme, r.puedeEliminarse(), hallazgos, redacciones, propuestas, r.getUpdatedAt());
	}

	private RequirementLinter cargarLinter() {
		try (Reader r = leer("classpath:rules/es-29148.rules")) {
			return RequirementLinter.cargar(r);
		} catch (IOException e) {
			throw new RequirementException("No se pudieron cargar las reglas de validacion");
		}
	}

	private ImportProfile cargarPerfil(String profileId) throws IOException {
		try (Reader r = leer("classpath:profiles/" + profileId + ".profile")) {
			return ImportProfile.cargar(r);
		} catch (IOException e) {
			throw new RequirementException("No existe el perfil de importacion " + profileId);
		}
	}

	private Reader leer(String ubicacion) throws IOException {
		Resource recurso = resources.getResource(ubicacion);
		return new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8);
	}

	private String siguienteIdentificador(UUID projectId) {
		// Por el mayor usado y no por la cuenta: si se elimino un requisito, contar
		// devuelve un numero ya tomado y el alta choca contra la unicidad.
		return String.format("REQ-%04d-v1", requirements.mayorNumero(projectId) + 1);
	}

	private RequirementKind tipoDe(String declarado, String sourceId) {
		if (declarado != null && !declarado.isBlank()) {
			try {
				return RequirementKind.valueOf(declarado);
			} catch (IllegalArgumentException e) {
				throw new RequirementException("Tipo de requisito no reconocido: " + declarado);
			}
		}
		return RequirementKind.conjeturar(sourceId);
	}

	/**
	 * Rol que puede llevar un requisito al estado indicado.
	 *
	 * <p>Revisar corresponde al facilitador, aprobar al propietario, y devolver a
	 * borrador o rechazar a quien produce, que es quien ha de corregirlo.</p>
	 */
	private ProjectRole rolQueDecide(RequirementStatus estado) {
		return switch (estado) {
			case REVIEWED -> ProjectRole.PROJECT_FACILITATOR;
			case APPROVED, REJECTED -> ProjectRole.PRODUCT_OWNER;
			default -> ProjectRole.TEAM_MEMBER;
		};
	}

	/**
	 * Impide retirar la aprobacion de un requisito con trabajo ya aceptado.
	 *
	 * <p>La comprobacion se hace en el servicio y no en la base porque el motivo
	 * es de proceso y no de integridad: la fila seguiria siendo valida, pero la
	 * aceptacion de un entregable habria dado por buena una decision que se
	 * retira despues.</p>
	 */
	private void comprobarQueNoHayTrabajoAceptado(Requirement requisito) {
		List<String> aceptados = deliverables.aceptadosDe(requisito.getId());

		if (!aceptados.isEmpty()) {
			throw new RequirementException(
					"No puede retirarse la aprobacion de este requisito: ya hay trabajo aceptado "
							+ "sobre el (" + String.join(", ", aceptados) + "). Formule una peticion "
							+ "de cambio, que deja constancia de por que cambia lo decidido");
		}
	}

	/**
	 * Siguiente identificador libre de la misma familia.
	 *
	 * <p>De RF-01 se pasa a RF-09 si los ocho primeros estan tomados, conservando
	 * el prefijo y el ancho del numero. Se conserva la familia porque distingue lo
	 * funcional de lo que no lo es, y perderla al renumerar cambiaria como se lee
	 * el requisito.</p>
	 */
	/**
	 * Identificador de origen que corresponde a la clase indicada.
	 *
	 * <p>Se conserva el numero si esta libre en la familia de destino, y se toma
	 * el siguiente si no lo esta. Conservarlo ayuda a seguir el rastro de un
	 * requisito que cambio de clase, que de otro modo pareceria otro distinto.</p>
	 */
	private String origenPara(UUID projectId, RequirementKind clase, Requirement requisito) {
		String prefijo = switch (clase) {
			case NON_FUNCTIONAL -> "RNF-";
			case CONSTRAINT -> "RES-";
			case USER_STORY -> "HU-";
			case USE_CASE -> "CU-";
			default -> "RF-";
		};

		Matcher m = Pattern.compile("(\\d+)$").matcher(requisito.getSourceId());
		String numero = m.find() ? m.group(1) : "01";

		Set<String> usados = requirements.findByProjectIdOrderByReadableIdAsc(projectId).stream()
				.filter(r -> !r.getId().equals(requisito.getId()))
				.map(Requirement::getSourceId)
				.filter(id -> id != null && !id.isBlank())
				.collect(Collectors.toCollection(HashSet::new));

		String candidato = prefijo + numero;
		return usados.contains(candidato) ? siguienteLibre(candidato, usados) : candidato;
	}

	private String siguienteLibre(String sourceId, Set<String> usados) {
		Matcher m = Pattern.compile("^(.*?)(\\d+)$").matcher(sourceId.trim());
		if (!m.find()) {
			// Sin numero al final no hay familia que continuar: se sufija.
			String candidato = sourceId + "-bis";
			int n = 2;
			while (usados.contains(candidato)) {
				candidato = sourceId + "-bis" + n++;
			}
			return candidato;
		}

		String prefijo = m.group(1);
		String digitos = m.group(2);
		int numero = Integer.parseInt(digitos);

		String candidato;
		do {
			numero++;
			candidato = prefijo + String.format("%0" + digitos.length() + "d", numero);
		} while (usados.contains(candidato));

		return candidato;
	}

	private double redondear(double valor) {
		return Math.round(valor * 100.0) / 100.0;
	}

	private Requirement buscar(UUID projectId, String readableId) {
		return requirements.findByProjectIdAndReadableId(projectId, readableId)
				.orElseThrow(() -> new RequirementException("No existe ese requisito en el proyecto"));
	}

	private Project exigirMembresia(String readableId, UUID solicitante) {
		return projects.exigirAccesoPublico(readableId, solicitante);
	}

	private Project exigirRol(String readableId, UUID solicitante, ProjectRole rol) {
		Project proyecto = exigirMembresia(readableId, solicitante);
		if (!projects.rolesEn(proyecto.getId(), solicitante).contains(rol)) {
			throw new ProjectAccessException(
					"Esta operacion corresponde al " + rol.getEtiqueta().toLowerCase());
		}
		return proyecto;
	}

	private void registrar(String tipo, UUID projectId, UUID actorId, String detalle, Instant momento) {
		String quien = users.findById(actorId).map(User::getUsername).orElse("desconocido");
		events.save(EventRecord.de(tipo, "Project", projectId, actorId, quien, detalle, momento));
	}
}
