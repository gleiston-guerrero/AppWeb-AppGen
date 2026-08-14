package org.slcp.service.generation;

import java.util.Map;
import org.slcp.service.domain.AiFeature;

/**
 * Las instrucciones que se envian al modelo, una por funcion.
 *
 * <p>Estaban incrustadas en cada generador, de modo que solo podian cambiarse
 * tocando el codigo. Aqui figuran como texto editable porque quien conoce el
 * dominio no es quien compila: si una instruccion produce malos resultados en un
 * proyecto concreto, ha de poder corregirse sin esperar una version.</p>
 *
 * <p><strong>La instruccion es de la funcion, no del proveedor.</strong> Todas
 * las APIs de una misma funcion reciben exactamente la misma, y esa es la
 * condicion que hace valida una comparacion: con instrucciones distintas se
 * compararian las instrucciones y no los modelos.</p>
 *
 * <p>Las marcas entre llaves se sustituyen antes de enviar. Si una instruccion
 * editada las omite, el modelo recibira menos contexto del previsto --- no
 * fallara, pero respondera peor, y por eso la pantalla las muestra.</p>
 */
public final class PromptCatalog {

	/** Marcas que se sustituyen en cualquier instruccion. */
	public static final String REQUISITO = "{requisito}";
	public static final String CLASE = "{clase}";
	public static final String HUECO = "{hueco}";

	private PromptCatalog() {
	}

	/** La instruccion de fabrica de cada funcion. */
	public static String porDefecto(AiFeature feature) {
		return switch (feature) {
			case GENERATE_TESTS -> PRUEBAS;
			case GENERATE_SPECS -> ESPECIFICACIONES;
			case GENERATE_DIAGRAMS -> DIAGRAMAS;
			case VALIDATE_REQUIREMENTS -> VALIDACION;
			case GENERATE_CODE -> CODIGO;
		};
	}

	/** Que marcas admite cada funcion, para que la pantalla las explique. */
	public static Map<String, String> marcasDe(AiFeature feature) {
		return switch (feature) {
			case GENERATE_TESTS -> Map.of(
					REQUISITO, "El requisito con su enunciado, criterio y tipo",
					CLASE, "La clase de prueba pedida: aceptacion, limites, negativa o rendimiento",
					HUECO, "La marca que ha de escribir donde falte un dato");

			case GENERATE_SPECS -> Map.of(
					REQUISITO, "Los requisitos de los que ha de salir",
					CLASE, "Caso de uso expandido o historia de usuario",
					HUECO, "La marca que ha de escribir donde falte un dato");

			default -> Map.of(
					REQUISITO, "El requisito o los requisitos de entrada",
					HUECO, "La marca que ha de escribir donde falte un dato");
		};
	}

	// =================================================================

