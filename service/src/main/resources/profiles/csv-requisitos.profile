# =====================================================================
# Perfil de importacion: valores separados por comas
#
# Un requisito por fila. La primera fila son los nombres de columna, y esos
# nombres son los que se corresponden con los campos canonicos.
# =====================================================================

profile.id          = csv-requisitos
profile.name        = Valores separados por comas (CSV)
profile.description = Hoja de calculo exportada como CSV, con un requisito por fila y los nombres de campo en la primera fila. Admite comas dentro de un valor si va entre comillas, y comillas dobladas para escapar una comilla.
profile.extensions  = .csv
profile.reader      = csv

field.id                       = id
field.identificador            = id
field.name                     = name
field.nombre                   = name
field.description              = description
field.descripcion              = description
field.enunciado                = description
field.actor                    = actor
field.entradas                 = inputs
field.salidas                  = outputs
field.precondiciones           = preconditions
field.postcondiciones          = postconditions
field.priority                 = priority
field.prioridad                = priority
field.verification             = verification
field.criterio de verificacion = verification
field.criterio                 = verification
field.metrica                  = metric
field.valor objetivo           = target
field.tipo                     = kind

expected = id, description, verification

example.begin
Identificador,Nombre,Descripcion,Actor,Prioridad,Criterio de verificacion
RF-01,Registrar parcela de cultivo,"El sistema debera registrar una parcela con su superficie, tipo de suelo y cultivo sembrado.",Responsable de la explotacion,Must,"Con datos validos, registrar una parcela y comprobar que aparece en el listado."
RF-02,Registrar lecturas de humedad,"El sistema debera almacenar cada lectura de humedad con su sensor, su marca de tiempo y su parcela.",Sensor de humedad,Must,Enviar una lectura y comprobar que queda almacenada con su marca de tiempo.
example.end
