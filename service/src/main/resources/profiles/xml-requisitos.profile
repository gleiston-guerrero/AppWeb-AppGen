# =====================================================================
# Perfil de importacion: XML
#
# Los atributos y los elementos hijos se tratan igual: que un dato viaje como
# atributo o como elemento es estilo de quien escribio el documento, no una
# diferencia de significado.
# =====================================================================

profile.id          = xml-requisitos
profile.name        = XML con elementos de requisito
profile.description = Archivo XML donde cada requisito es un elemento hijo de la raiz. Sus campos pueden ir como atributos o como elementos, indistintamente.
profile.extensions  = .xml
profile.reader      = xml
# Nombre del elemento que representa un requisito.
xml.item            = requirement

field.id                       = id
field.identificador            = id
field.name                     = name
field.nombre                   = name
field.description              = description
field.descripcion              = description
field.statement                = description
field.actor                    = actor
field.inputs                   = inputs
field.outputs                  = outputs
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
<?xml version="1.0" encoding="UTF-8"?>
<requirements project="Granja Inteligente">

  <requirement id="RF-01" priority="Must">
    <name>Registrar parcela de cultivo</name>
    <description>El sistema debera registrar una parcela con su superficie, tipo de suelo y cultivo sembrado.</description>
    <actor>Responsable de la explotacion</actor>
    <verification>Con datos validos, registrar una parcela y comprobar que aparece en el listado.</verification>
  </requirement>

  <requirement id="RF-02" priority="Must">
    <name>Registrar lecturas de humedad</name>
    <description>El sistema debera almacenar cada lectura de humedad con su sensor y su parcela.</description>
    <actor>Sensor de humedad</actor>
    <verification>Enviar una lectura y comprobar que queda almacenada con su marca de tiempo.</verification>
  </requirement>

</requirements>
example.end
