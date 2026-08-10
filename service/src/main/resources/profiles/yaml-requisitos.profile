# =====================================================================
# Perfil de importacion: YAML
# =====================================================================

profile.id          = yaml-requisitos
profile.name        = YAML con lista de requisitos
profile.description = Archivo YAML con una lista de requisitos, en la raiz o bajo la clave "requirements". Admite texto de varias lineas con la barra vertical, que es lo comodo para enunciados largos.
profile.extensions  = .yaml, .yml
profile.reader      = yaml
list.path           = requirements

field.id                       = id
field.identificador            = id
field.name                     = name
field.nombre                   = name
field.description              = description
field.descripcion              = description
field.statement                = description
field.enunciado                = description
field.actor                    = actor
field.inputs                   = inputs
field.outputs                  = outputs
field.preconditions            = preconditions
field.postconditions           = postconditions
field.priority                 = priority
field.prioridad                = priority
field.verification             = verification
field.criterio                 = verification
field.metric                   = metric
field.target                   = target
field.kind                     = kind
field.tipo                     = kind

expected = id, description, verification

example.begin
project: Granja Inteligente

requirements:
  - id: RF-01
    name: Registrar parcela de cultivo
    description: >
      El sistema debera registrar una parcela con su superficie,
      tipo de suelo y cultivo sembrado.
    actor: Responsable de la explotacion
    priority: Must
    verification: Con datos validos, registrar una parcela y comprobar que aparece en el listado.

  - id: RF-02
    name: Registrar lecturas de humedad
    description: El sistema debera almacenar cada lectura de humedad con su sensor y su parcela.
    actor: Sensor de humedad
    priority: Must
    verification: Enviar una lectura y comprobar que queda almacenada con su marca de tiempo.
example.end
