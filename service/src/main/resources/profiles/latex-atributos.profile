# =====================================================================
# Perfil de importacion: entorno LaTeX "atributos"
#
# Describe como esta escrito el documento, no como se lee. El lector es el
# mismo para cualquier perfil; lo que cambia es esta descripcion.
#
# Origen: ERS de MundiPets, Ingenieria de Requerimientos, UTEQ 2026-2027.
# =====================================================================

profile.id          = latex-atributos
profile.name        = LaTeX con entorno de atributos
profile.description = Documento LaTeX donde cada requisito ocupa un entorno propio y sus campos son filas de tabla. Es el formato de las ERS producidas con la plantilla IEEE de la asignatura.
profile.extensions  = .tex

example.begin
\subsubsection{RF-01 --- Registrar mascota}

\begin{atributos}{RF-01}{RF-01 --- Registrar mascota.}
    Identificador & RF-01 \\
    Nombre & Registrar mascota \\
    Descripcion & El sistema debera registrar la mascota con nombre, especie y raza. \\
    Actor & Propietario de mascota \\
    Prioridad (MoSCoW) & Must \\
    Criterio de verificacion & Con datos validos, registrar una mascota y comprobar que queda almacenada. \\
\end{atributos}
example.end

# Delimitadores del bloque que contiene un requisito.
block.begin    = \begin{atributos}
block.end      = \end{atributos}

# De donde sale el identificador cuando el bloque lo declara en su cabecera.
id.pattern     = \\begin\{atributos\}\{([^}]+)\}

# Estructura de cada fila: etiqueta, separador, valor, terminador.
row.separator  = &
row.terminator = \\

# ---------------------------------------------------------------------
# Correspondencia entre las etiquetas del documento y los campos canonicos.
#
# La comparacion prescinde de mayusculas y acentos, de modo que no hace falta
# una entrada por cada variante tipografica.
# ---------------------------------------------------------------------
field.Identificador              = id
field.Nombre                     = name
field.Descripcion                = description
field.Actor                      = actor
field.Origen (ID de evidencia)   = source
field.Entradas                   = inputs
field.Salidas                    = outputs
field.Precondiciones             = preconditions
field.Postcondiciones            = postconditions
field.Prioridad (MoSCoW)         = priority
field.Criterio de verificacion   = verification

# Requisitos no funcionales
field.Caracteristica ISO 25010   = qualityCharacteristic
field.Metrica                    = metric
field.Valor objetivo             = target

# ---------------------------------------------------------------------
# Historias de usuario
#
# 'Criterio de aceptacion' se corresponde con el mismo campo canonico que
# 'Criterio de verificacion': son el mismo concepto con distinto nombre segun
# el tipo de requisito. Tratarlos como campos distintos haria que las historias
# de usuario apareciesen como carentes de criterio cuando si lo traen.
# ---------------------------------------------------------------------
field.Historia (Connextra)       = statement
field.Criterio de aceptacion     = verification
field.RF asociado                = relatedRequirement
field.RF asociados               = relatedRequirement
field.Estimable (E)              = investEstimable
field.Independiente (I)          = investIndependent
field.Negociable (N)             = investNegotiable
field.Valiosa (V)                = investValuable
field.Pequena (S)                = investSmall
field.Verificable (T)            = investTestable

# ---------------------------------------------------------------------
# Campos cuya ausencia debe reportarse.
#
# Se exige lo minimo para que un requisito sea utilizable: que se pueda
# nombrar, entender y comprobar. Exigir mas convertiria el informe en ruido,
# porque casi ningun documento real trae todos los campos de todos.
# ---------------------------------------------------------------------
# ---------------------------------------------------------------------
# Casos de uso
# ---------------------------------------------------------------------
field.Actor principal            = actor
field.Actores secundarios        = secondaryActors
field.Flujo principal            = mainFlow
field.Flujos alternativos        = alternateFlows
field.Flujos de excepcion        = exceptionFlows

# ---------------------------------------------------------------------
# Requisitos de diseno y restricciones
# ---------------------------------------------------------------------
field.Justificacion              = rationale
field.Impacto                    = impact

# ---------------------------------------------------------------------
# Campos cuya ausencia debe reportarse.
#
# Se exige lo minimo para que un requisito sea utilizable: que se pueda
# nombrar, entender y comprobar.
#
# 'name' no se exige: las historias de usuario y los casos de uso se nombran en
# el titulo de su seccion y no repiten el campo dentro del bloque. Exigirlo
# produciria una carencia que no lo es.
# ---------------------------------------------------------------------
expected = id, description, verification
