# Requisitos: cargar, redactar, validar y aprobar

> Verificado aquí: **7 migraciones** contra PostgreSQL 17, **100 pruebas Java** en verde, y la interfaz con tipos estrictos y `strictTemplates`. El servicio Spring no se compila aquí: `mvn test` es su verificación.

```powershell
cd C:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-requisitos.zip" -DestinationPath . -Force
Remove-Item LEEME-REQUISITOS.md
cd service
mvn clean spring-boot:run
```

En otra ventana: `cd web`, `npm start`.

## Cómo llegar

Entre como facilitador → **Mi trabajo** → en su proyecto, botón **Requisitos**.

Necesita ser **miembro del equipo** del proyecto para cargar o redactar. Si solo es facilitador, incorpórese a sí mismo como miembro del equipo desde *Ver equipo* — el facilitador organiza, el equipo produce (ROL-02).

## Cargar la ERS de MundiPets

1. **Cargar un documento** → elija `docs/casos/MundiPets_ERS_SRS_2A_v1.0.tex`
2. Perfil: *LaTeX con entorno de atributos*
3. **Importar**

Debe informar de **78 encontrados** e importarlos en borrador. Si repite la importación, los omite en lugar de duplicarlos.

## Qué verá en cada requisito

**Los hallazgos**, cada uno con la característica de ISO/IEC/IEEE 29148 que incumple y qué corregir. Distinguidos entre `DEFECTO` —incumple— y `SOSPECHA` —puede incumplir, requiere mirada humana—.

**Los criterios propuestos**, solo para los que no lo tienen. Siempre más de uno (ANA-20), con su fundamento, y tres salidas que cuestan lo mismo: aceptar tal cual, modificar antes de aceptar, o dejarlo como está (ANA-21).

**La procedencia**: si acepta una propuesta, el requisito queda marcado como «texto sugerido» y el resumen lo cuenta (ANA-16, ANA-19).

## Lo que la plataforma NO hace, y es deliberado

**No inventa magnitudes.** Ante un requisito no funcional, propone la forma del criterio con `[indique el valor]` donde va la cifra. El botón de aceptar tal cual queda deshabilitado: hay que rellenarla. Qué valor basta depende del riesgo que se tolere, y eso no está en el requisito.

**No propone cuando no puede derivar.** Ante *«el sistema deberá gestionar adecuadamente la interoperabilidad»* no ofrece nada, porque no hay acción observable de la que partir. Una propuesta genérica parecería una respuesta y se aceptaría sin leerla.

**No corrige nada al importar.** Lo cargado queda tal cual, con sus carencias reportadas.

## Aprobar

Solo el **propietario del producto**, y solo sobre requisitos ya marcados como revisados. La pantalla lo recuerda: aprobar afirma que eso es lo que el sistema debe hacer, no que ya lo haga. **El cierre no se declara**: se calculará de la aceptación de los entregables (RQM-14).

## Reglas que impone la base de datos

Verificadas con siete comprobaciones contra PostgreSQL real.

| Regla | Cómo |
|---|---|
| Un `RF-01` por proyecto | Índice único parcial sobre el identificador de origen |
| **No se cambia el texto de un requisito aprobado** | Disparador: hay que devolverlo a revisión (RQM-08) |
| Identificador, proyecto y fecha inmutables | Disparador (TRC-03) |
| Solo tipos y estados declarados | Restricciones de comprobación |

## Comprobar el rastro

```powershell
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$env:PGPASSWORD = "slcp_dev_only"
& $psql -U slcp -d slcp -c "SELECT event_type, actor_label, payload FROM event_records WHERE event_type LIKE 'REQUIREMENT%' ORDER BY occurred_at DESC LIMIT 10;"
```

Aparecerá `REQUIREMENTS_IMPORTED`, `REQUIREMENT_SUGGESTION_ACCEPTED` y `REQUIREMENT_APPROVED` con quién lo hizo.

## Sobre la calidad de las propuestas

El sugeridor es **determinista y modesto**. Deriva el criterio del verbo y su objeto, y produce un texto correcto pero rígido. Es el respaldo que ANA-06 exige: la plataforma debe funcionar sin servicio externo.

La generación asistida se conecta sustituyendo un componente —`CriterionSuggester`— sin tocar nada más. Ahí es donde vendrá la calidad de redacción; lo que no cambiará es el límite: **la magnitud sigue sin proponerse**, venga de donde venga la propuesta.
