# Registro de enmiendas al oráculo `naming-vectors.tsv`

Conforme a TGT-08 de SLCP-DOC-001, el oráculo se sella antes de la implementación y toda alteración posterior exige aprobación humana independiente y queda registrada como evento distinto. Este archivo es ese registro.

---

## A-001 — Inconsistencia interna en el tratamiento de palabras reservadas

- **Detectado:** 2026-08-06, en la primera ejecución del oráculo contra la implementación.
- **Estado:** `RATIFIED` — ratificada el 6 de agosto de 2026
- **Vector afectado:** V064
- **Evidencia:** ejecución con 50 vectores superados y 1 fallido (V066).

### Defecto

El oráculo aplicaba dos criterios distintos e incompatibles a la comprobación de colisión con palabras reservadas:

| Vector | Destino | Identificador generado | Esperado en el oráculo | Criterio implícito |
|---|---|---|---|---|
| V066 | Java, tipo | `Static` | `Static` (válido) | Sensible a mayúsculas |
| V064 | C#, miembro | `Namespace` | `ERROR:RESERVED_WORD` | Insensible a mayúsculas |

Ambos vectores no pueden ser correctos a la vez: o la comprobación distingue mayúsculas o no las distingue.

### Análisis

El criterio correcto no es uniforme, sino que depende de las reglas reales de cada destino:

- **Java y C#** tratan las palabras clave de forma sensible a mayúsculas. `class Static {}` es legal en Java, y `Namespace` es un nombre de propiedad perfectamente válido y frecuente en C#. Rechazarlos sería imponer una restricción que el compilador no impone y descartar identificadores legítimos de uso común.
- **PHP** trata las palabras reservadas de forma insensible a mayúsculas, de modo que `Class` colisiona igual que `class`.
- **SQL** pliega los identificadores sin entrecomillar, por lo que la comparación debe ser insensible a mayúsculas en todos los dialectos considerados.

El vector correcto es V066. El erróneo es V064.

### Enmienda propuesta

Modificar el valor esperado de V064 de `ERROR:RESERVED_WORD` a `Namespace`, y añadir vectores que fijen de forma explícita la sensibilidad a mayúsculas de cada destino, para que la inconsistencia no pueda reaparecer.

La implementación aplica en consecuencia comprobación sensible a mayúsculas en Java y C#, e insensible en PHP y en los dialectos SQL.

### Ratificación

Ratificada por la parte interesada con la regla general siguiente: *«dependiendo de cada lenguaje o tecnología, que las traduzca de manera que respete las buenas prácticas de ellas»*. La enmienda A-001 es la aplicación directa de esa regla, dado que Java y C# distinguen mayúsculas en sus palabras clave y PHP y los dialectos SQL no.

- Ratificada por: la parte interesada
- Fecha: 6 de agosto de 2026
- Consecuencia: el incremento 0.1 puede alcanzar línea base

---

## A-002 — No procede: el oráculo era correcto

- **Detectado:** 2026-08-06, primera ejecución del oráculo de `IdentifierAdvisor`.
- **Estado:** `NO_AMENDMENT` — no se modificó el oráculo.

Siete vectores fallaron. El análisis mostró que el error estaba en la implementación y no en el oráculo: se había añadido la forma plural como estrategia de alternativa. Es legal pero produce nombres falsos, porque una columna que guarda una sola orden no debe llamarse `orders`.

Se corrigió el código y se dejó el oráculo intacto. Se registra aquí porque el criterio aplicado —la legalidad no basta para proponer un nombre— es una decisión de diseño y no un detalle de implementación.
