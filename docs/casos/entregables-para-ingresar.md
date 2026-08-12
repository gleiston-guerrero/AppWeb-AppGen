# Los ocho entregables, listos para el formulario

Cada bloque trae los cuatro campos que pide **Entregables → Planificar un entregable**, en su orden.

Los requisitos se marcan en la lista de la propia pantalla. Aquí van sus identificadores y su nombre, para que los reconozca sin dudar.

---

## ENT-01

**Nombre**
```
Catálogo de la explotación
```

**Qué es y qué incluye**
```
Registro de las parcelas de cultivo, de los sensores instalados en ellas y de sus calibraciones. Incluye el alta y la consulta de parcelas con su superficie calculada a partir de los vértices, el alta de sensores con su tipo de medida, periodo de muestreo y ubicación dentro de la parcela, y el registro de calibraciones con su fecha y su desviación. Es la base de los demás entregables: sin parcelas no hay dónde instalar sensores, y sin sensores no hay lecturas.
```

**Qué debe comprobar quien lo recibe para darlo por bueno**
```
Registrar una parcela con cuatro vértices y comprobar que la superficie calculada coincide con la declarada. Dar de alta un sensor sobre esa parcela y comprobar que queda asociado con su periodo de muestreo y su ubicación. Registrar una calibración con su desviación y comprobar que las lecturas posteriores de ese sensor la incorporan.
```

**Requisitos que realiza**

| | |
|---|---|
| REQ-0001 | Registrar una parcela de cultivo |
| REQ-0002 | Dar de alta un sensor en una parcela |
| REQ-0017 | Registrar la calibración de un sensor |

---

## ENT-02

**Nombre**
```
Ingesta y vigilancia de lecturas
```

**Qué es y qué incluye**
```
Recepción, almacenamiento y vigilancia de las lecturas que envían los sensores. Incluye el punto de acceso de ingesta con rechazo de sensores no dados de alta, el almacén con índice temporal, la detección de sensores que dejan de comunicar con su aviso al responsable, y la retención de lecturas en el dispositivo de campo mientras no haya comunicación, con su reenvío sin duplicados al restablecerse.
```

**Qué debe comprobar quien lo recibe para darlo por bueno**
```
Enviar una lectura desde un sensor dado de alta y comprobar que queda almacenada con su sensor, tipo de medida, valor, unidad y marca de tiempo. Con un periodo de muestreo de quince minutos, dejar de enviar lecturas durante treinta y un minutos y comprobar que llega la notificación identificando el sensor. Interrumpir la comunicación durante veinticuatro horas generando lecturas, restablecerla, y comprobar que todas llegan y que ninguna queda duplicada.
```

**Requisitos que realiza**

| | |
|---|---|
| REQ-0003 | Almacenar cada lectura recibida de un sensor |
| REQ-0007 | Notificar cuando un sensor deje de enviar lecturas |
| REQ-0009 | Conservar en el dispositivo lo no transmitido |

---

## ENT-03

**Nombre**
```
Gobierno del riego
```

**Qué es y qué incluye**
```
Decisión y ejecución del riego de cada parcela. Incluye el catálogo de cultivos con su umbral de humedad y su dosis, la apertura de la válvula cuando la humedad media desciende del umbral, el cierre cuando el volumen aportado alcanza la dosis, la programación del riego en franjas horarias respetando el umbral, y la emisión de la orden a la válvula con su reintento ante fallo y su latencia medida.
```

**Qué debe comprobar quien lo recibe para darlo por bueno**
```
Con un umbral del treinta por ciento, enviar lecturas que promedien veinticinco por ciento y comprobar que se emite la orden de apertura y queda registrada. Con una dosis de doscientos litros, ejecutar un riego y comprobar que la válvula se cierra entre ciento noventa y cinco y doscientos cinco litros aportados. Programar el riego entre las dos y las cinco y comprobar que no se abre la válvula fuera de esa franja. Provocar cien activaciones y comprobar que al menos noventa y cinco emiten la orden en menos de cinco segundos.
```

**Requisitos que realiza**

| | |
|---|---|
| REQ-0004 | Activar el riego por umbral de humedad |
| REQ-0005 | Cerrar el riego por dosis alcanzada |
| REQ-0018 | Programar el riego en una franja horaria |
| REQ-0010 | Emitir la orden dentro del plazo establecido |

---

## ENT-04

**Nombre**
```
Consulta y explotación de datos
```

**Qué es y qué incluye**
```
Lo que el responsable de la explotación consulta y se lleva. Incluye el consumo de agua acumulado por parcela en el periodo que indique, el balance entre el agua aportada por riego, la lluvia registrada y la evapotranspiración estimada, y la exportación del historial de riego en un archivo de valores separados por comas.
```

**Qué debe comprobar quien lo recibe para darlo por bueno**
```
Con tres riegos registrados de volumen conocido, consultar el periodo que los contiene y comprobar que el consumo total coincide con su suma. Con riegos y lluvia conocidos, consultar el balance de una parcela y comprobar que el resultado corresponde a la diferencia esperada. Exportar el historial de riego y comprobar que el archivo trae una fila por riego, con las columnas acordadas y con las comas del texto correctamente escapadas.
```

