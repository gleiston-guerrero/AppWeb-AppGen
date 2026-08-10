# =====================================================================
# Perfil de importacion: Markdown con campos etiquetados
#
# Cada requisito empieza en un encabezado de segundo nivel y termina donde
# empieza el siguiente. Los campos son lineas de la forma "Etiqueta: valor",
# con o sin negrita.
# =====================================================================

profile.id          = markdown-campos
profile.name        = Markdown con campos etiquetados
profile.description = Documento Markdown donde cada requisito empieza en un encabezado de segundo nivel y sus campos son lineas con etiqueta y dos puntos. No hace falta marca de cierre: cada requisito termina donde empieza el siguiente.
profile.extensions  = .md, .markdown, .txt

block.begin    = ## 
block.end      = <NEXT>
id.pattern     = ^##\s+([A-Za-z]+-[0-9]+)
# El nombre va en el propio encabezado, tras el identificador y un guion.
name.pattern   = ^##\s+[A-Za-z]+-[0-9]+\s*[\u2014\u2013-]+\s*(.+?)\s*$

row.separator  = :
row.terminator =

field.Identificador            = id
field.ID                       = id
field.Nombre                   = name
field.Titulo                   = name
field.Descripcion              = description
field.Enunciado                = description
field.Actor                    = actor
field.Entradas                 = inputs
field.Salidas                  = outputs
field.Precondiciones           = preconditions
field.Postcondiciones          = postconditions
field.Prioridad                = priority
field.Criterio de verificacion = verification
field.Criterio de aceptacion   = verification
field.Metrica                  = metric
field.Valor objetivo           = target

expected = id, description, verification

example.begin
## RF-01 — Registrar mascota

**Descripcion:** El sistema debera registrar la mascota con nombre, especie y raza.
**Actor:** Propietario de mascota
**Prioridad:** Must
**Criterio de verificacion:** Con datos validos, registrar una mascota y comprobar que queda almacenada.

## RF-02 — Consultar historial medico

**Descripcion:** El sistema debera mostrar el historial medico de una mascota registrada.
**Criterio de verificacion:** Consultar el historial de una mascota con antecedentes y comprobar que aparecen todos.
example.end
