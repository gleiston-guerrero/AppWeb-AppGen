# Planificación completa — Granja Inteligente

Entregables, componentes, tareas, actividades y recursos para los veinte requisitos aprobados.

**Esfuerzo** en puntos, con la escala de Fibonacci que ya venimos usando. **Recursos** distingue personas de medios materiales, porque se consiguen de forma distinta y con distinta antelación.

---

## Resumen

| Entregable | Requisitos | Componentes | Tareas | Esfuerzo |
|---|---|---|---|---|
| ENT-01 · Catálogo de la explotación | RF-01, RF-02, RF-15 | 3 | 10 | 47 |
| ENT-02 · Ingesta y vigilancia | RF-03, RF-07, RNF-01 | 3 | 9 | 55 |
| ENT-03 · Gobierno del riego | RF-04, RF-05, RF-16, RNF-02 | 4 | 12 | 68 |
| ENT-04 · Consulta y explotación | RF-06, RF-08, RF-17 | 3 | 9 | 42 |
| ENT-05 · Autonomía en campo | RNF-03 | 2 | 5 | 34 |
| ENT-06 · Acceso y trazabilidad | RNF-1, RF-T2 | 2 | 7 | 39 |
| ENT-07 · Continuidad del servicio | RNF-3, RNF-T4 | 2 | 6 | 31 |
| ENT-08 · Accesibilidad y datos del usuario | RNF-T5, RF-T6 | 2 | 6 | 26 |

**Total: 8 entregables · 21 componentes · 64 tareas · 342 puntos.**

---

## ENT-01 · Catálogo de la explotación

**Requisitos:** REQ-0001 *(registrar parcela)* · REQ-0002 *(alta de sensor)* · REQ-0017 *(calibración)*

**Aceptación:** registrar una parcela con cuatro vértices y comprobar que la superficie calculada coincide con la declarada; dar de alta un sensor sobre ella; registrar una calibración y comprobar que las lecturas posteriores incorporan la desviación.

### C-01.1 · Modelo de parcela

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Esquema y migración | Diseñar tablas · Escribir migración · Probarla contra PostgreSQL | 3 |
| Entidad y repositorio | Implementar · Escribir pruebas | 3 |
| Cálculo de superficie desde vértices | Estudiar la fórmula para polígonos · Implementar · Probar con parcelas reales | 5 |

> El cálculo de superficie es la tarea que decide la aceptación: el criterio exige que lo calculado coincida con lo declarado, y con vértices en coordenadas geográficas eso no es trivial.

### C-01.2 · Alta y consulta de parcelas

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Servicio y punto de acceso | Implementar · Autorización por rol · Pruebas | 5 |
| Pantalla de registro | Diseñar formulario · Implementar · Captura de vértices en mapa · Pruebas | 8 |
| Pantalla de listado | Implementar · Filtros por cultivo · Pruebas | 5 |

### C-01.3 · Sensores y su calibración

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Modelo de sensor | Esquema · Entidad · Ubicación dentro de la parcela | 3 |
| Alta con periodo de muestreo | Servicio · Punto de acceso · Pruebas | 5 |
| Registro de calibración | Modelo con fecha y desviación · Aplicación a lecturas posteriores · Pruebas | 5 |
| Pantalla de sensores | Implementar · Ver calibraciones · Pruebas | 5 |

**Recursos · personas:** 1 desarrollador de servicio, 1 de interfaz.
**Recursos · medios:** entorno de desarrollo, PostgreSQL, biblioteca de mapas, **datos reales de parcelas de al menos una explotación** para probar el cálculo.

---

## ENT-02 · Ingesta y vigilancia de lecturas

**Requisitos:** REQ-0003 *(almacenar lecturas)* · REQ-0007 *(alerta por silencio)* · REQ-0009 *(retención ante corte)*

**Aceptación:** enviar una lectura y comprobar que queda con todos sus datos; con muestreo de 15 minutos, callar 31 minutos y comprobar que llega la alerta; interrumpir la comunicación 24 horas y comprobar que al restablecerla llegan todas sin duplicarse.

### C-02.1 · Almacén de lecturas

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Esquema con índice temporal | Diseñar · Migrar · Medir consulta con un millón de filas | 5 |
| Punto de acceso de ingesta | Implementar · Autenticación del dispositivo · Pruebas | 5 |
| Rechazo de sensores no dados de alta | Implementar · Pruebas | 3 |

