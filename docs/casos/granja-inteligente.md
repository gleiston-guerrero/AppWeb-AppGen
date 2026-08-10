# Sistema de Gestion de Granja Inteligente (SGGI)

Especificacion de requisitos en formato Markdown con campos etiquetados.
Perfil de importacion: `markdown-campos`.

## RF-01 — Registrar parcela de cultivo

**Descripcion:** El sistema debera registrar una parcela de cultivo con su identificador, superficie en hectareas, tipo de suelo, cultivo sembrado y coordenadas de sus vertices.
**Actor:** Responsable de la explotacion
**Prioridad:** Must
**Criterio de verificacion:** Con datos validos, registrar una parcela y comprobar que aparece en el listado con su superficie y su cultivo.

## RF-02 — Registrar lecturas de humedad del suelo

**Descripcion:** El sistema debera almacenar cada lectura de humedad del suelo recibida de un sensor, junto con el identificador del sensor, la marca de tiempo y la parcela a la que pertenece.
**Actor:** Sensor de humedad
**Prioridad:** Must
**Criterio de verificacion:** Enviar una lectura desde un sensor dado de alta y comprobar que queda almacenada con su marca de tiempo y su parcela.

## RF-03 — Activar el riego por umbral de humedad

**Descripcion:** Cuando la humedad media de una parcela descienda por debajo del umbral configurado para su cultivo, el sistema debera activar la valvula de riego de esa parcela.
**Actor:** Sistema
**Prioridad:** Must
**Criterio de verificacion:** Con un umbral del 30 por ciento, enviar lecturas que promedien 25 por ciento y comprobar que se emite la orden de apertura y queda registrada.

## RF-04 — Registrar el consumo de agua de cada riego

**Descripcion:** El sistema debera registrar el volumen de agua consumido en cada activacion de riego, asociado a su parcela y a su marca de tiempo.
**Actor:** Sistema
**Prioridad:** Should
**Criterio de verificacion:** Ejecutar un riego de volumen conocido y comprobar que el consumo registrado coincide con el medido por el caudalimetro.

## RF-05 — Emitir alerta por temperatura fuera de rango en el invernadero

**Descripcion:** Cuando la temperatura de un invernadero salga del rango configurado para su cultivo, el sistema debera notificar al responsable de la explotacion indicando el invernadero y la temperatura medida.
**Actor:** Sistema
**Prioridad:** Must
**Criterio de verificacion:** Configurar un rango de 18 a 28 grados, simular una lectura de 32 grados y comprobar que llega la notificacion con el invernadero y el valor.

## RF-06 — Consultar el historial de un animal

**Descripcion:** El sistema debera mostrar el historial de un animal identificado por su crotal, incluyendo pesajes, tratamientos veterinarios y traslados entre parcelas.
**Actor:** Veterinario
**Prioridad:** Should
**Criterio de verificacion:** Consultar un animal con tres pesajes y dos tratamientos registrados y comprobar que aparecen los cinco asientos ordenados por fecha.

## RF-07 — Planificar el calendario de siembra

**Descripcion:** El sistema deberia permitir planificar el calendario de siembra de cada parcela para la campana siguiente.
**Actor:** Responsable de la explotacion
**Prioridad:** Could
**Criterio de verificacion:** Planificar una siembra y comprobar que aparece en el calendario de la parcela.

## RF-08 — Dar de alta un sensor en una parcela

**Descripcion:** El sistema debera dar de alta un sensor asociandolo a una parcela, con su tipo de medida, su periodo de muestreo y su ubicacion dentro de la parcela.
**Actor:** Responsable de la explotacion
**Prioridad:** Must

## RNF-01 — Autonomia de los dispositivos de campo

**Descripcion:** El sistema debera operar los dispositivos de campo con la autonomia establecida sin sustitucion de bateria.
**Metrica:** Dias de operacion continua con una carga completa, con periodo de muestreo de 15 minutos
**Valor objetivo:** Al menos 180 dias
**Prioridad:** Must
**Criterio de verificacion:** Someter un dispositivo a muestreo cada 15 minutos y comprobar que supera los 180 dias antes de agotar la bateria.

## RNF-02 — Proteccion de los datos de la explotacion

**Descripcion:** El sistema debera cifrar las comunicaciones entre los dispositivos de campo y la plataforma.
**Metrica:** Proporcion de comunicaciones establecidas con cifrado extremo a extremo
**Valor objetivo:** El 100 por ciento de las comunicaciones
**Prioridad:** Must
**Criterio de verificacion:** Interceptar el trafico entre un dispositivo y la plataforma y comprobar que ningun dato de lectura viaja legible.
