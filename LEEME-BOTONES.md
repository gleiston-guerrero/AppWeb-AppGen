# Revisión de los botones, y un defecto encontrado

```powershell
cd D:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-botones.zip" -DestinationPath . -Force
Remove-Item LEEME-BOTONES.md
cd web
npm start
```

Solo interfaz. El servicio no hace falta reiniciarlo.

## Qué revisé

No puedo pulsarlos, pero sí comprobar la cadena completa de cada uno: **botón → manejador → método del servicio → punto de acceso → autorización**. Si falta un eslabón, el botón no hace nada.

| Comprobación | Resultado |
|---|---|
| 57 acciones de plantilla tienen manejador | **Todas** |
| Cada llamada de componente a servicio existe | **Todas** |
| Cada ruta que pide la interfaz existe en el servidor | **Todas** |
| Rutas del servidor que nadie usa | **Una, y era un defecto** |

## El defecto: la sesión caducaba y nadie la renovaba

El servicio expone `PUT /api/v1/auth/sessions/current` para renovar la sesión. **La interfaz no lo llamaba nunca.**

El token de acceso dura quince minutos. Pasado ese rato, **todos los botones fallan a la vez** con «su sesión ha caducado». El síntoma —«no funciona nada»— no se parece a la causa, y es muy posible que sea lo que le estaba ocurriendo.

Ahora un interceptor detecta la caducidad, renueva y **repite la petición**: quien pulsó el botón no tiene que volver a pulsarlo.

## Tres decisiones del interceptor

**Renueva al recibir la negativa, no por temporizador.** Un temporizador mantendría vivas indefinidamente las sesiones de pestañas olvidadas.

**Las rutas de acceso quedan excluidas.** Si el inicio de sesión falla por contraseña incorrecta, intentar renovar sería un bucle — y un 401 de credenciales no es una caducidad.

**Si la renovación también falla, el error llega a quien pidió** y la sesión se descarta. No se reintenta indefinidamente.

Cuatro pruebas: renovación y reintento, fallo de la renovación, exclusión del inicio de sesión, y que un 403 no provoque renovación.

## Lo que la revisión no puede descartar

Que un botón haga lo que no debe. La cadena está completa y los roles cuadran, pero eso significa que **llegan al servidor**, no que el resultado sea el correcto. Eso solo lo dice usarlos.

Si alguno sigue sin responder, deme lo que aparezca en **F12 → Console** y en **Network** al pulsarlo: con el código y el mensaje lo localizo.