### C-02.2 · Vigilancia de sensores

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Detección de sensores en silencio | Decidir cómo se programa la comprobación · Implementar · Pruebas | 5 |
| Emisión y envío de la alerta | Implementar · Plantilla del aviso · Pruebas | 5 |
| Evitar la repetición del aviso | Decidir cada cuánto se reitera · Implementar · Pruebas | 3 |

> La última no está en el requisito y hace falta: sin ella, un sensor averiado genera un aviso por periodo de muestreo y el responsable deja de leerlos.

### C-02.3 · Retención en el dispositivo de campo

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Almacén local del dispositivo | Elegir el soporte según memoria disponible · Implementar · Probar con 24 h de lecturas | 8 |
| Reenvío al restablecer | Implementar · Control de orden · Pruebas | 8 |
| **Descarte de duplicados** | Decidir qué identifica una lectura · Implementar · Probar el ciclo completo | 8 |
| Ensayo de corte de 24 horas | Montar el banco · Ejecutar · Documentar el resultado | 5 |

**Recursos · personas:** 1 desarrollador de servicio, 1 de firmware.
**Recursos · medios:** **dos dispositivos de campo con sus sensores**, pasarela, banco de pruebas con corte de comunicación controlable, generador de lecturas sintéticas.

> El ensayo de 24 horas ocupa un día de calendario aunque sean 5 puntos de esfuerzo. Conviene empezarlo pronto, no al final.

---

## ENT-03 · Gobierno del riego

**Requisitos:** REQ-0004 *(abrir por umbral)* · REQ-0005 *(cerrar por dosis)* · REQ-0018 *(programar franja horaria)* · REQ-0010 *(latencia de la orden)*

**Aceptación:** con umbral del 30 %, lecturas que promedien 25 % abren la válvula; con dosis de 200 litros, cierra entre 195 y 205; programada una franja de 2 a 5, no se abre fuera de ella; y en 100 activaciones, al menos 95 emiten la orden en menos de 5 segundos.

### C-03.1 · Cultivos, umbrales y dosis

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Modelo de cultivo | Esquema · Umbral, dosis y ciclo · Pruebas | 3 |
| Asignación de cultivo a parcela | Servicio · Pruebas | 3 |
| Pantalla de cultivos | Implementar · Pruebas | 5 |

### C-03.2 · Decisión de apertura

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Cálculo de la humedad media | Decidir cómo promediar lecturas irregulares · Implementar · Probar con datos reales | 5 |
| Comparación con el umbral | Implementar · Pruebas | 3 |
| Registro de la activación | Implementar · Pruebas | 3 |

### C-03.3 · Decisión de cierre y programación

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Lectura del caudalímetro | Integrar · Probar con caudal conocido | 5 |
| Volumen acumulado por riego | Implementar · Pruebas | 5 |
| Corte al alcanzar la dosis | Implementar · Probar la tolerancia de ±5 litros | 5 |
| Franjas horarias de riego | Modelo · Respeto del umbral dentro de la franja · Pantalla · Pruebas | 8 |

### C-03.4 · Emisión de la orden

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Interfaz con la válvula | Integrar el protocolo · Probar sobre válvula real | 8 |
| Reintento ante fallo | Decidir cuántos y con qué espera · Implementar · Pruebas | 5 |
| **Medición de latencia** | Montar el banco de 100 activaciones · Medir · Documentar | 5 |
| Registro de órdenes emitidas | Implementar · Pruebas | 3 |

**Recursos · personas:** 1 desarrollador de servicio, 1 de firmware, **1 agrónomo para fijar umbrales y dosis por cultivo**.
**Recursos · medios:** válvula y caudalímetro de ensayo, banco de latencia, dispositivo de campo.

> El agrónomo es un recurso de verdad, no un adorno: los umbrales y las dosis son decisiones agronómicas, y ningún desarrollador puede fijarlas. Si no está disponible, este entregable se detiene.

---

## ENT-04 · Consulta y explotación de datos

**Requisitos:** REQ-0006 *(consumo por parcela)* · REQ-0008 *(exportar historial)* · REQ-0019 *(balance hídrico)*

**Aceptación:** con tres riegos de volumen conocido, el consumo del periodo coincide con su suma; el archivo exportado trae una fila por riego; y el balance coincide con la diferencia esperada entre aporte, lluvia y evapotranspiración.

### C-04.1 · Consumo de agua

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Acumulado por parcela y periodo | Implementar · Pruebas | 5 |
| Punto de acceso y pantalla | Implementar · Pruebas | 5 |

