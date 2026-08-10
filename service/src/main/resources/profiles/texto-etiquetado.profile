# =====================================================================
# Perfil de importacion: texto plano con bloques separados
#
# Cada requisito empieza con su identificador entre corchetes y termina en la
# primera linea vacia. Es el formato mas simple que admite la plataforma, y el
# indicado para quien no trabaja con LaTeX ni con Markdown.
# =====================================================================

profile.id          = texto-etiquetado
profile.name        = Texto plano con bloques separados
profile.description = Archivo de texto donde cada requisito empieza con su identificador entre corchetes y termina en una linea vacia. Los campos son lineas con etiqueta y dos puntos.
profile.extensions  = .txt, .text

block.begin    = [
block.end      = <BLANK>
id.pattern     = ^\[([^\]]+)\]

row.separator  = :
row.terminator =

field.Identificador            = id
field.ID                       = id
field.Nombre                   = name
field.Descripcion              = description
field.Enunciado                = description
field.Actor                    = actor
field.Prioridad                = priority
field.Criterio de verificacion = verification
field.Criterio                 = verification
field.Metrica                  = metric
field.Valor objetivo           = target

expected = id, description, verification

example.begin
[RF-01]
Nombre: Registrar mascota
Descripcion: El sistema debera registrar la mascota con nombre, especie y raza.
Actor: Propietario de mascota
Criterio de verificacion: Con datos validos, registrar una mascota y comprobar que queda almacenada.

[RF-02]
Nombre: Consultar historial medico
Descripcion: El sistema debera mostrar el historial medico de una mascota registrada.
Criterio de verificacion: Consultar el historial y comprobar que aparecen todos los antecedentes.
example.end
