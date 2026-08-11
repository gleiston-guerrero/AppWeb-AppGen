# Corrección de requisitos con opciones de redacción

> Verificado aquí: **182 pruebas Java** en verde, interfaz con tipos estrictos y `strictTemplates`.

```powershell
cd C:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-correccion.zip" -DestinationPath . -Force
Remove-Item LEEME-CORRECCION.md
cd service
mvn clean spring-boot:run
```

## Lo nuevo: la plataforma propone cómo reescribir el enunciado

Hasta ahora solo proponía criterios de verificación. Ahora también **redacciones alternativas del enunciado**, derivadas de sus defectos:

| Defecto | Qué propone |
|---|---|
| «debería» / «podrá» | Lo pasa a «deberá», que es la forma que obliga |
| «deberá ser capaz de» | Enuncia la acción en lugar de la capacidad |
| Voz pasiva | «Se requiere que sea notificado X» → «El sistema deberá notificar a X» |
| Sin sujeto | Antepone «El sistema», **retirando el pronombre del verbo** |
| Término sin magnitud | Lo sustituye por `[indique la magnitud]`, nunca por una cifra |
| Dos obligaciones | Divide, y el fundamento trae la segunda para darla de alta aparte |
| Condición al final | La lleva al principio, según la sintaxis de la norma |

## Cómo se usa

En un requisito con problemas, despliéguelo y pulse **Corregir en el formulario**. El requisito sube al formulario de arriba, que cambia a **«Corregir REQ-000N»** y cuyo botón pasa a decir **«Actualizar requisito»**.

Arriba aparece la **paginación de opciones**: flechas, indicador «Opción 2 de 3» y puntos para saltar. La posición cero es siempre **el texto original** — quien revisa debe ver primero lo que hay y después lo que se le propone.

Cada opción trae su fundamento. **Puede editar el texto de cualquiera antes de actualizar**: en cuanto lo toca, deja de constar como texto de la plataforma y pasa a constar como suyo.

## Si no puede inferir

Cuando la plataforma no logra derivar ninguna redacción, lo dice: *«no ha podido derivar ninguna redacción alternativa; corríjalo usted a la vista de los hallazgos»*. El formulario queda igualmente cargado para que lo escriba.

## Sobre los botones que no funcionaban

Retiré **«Guardar criterio»**: su función la absorbe el formulario, que es donde ahora se corrige todo. Tener dos sitios para editar lo mismo era parte del problema.

**«Marcar como revisado»** sigue, ahora con indicador de progreso y con mensajes de error mucho más claros: un 403 ya no aparece como «error 403» sino explicando que editar corresponde al miembro del equipo y aprobar al propietario.

**Si el botón seguía sin responder, lo más probable es que le faltara el rol.** Compruébelo: en *Mi trabajo*, su proyecto debe mostrarle la etiqueta **Miembro del equipo**. Si solo dice *Facilitador*, incorpórese a sí mismo como miembro del equipo desde *Ver equipo*.

Y si aun así falla, deme lo que aparezca en **F12 → Console** y en **Network** al pulsar: con el código y el mensaje lo localizo.

## Tres defectos que las pruebas destaparon

**«Se requiere que sea notificado»** producía *«deberá notificado»* — el participio no se volvía infinitivo y la frase quedaba sin verbo principal. Ahora solo se transforma la terminación `-ado`, que da `-ar` sin ambigüedad; `-ido` puede venir de `-er` o de `-ir`, y acertar por azar sería peor que no proponer.

**«Deberá almacenarse el historial»** daba *«El sistema deberá almacenarse el historial»* — que dice que el sistema **se almacena a sí mismo**. Cambiaba el significado del requisito. Ahora se retira el pronombre.

**«Cuando un animal salga»** quedaba en *«Cuando n animal salga»*: al reordenar, avanzaba la longitud de la marca con espacios sobre un texto donde ya se habían recortado, y se comía una letra.
