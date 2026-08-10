# Dos requisitos para escribir a mano

Para probar el alta manual, en **Requisitos → Redactar un requisito**.

---

## 1. Bien redactado — no debe producir ningún hallazgo

**Tipo:** Requisito funcional

**Nombre:**
```
Registrar el traslado de un animal entre parcelas
```

**Enunciado:**
```
El sistema debera registrar el traslado de un animal entre parcelas, con su crotal, la parcela de origen, la parcela de destino y la fecha del traslado.
```

**Criterio de verificación:**
```
Trasladar un animal de una parcela a otra y comprobar que el asiento aparece en su historial con ambas parcelas y su fecha.
```

**Qué debe pasar:** el requisito aparece marcado como **conforme**, sin hallazgos y sin propuestas.

---

## 2. Con defectos — para ver trabajar al validador

**Tipo:** Requisito no funcional

**Nombre:**
```
Respuesta del panel de la explotacion
```

**Enunciado:**
```
El sistema deberia mostrar el panel de la explotacion de forma rapida y con una interfaz amigable.
```

**Criterio de verificación:** *déjelo vacío.*

**Qué debe pasar:** tres o cuatro hallazgos —«debería» no obliga, «rápida» y «amigable» no tienen magnitud— y, al desplegarlo, **criterios propuestos**.

Como es un requisito no funcional, una de las propuestas traerá `[indique el valor]` y **su botón de aceptar estará deshabilitado**: la plataforma no propone magnitudes. Tendrá que usar *Modificar antes de aceptar* y poner la cifra usted.

Ahí es donde se ve la regla completa: la plataforma redacta lo observable, y la magnitud la decide quien responde del sistema.
