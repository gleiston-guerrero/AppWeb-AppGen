# Corrección de V5 y comprobación del dominio de correo

## 1. Migración V5 corregida

**El fallo:** la migración imponía la restricción nueva **antes** de migrar los datos, así que fallaba contra cualquier base que ya tuviera cuentas — precisamente el caso para el que existe.

**Cómo se me escapó:** verifiqué contra una base cuyos datos no contenían el caso. Solo estaba el administrador, que ya tenía el rol nuevo. Ahora está comprobada contra datos que reproducen el suyo: dos cuentas con `USER` que pasan correctamente a `FACILITATOR`.

La migración falló y revirtió, así que su base sigue en la versión 4. Basta con extraer y arrancar.

```powershell
cd C:\Repositorios\AppWeb-AppGen
Expand-Archive -Path "$HOME\Downloads\slcp-correo-y-v5.zip" -DestinationPath . -Force
Remove-Item LEEME.md
cd service
mvn clean spring-boot:run
```

## 2. Comprobación del dominio de correo

Al registrarse, se consulta si el dominio declara servidores de correo. Si no puede recibir mensajes, se rechaza con explicación.

```powershell
# Dominio inexistente: debe rechazarse
$malo = @{ username="prueba1"; email="alguien@dominio-que-no-existe-99887766.com"; fullName="Prueba"; password="una frase larga de acceso" } | ConvertTo-Json
Probar POST http://localhost:8081/api/v1/registrations $malo

# Dominio real: debe aceptarse
$bueno = @{ username="prueba2"; email="prueba2@uteq.edu.ec"; fullName="Prueba"; password="una frase larga de acceso" } | ConvertTo-Json
Probar POST http://localhost:8081/api/v1/registrations $bueno
```

### Lo que esta comprobación NO hace

Dice que el **dominio** puede recibir correo. **No dice que el buzón exista.**

Preguntárselo al servidor de destino es posible y prácticamente inútil: casi todos responden que sí a cualquier dirección para no revelar cuáles tienen, y los que responden con la verdad suelen bloquear a quien pregunta.

**La única comprobación concluyente es enviar un enlace y esperar a que alguien lo abra**, porque acredita a la vez que la dirección existe y que pertenece a quien dice. Eso es el flujo de invitación de SLCP-ADR-0005, y es el incremento siguiente.

### Ante fallo, no bloquea

Si no hay resolución de nombres, el registro sigue adelante. Una caída del servicio de nombres impediría registrarse a todo el mundo, lo que sería peor que admitir un dominio dudoso que el enlace de confirmación descartaría después.

## Verificado

- Migración V5 contra PostgreSQL 17 **con datos que reproducen el fallo**
- **49 pruebas Java en verde**, seis de ellas consultando DNS de verdad