### C-04.2 · Balance hídrico

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Ingesta de lluvia registrada | Decidir la fuente · Integrar · Pruebas | 5 |
| **Estimación de evapotranspiración** | Elegir el método y justificarlo con el agrónomo · Implementar · Validar | 8 |
| Presentación del balance | Implementar · Pruebas | 5 |

### C-04.3 · Exportación

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| **Definir las columnas del archivo** | Acordarlas con el responsable · Documentarlas | 2 |
| Generación del archivo | Implementar · Escapado de comas y comillas · Pruebas | 5 |
| Descarga desde la aplicación | Implementar · Pruebas | 3 |
| Exportación de grandes históricos | Probar con un año de riegos · Ajustar si tarda | 5 |

**Recursos · personas:** 1 desarrollador de servicio, 1 de interfaz, **agrónomo** para el método de evapotranspiración.
**Recursos · medios:** **fuente de datos meteorológicos** —estación propia o servicio contratado—, que hay que decidir y quizá pagar.

> Sin la fuente meteorológica, REQ-0019 no puede realizarse. Conviene resolverlo antes de empezar el entregable, no dentro de él.

---

## ENT-05 · Autonomía en campo

**Requisitos:** REQ-0020 *(180 días sin sustituir batería)*

**Aceptación:** someter un dispositivo a muestreo cada quince minutos y comprobar que supera los ciento ochenta días.

### C-05.1 · Consumo del dispositivo

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Medición del consumo por operación | Instrumentar · Medir muestreo, transmisión y reposo | 8 |
| Reducción del consumo en reposo | Ajustar el ciclo de sueño · Medir de nuevo | 8 |
| Ajuste de la cadencia de transmisión | Decidir cuántas lecturas por envío · Implementar · Medir | 5 |

### C-05.2 · Verificación de la autonomía

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| **Ensayo acelerado de autonomía** | Diseñar el ensayo · Ejecutarlo · Extrapolar y documentar | 8 |
| Aviso de batería baja | Implementar · Pruebas | 5 |

> **Este entregable no puede aceptarse en el plazo del proyecto por medición directa**: 180 días son seis meses. El ensayo acelerado —medir consumo real y extrapolar— es lo único viable, y conviene que el propietario acepte ese método por escrito antes de empezar, no al entregar.

**Recursos · personas:** 1 de firmware, **1 con instrumental de medida eléctrica**.
**Recursos · medios:** analizador de consumo, cámara climática, **tres dispositivos idénticos** para no depender de una sola medición.

---

## ENT-06 · Acceso y trazabilidad

**Requisitos:** REQ-0029 *(segundo factor)* · REQ-0031 *(traza de auditoría)*

**Aceptación:** iniciar sesión desde un dispositivo nuevo y comprobar que se pide el segundo factor; modificar un dato y comprobar que la traza recoge autor, fecha y operación.

### C-06.1 · Segundo factor de verificación

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| **Decidir el segundo factor** | Comparar aplicación de códigos, SMS y correo · Decidir con el responsable · Documentar | 3 |
| Reconocimiento de dispositivo | Decidir qué lo identifica · Implementar · Pruebas | 8 |
| Verificación del segundo factor | Implementar · Pruebas | 8 |
| Recuperación si se pierde el factor | Diseñar el procedimiento · Implementar · Pruebas | 5 |

> La última no está en el requisito y es imprescindible: sin ella, quien pierda el teléfono pierde la cuenta.

### C-06.2 · Traza de auditoría

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Registro automático de las modificaciones | Implementar en el punto común · Pruebas | 8 |
| Consulta de la traza | Pantalla · Filtros por autor y fecha · Pruebas | 5 |
| **Retención e inalterabilidad** | Decidir cuánto se conserva · Impedir su modificación · Pruebas | 2 |

**Recursos · personas:** 1 desarrollador de servicio, 1 de interfaz.
**Recursos · medios:** **servicio de envío del segundo factor** si se elige SMS, que tiene coste por mensaje.

---

## ENT-07 · Continuidad del servicio

**Requisitos:** REQ-0030 *(copia diaria, 30 días)* · REQ-0032 *(disponibilidad en horario)*

**Aceptación:** comprobar que existen las copias de los últimos treinta días y **restaurar una de ellas correctamente**; medir la disponibilidad durante un mes de campaña.

