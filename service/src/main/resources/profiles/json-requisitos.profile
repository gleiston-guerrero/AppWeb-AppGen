# =====================================================================
# Perfil de importacion: JSON
#
# Los formatos con estructura propia no declaran delimitadores: su estructura
# ya dice donde empieza y acaba cada requisito. Lo que sigue siendo dato es la
# correspondencia entre las claves del documento y los campos canonicos.
# =====================================================================

profile.id          = json-requisitos
profile.name        = JSON con lista de requisitos
profile.description = Archivo JSON con un arreglo de requisitos, en la raiz o bajo la clave "requirements". Cada requisito es un objeto cuyas claves son sus campos. Los valores multiples pueden ir como arreglo.
profile.extensions  = .json
profile.reader      = json
# Clave que contiene la lista de requisitos.
json.list           = requirements

field.id                       = id
field.identificador            = id
field.name                     = name
field.nombre                   = name
field.title                    = name
field.description              = description
field.descripcion              = description
field.statement                = description
field.enunciado                = description
field.actor                    = actor
field.actors                   = actor
field.inputs                   = inputs
field.entradas                 = inputs
field.outputs                  = outputs
field.salidas                  = outputs
field.preconditions            = preconditions
field.precondiciones           = preconditions
field.postconditions           = postconditions
field.postcondiciones          = postconditions
field.priority                 = priority
field.prioridad                = priority
field.verification             = verification
field.criterio de verificacion = verification
field.acceptanceCriteria       = verification
field.criterio de aceptacion   = verification
field.metric                   = metric
field.metrica                  = metric
field.target                   = target
field.valor objetivo           = target
field.kind                     = kind
field.tipo                     = kind

expected = id, description, verification

example.begin
{
  "project": "Granja Inteligente",
  "requirements": [
    {
      "id": "RF-01",
      "name": "Registrar parcela de cultivo",
      "description": "El sistema debera registrar una parcela con su superficie, tipo de suelo y cultivo sembrado.",
      "actor": "Responsable de la explotacion",
      "priority": "Must",
      "verification": "Con datos validos, registrar una parcela y comprobar que aparece en el listado."
    },
    {
      "id": "RF-02",
      "name": "Registrar lecturas de humedad",
      "description": "El sistema debera almacenar cada lectura de humedad con su sensor, su marca de tiempo y su parcela.",
      "actor": ["Sensor de humedad", "Pasarela de campo"],
      "priority": "Must",
      "verification": "Enviar una lectura y comprobar que queda almacenada con su marca de tiempo."
    }
  ]
}
example.end