**Requisitos que realiza**

| | |
|---|---|
| REQ-0006 | Mostrar el consumo de agua por parcela |
| REQ-0008 | Exportar el historial de riego |
| REQ-0019 | Mostrar el balance hídrico de la parcela |

---

## ENT-05

**Nombre**
```
Autonomía de los dispositivos de campo
```

**Qué es y qué incluye**
```
Comportamiento energético de los dispositivos instalados en la parcela. Incluye la medición del consumo por operación, la reducción del consumo en reposo, el ajuste de la cadencia de transmisión, el aviso de batería baja y el ensayo que acredita la autonomía exigida.
```

**Qué debe comprobar quien lo recibe para darlo por bueno**
```
Medir el consumo real del dispositivo en muestreo, transmisión y reposo, y comprobar que la autonomía extrapolada a partir de esas medidas supera los ciento ochenta días con muestreo cada quince minutos. Comprobar que el dispositivo avisa antes de agotarse. El plazo exigido excede la duración del proyecto, de modo que la aceptación se hace sobre el ensayo acelerado y no sobre una medición directa de seis meses; quien recibe debe aceptar expresamente ese método antes de que empiece el trabajo.
```

**Requisitos que realiza**

| | |
|---|---|
| REQ-0020 | Operar los sensores sin sustitución de batería |

---

## ENT-06

**Nombre**
```
Acceso y trazabilidad
```

**Qué es y qué incluye**
```
Seguridad del acceso a la plataforma y constancia de lo que se hace en ella. Incluye el segundo factor de verificación al entrar desde un dispositivo no reconocido, el reconocimiento de dispositivos, el procedimiento de recuperación si se pierde el factor, y la traza de auditoría de toda operación que modifique datos, con su consulta y su retención.
```

**Qué debe comprobar quien lo recibe para darlo por bueno**
```
Iniciar sesión desde un dispositivo nuevo y comprobar que se solicita el segundo factor, y desde uno ya reconocido y comprobar que no se solicita. Simular la pérdida del segundo factor y comprobar que el procedimiento de recuperación devuelve el acceso sin abrir un camino más débil. Modificar un dato y comprobar que la traza recoge el autor, la fecha y la operación realizada, y que esa traza no puede alterarse.
```

**Requisitos que realiza**

| | |
|---|---|
| REQ-0029 | Exigir un segundo factor desde dispositivo no reconocido |
| REQ-0031 | Registrar la traza de auditoría |

---

## ENT-07

**Nombre**
```
Continuidad del servicio
```

**Qué es y qué incluye**
```
Que el servicio esté cuando hace falta y que sus datos se puedan recuperar. Incluye la copia de seguridad completa diaria con retención de treinta días, el ensayo de restauración, la vigilancia del servicio durante el horario de operación y la medición de su disponibilidad.
```

**Qué debe comprobar quien lo recibe para darlo por bueno**
```
Comprobar que existen las copias de los últimos treinta días y restaurar una de ellas en un entorno limpio, verificando que los datos restaurados están completos. Comprobar que las copias anteriores a treinta días se han retirado. Medir la disponibilidad durante un mes de campaña y comprobar que cumple lo acordado dentro del horario de operación establecido.
```

**Requisitos que realiza**

| | |
|---|---|
| REQ-0030 | Realizar copia de seguridad diaria |
| REQ-0032 | Mantenerse disponible en el horario de operación |

---

## ENT-08

**Nombre**
```
Accesibilidad y datos del usuario
```

**Qué es y qué incluye**
```
Que la aplicación pueda manejarse sin ratón ni vista y que el usuario pueda llevarse sus datos. Incluye el recorrido completo por teclado de todas las pantallas con orden de foco correcto, el etiquetado para lector de pantalla, y la exportación de los datos personales del usuario que lo solicite en un formato legible por máquina.
```

**Qué debe comprobar quien lo recibe para darlo por bueno**
```
Recorrer una pantalla completa usando solo el teclado y comprobar que toda acción es alcanzable y que el orden de foco sigue el de lectura. Recorrerla con un lector de pantalla y comprobar que cada control se anuncia con un nombre que dice lo que hace. Solicitar la exportación de datos personales y comprobar que el archivo entregado contiene los datos del usuario y ninguno de otro.
```

**Requisitos que realiza**

| | |
|---|---|
| REQ-0033 | Permitir el manejo por teclado y lector de pantalla |
| REQ-0034 | Exportar los datos personales del usuario |

---

## Antes de empezar

**Los requisitos deben estar aprobados**, y los veinte lo están.

**Créelos en este orden**: ENT-01 primero, porque todo lo demás lo necesita, y después ENT-02 y ENT-03, que dependen de él. ENT-04 al final de esa cadena. Los cuatro últimos son independientes y pueden crearse cuando quiera.

**Dos criterios de aceptación traen una condición** que conviene resolver antes de crear el entregable, no al entregarlo:

- **ENT-05** se acepta sobre un ensayo acelerado, porque 180 días exceden el proyecto. Que el propietario acepte ese método por escrito.
- **ENT-04** menciona «las columnas acordadas» de la exportación: acuérdelas antes, o ese criterio no podrá comprobarse.