### C-07.1 · Copias de seguridad

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Copia diaria automatizada | Implementar · Programar · Verificar la primera semana | 5 |
| Retención de treinta días | Implementar el purgado · Pruebas | 3 |
| **Ensayo de restauración** | Restaurar en un entorno limpio · Comprobar la integridad · Documentar el procedimiento | 8 |

> Una copia que nunca se ha restaurado no es una copia: es un archivo del que se supone algo. El criterio de aceptación exige la restauración, y por eso es tarea propia.

### C-07.2 · Disponibilidad

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| **Definir el horario de operación** | Acordarlo con el responsable · Documentarlo | 2 |
| Vigilancia del servicio | Implementar la comprobación · Avisos · Pruebas | 8 |
| Medición y registro de la disponibilidad | Implementar · Informe mensual | 5 |

**Recursos · personas:** 1 de sistemas, 1 desarrollador.
**Recursos · medios:** **almacenamiento para treinta días de copias**, entorno limpio para el ensayo de restauración, servicio de vigilancia externa.

---

## ENT-08 · Accesibilidad y datos del usuario

**Requisitos:** REQ-0033 *(teclado y lector de pantalla)* · REQ-0034 *(exportar datos personales)*

**Aceptación:** recorrer una pantalla completa solo con teclado y comprobar que toda acción es alcanzable; solicitar la exportación y comprobar que el archivo contiene los datos del usuario.

### C-08.1 · Manejo por teclado y lector

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| Recorrido por teclado en todas las pantallas | Revisar cada una · Corregir el orden de foco · Pruebas | 8 |
| Etiquetado para lector de pantalla | Revisar · Corregir · Probar con un lector real | 8 |
| **Prueba con una persona usuaria de lector** | Preparar la sesión · Observar · Corregir lo que aparezca | 3 |

> La última cambia el resultado. Cumplir la norma en el marcado y ser manejable con un lector no son lo mismo, y solo lo segundo es lo que el requisito pide.

### C-08.2 · Exportación de datos personales

| Tarea | Actividades | Esfuerzo |
|---|---|---|
| **Determinar qué datos son personales** | Inventariarlos · Acordarlo con el responsable · Documentar | 2 |
| Generación del archivo | Implementar · Formato legible por máquina · Pruebas | 3 |
| Solicitud y entrega | Pantalla · Aviso al estar listo · Pruebas | 2 |

**Recursos · personas:** 1 de interfaz, **1 persona usuaria de lector de pantalla** para la prueba.
**Recursos · medios:** lector de pantalla instalado, dispositivo de prueba.

---

## Recursos que hay que conseguir antes, no durante

| Recurso | Para | Cuándo hay que tenerlo |
|---|---|---|
| **Agrónomo** | Umbrales, dosis, evapotranspiración | Antes de ENT-03 |
| **Dispositivos de campo y sensores** | ENT-02, ENT-05 | Antes de ENT-02 |
| **Válvula y caudalímetro de ensayo** | ENT-03 | Antes de C-03.3 |
| **Fuente de datos meteorológicos** | Balance hídrico | Antes de C-04.2 |
| **Analizador de consumo y cámara climática** | ENT-05 | Antes de C-05.1 |
| **Datos reales de parcelas** | Cálculo de superficie | Antes de C-01.1 |
| **Persona usuaria de lector de pantalla** | ENT-08 | Antes de C-08.1 |

Son los que detienen el trabajo si faltan. Los demás —entornos, bases de datos, bibliotecas— se resuelven el mismo día.

---

## Orden y dependencias

ENT-01 abre todo: sin parcelas y sensores no hay nada que medir ni que regar.

ENT-02 depende de ENT-01. ENT-03 depende de ENT-02 —sin lecturas no hay con qué comparar el umbral—. ENT-04 depende de ENT-03 para tener riegos que consultar.

**ENT-05, ENT-06, ENT-07 y ENT-08 son independientes** y pueden ir en paralelo desde el principio. Conviene empezar ENT-05 pronto pese a ser el menos urgente: su ensayo de autonomía es el que más calendario consume.

## Tres tareas que suelen olvidarse y aquí van con dueño

**«Decidir el segundo factor»**, **«definir el horario de operación»** y **«determinar qué datos son personales»** valen 2 o 3 puntos cada una y no son de programación: son conversaciones con el responsable. Si no se asignan, no ocurren, y su entregable se queda sin poder aceptarse por una decisión de quince minutos que nadie tomó.