	private static final String PRUEBAS = """
			Eres un ingeniero de pruebas. Escribe un escenario en Gherkin, en castellano, \
			para el requisito que se te da.

			FORMA DEL ESCENARIO
			  Caracteristica: <nombre corto de lo que se prueba>

			    Escenario: <lo que ocurre en este caso concreto, no el nombre de la caracteristica>
			      Dado <el estado del que se parte>
			      Cuando <una sola accion, en presente>
			      Entonces <lo que se observa>
			      Y <cada afirmacion adicional, en su propia linea>

			REGLAS QUE NO PUEDES INCUMPLIR

			1. Reparte el criterio de verificacion en sus tres partes. Un criterio como
			   "Con datos validos, registrar una parcela y comprobar que aparece en el listado"
			   contiene las tres:
			     Dado que se parte de datos validos
			     Cuando se registra una parcela
			     Entonces aparece en el listado
			   NO pegues el criterio entero tras "Entonces": repetiria la accion y esconderia
			   lo unico que se comprueba.

			2. El "Entonces" afirma lo que se observa, no lo que hay que hacer. Nada de
			   "comprobar que", "verificar que" ni "revisar que": eso son instrucciones al
			   tester, no resultados.

			3. Una afirmacion por linea, separadas con "Y". Si la prueba falla, ha de poder
			   saberse cual fallo.

			4. El "Cuando" es una sola accion en presente. Ni infinitivo ni varias acciones
			   encadenadas: si hacen falta dos, el escenario son dos.

			5. El "Dado" fija un estado concreto del mundo. No escribas "Dado que el sistema
			   esta en las condiciones previstas para RF-01": eso no establece nada.

			6. No inventes ninguna cifra, umbral, plazo ni cantidad que no aparezca en el
			   requisito. Donde haga falta una y no la tengas, escribe exactamente: {hueco}

			7. No supongas comportamientos que el requisito no describa.

			8. Escribe solo el Gherkin, sin explicaciones alrededor.

			CLASE DE PRUEBA PEDIDA: {clase}
			  ACCEPTANCE  - el camino que el requisito describe, con su resultado observable
			  BOUNDARY    - el comportamiento justo por debajo, justo en, y justo por encima
			                de cada magnitud declarada. Usa un Esquema del escenario con
			                Ejemplos cuando haya mas de un valor que probar
			  NEGATIVE    - que ocurre cuando NO se cumple la condicion del requisito. Casi
			                ningun requisito lo dice: si no lo dice, deja el resultado como
			                hueco en lugar de suponerlo
			  PERFORMANCE - la medida de la magnitud exigida, con la carga y la repeticion
			                necesarias para que la medida signifique algo

			{requisito}
			""";

	private static final String ESPECIFICACIONES = """
			Eres un analista de requisitos. Redacta lo que se te pide en castellano, a partir \
			de los requisitos que se te dan.

			Devuelve SOLO un objeto JSON, sin texto alrededor.

			Si se pide USE_CASE, con esta forma:
			{
			  "nombre": "frase verbal breve",
			  "actorPrincipal": "quien lo inicia; nunca 'el sistema'",
			  "actoresSecundarios": ["..."],
			  "objetivo": "que se consigue",
			  "precondiciones": ["..."],
			  "flujoPrincipal": [
			    {"numero":1, "accionDelActor":"...", "respuestaDelSistema":"...", "referencia":""}
			  ],
			  "flujosAlternativos": [
			    {"numero":"2.1", "condicion":"...", "respuesta":"...", "desdeElPaso":2}
			  ],
			  "flujosExcepcionales": [
			    {"numero":"E1", "condicion":"... (paso 2)", "respuesta":"... El flujo retorna al paso 2.", "desdeElPaso":2}
			  ],
			  "postcondicionExito": "...",
			  "postcondicionFracaso": "...",
			  "relaciones": "", "requisitosEspeciales": "", "prioridad": "", "riesgos": ""
			}

			Si se pide USER_STORY, con esta otra:
			{
			  "descripcion": "Como <rol>, quiero <funcionalidad>, para <beneficio>.",
			  "criteriosDeAceptacion": "Escenarios en Gherkin: uno de exito y uno por cada camino que no lo alcance",
			  "actor": "el rol, extraido de la descripcion",
			  "funcionalidad": "la accion, extraida de la descripcion",
			  "beneficio": "el porque, extraido de la descripcion",
			  "prioridad": "", "dependencias": "", "componentes": "", "valorDeNegocio": ""
			}

			REGLAS QUE NO PUEDES INCUMPLIR

			1. En un caso de uso, el primer paso empieza por "Este caso de uso inicia cuando"; el
			   ultimo, por "Este caso de uso termina cuando".
			2. Un paso puede tener solo una de las dos columnas. Deja "" en la que no aplique: la
			   persona actua una vez y el sistema hace varias cosas seguidas.
			3. No menciones decisiones de diseno: nada de base de datos, tabla, API ni pantalla
			   concreta. Di "el sistema registra al usuario", no "lo guarda en la base de datos".
			4. Debe haber al menos un paso de comprobacion del que cuelguen las excepciones.
			5. Cada excepcion indica entre parentesis el paso del que se desvia y termina diciendo
			   a que paso retorna el flujo.
			6. La postcondicion de fracaso dice que NO queda hecho, no solo que fallo.
			7. El actor principal no puede ser el sistema: el sistema es la frontera, no un actor.
			8. En una historia, la descripcion es una narrativa Connextra, no tres campos pegados,
			   y el beneficio dice para que sirve, no repite la funcionalidad.
			9. No inventes cifras, plazos ni cantidades que no esten en los requisitos. Donde
			   haga falta una y no la tengas, escribe exactamente: {hueco}

			SE PIDE: {clase}

			{requisito}
			""";

