/**
 * Conserva la posición de la página al recargar sus datos.
 *
 * Después de una acción —guardar, marcar, aceptar— la lista se vuelve a pedir y
 * el navegador la redibuja desde arriba. Quien estaba trabajando en la tarea
 * decimoquinta acaba en la primera y tiene que buscarla otra vez.
 *
 * Se conserva la posición, no el elemento: es lo que funciona en cualquier
 * pantalla sin que cada una tenga que identificar dónde estaba el usuario. Para
 * volver a un elemento concreto —que además conviene desplegar— está
 * `volverAlElemento`.
 */
export function conservarPosicion(): () => void {
  const y = window.scrollY;

  return () => {
    // En el ciclo siguiente: la lista acaba de cambiar y todavía no tiene su
    // altura definitiva. Restaurar antes dejaría la página donde no toca.
    requestAnimationFrame(() => window.scrollTo({ top: y, behavior: 'instant' }));
  };
}

/**
 * Devuelve la vista a un elemento concreto.
 *
 * Se usa cuando la acción cambia la altura de lo que hay encima —al desplegar
 * un detalle, al añadir filas— y conservar la posición ya no bastaría porque el
 * elemento se habrá movido.
 */
export function volverAlElemento(id: string): void {
  setTimeout(() => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, 0);
}
