#!/bin/sh
#
# SLCP - Oraculo del hook commit-msg
# Se escribe y ejecuta antes de dar por bueno el hook, conforme a TGT-08.
#
# Uso:  sh .githooks/commit-msg-test.sh

HOOK="$(dirname "$0")/commit-msg"
TMP=$(mktemp)
PASSED=0
FAILED=0

comprobar() {
	DESCRIPCION="$1"
	ESPERADO="$2"
	MENSAJE="$3"

	printf '%s' "$MENSAJE" > "$TMP"
	sh "$HOOK" "$TMP" > /dev/null 2>&1
	OBTENIDO=$?

	if [ "$OBTENIDO" -eq "$ESPERADO" ]; then
		PASSED=$((PASSED + 1))
	else
		FAILED=$((FAILED + 1))
		echo "  FALLO  $DESCRIPCION (esperado=$ESPERADO obtenido=$OBTENIDO)"
	fi
}

# --- Deben ser aceptados (codigo 0) ---
comprobar "identificador simple"            0 "feat(naming): validar nombres   [NAM-08]"
comprobar "dos identificadores"             0 "chore(repo): estructura inicial   [CON-01, CON-02]"
comprobar "rango de identificadores"        0 "docs(spec): familia VER   [VER-01..VER-23]"
comprobar "identificador en cuerpo"         0 "fix(naming): corregir caso

Detalle del cambio.
Requisito: [NAM-05]"
comprobar "commit de fusion"                0 "Merge branch 'feat/REQ-0017'"
comprobar "commit fixup"                    0 "fixup! feat(naming): validar nombres"
comprobar "familia PRO"                     0 "feat(core): propagacion transaccional   [PRO-03]"

# --- Deben ser rechazados (codigo 1) ---
comprobar "sin identificador"               1 "feat(naming): validar nombres propuestos"
comprobar "identificador sin corchetes"     1 "feat(naming): validar nombres NAM-08"
comprobar "familia inexistente"             1 "feat(naming): validar nombres   [XYZ-08]"
comprobar "identificador incompleto"        1 "feat(naming): validar nombres   [NAM-]"
comprobar "un solo digito"                  1 "feat(naming): validar nombres   [NAM-8]"
comprobar "mensaje vacio"                   1 ""

rm -f "$TMP"

echo "Comprobaciones superadas: $PASSED"
echo "Comprobaciones fallidas:  $FAILED"
if [ "$FAILED" -eq 0 ]; then
	echo "RESULTADO: VERDE"
	exit 0
else
	echo "RESULTADO: ROJO"
	exit 1
fi