	private static final String DIAGRAMAS = """
			Eres un analista de requisitos. Identifica los actores de caso de uso de los \
			requisitos que se te dan.

			Un actor es quien ejerce lo que el sistema hace o a quien va dirigido. EL SISTEMA NO \
			ES UN ACTOR: es la frontera de lo que se dibuja, y casi todos los enunciados lo ponen \
			como sujeto.

			Devuelve SOLO un objeto JSON con esta forma:
			{
			  "actores": [
			    {"requisito": "RF-01", "actor": "Responsable de la explotacion",
			     "porque": "por que se deduce del enunciado o del dominio"}
			  ]
			}

			REGLAS QUE NO PUEDES INCUMPLIR

			1. Si el enunciado no dice a quien sirve, puedes proponerlo por conocimiento del
			   dominio, pero dilo en "porque": ha de distinguirse lo que el requisito dice de lo
			   que tu infieres.
			2. Si no puedes proponer ninguno con fundamento, escribe {hueco} como actor en lugar
			   de inventar uno plausible.
			3. Un actor es un papel, no una persona concreta ni un aparato del sistema.

			{requisito}
			""";

	private static final String VALIDACION = """
			Eres un revisor de requisitos. Comprueba lo que las reglas automaticas no pueden: \
			si el criterio de verificacion comprueba de verdad lo que el enunciado exige.

			Devuelve SOLO un objeto JSON con esta forma:
			{
			  "reparos": [
			    {"requisito": "RF-01", "campo": "verification",
			     "motivo": "que falla y por que importa", "grave": true}
			  ]
			}

			QUE HAS DE MIRAR

			1. Si el criterio comprueba el enunciado entero o solo una parte de el.
			2. Si el criterio puede ejecutarse tal como esta, o le falta un dato para poder
			   hacerlo.
			3. Si el enunciado exige dos cosas distintas, que deberian ser dos requisitos.
			4. Si el enunciado depende de otro requisito sin decirlo.

			No repitas lo que una regla ya detecta: terminos vagos, voz pasiva, falta de sujeto.
			Eso ya esta comprobado. Senala solo lo que exige entender el contenido.

			Si no encuentras nada, devuelve la lista vacia. Inventar reparos para parecer util
			hace que se dejen de leer todos.

			{requisito}
			""";

	private static final String CODIGO = """
			Eres un programador. Propon la implementacion que hace pasar las pruebas aceptadas \
			que se te dan.

			REGLAS QUE NO PUEDES INCUMPLIR

			1. Escribe solo lo necesario para que pasen esas pruebas. Nada de funciones de mas
			   "por si acaso": lo que no esta probado no esta pedido.
			2. No inventes cifras, umbrales ni plazos que no aparezcan en las pruebas ni en los
			   requisitos. Donde haga falta uno y no lo tengas, escribe {hueco}.
			3. Explica en un comentario cada decision que las pruebas no obliguen a tomar: ahi es
			   donde alguien tendra que revisarte.
			4. Devuelve solo el codigo, sin explicaciones alrededor.

			{requisito}
			""";
}
