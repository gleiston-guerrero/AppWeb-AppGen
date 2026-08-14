import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Activity, Component as Componente, Plan, Resource, Task } from './plan';

/** Acceso a la descomposición del trabajo de un proyecto. */
@Injectable({ providedIn: 'root' })
export class PlanService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/v1/projects/${projectId}/plan`;
  }

  /** Plan completo con su avance calculado. */
  plan(projectId: string): Observable<Plan> {
    return this.http.get<Plan>(this.url(projectId));
  }

  // --- Componentes ---

  crearComponente(
    projectId: string,
    deliverableId: string,
    datos: { name: string; description?: string },
  ): Observable<Componente> {
    return this.http.post<Componente>(
      `${this.url(projectId)}/deliverables/${deliverableId}/components`,
      datos,
    );
  }

  editarComponente(
    projectId: string,
    componentId: string,
    datos: { name: string; description?: string },
  ): Observable<Componente> {
    return this.http.put<Componente>(`${this.url(projectId)}/components/${componentId}`, datos);
  }

  eliminarComponente(projectId: string, componentId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(projectId)}/components/${componentId}`);
  }

  // --- Tareas ---

  crearTarea(
    projectId: string,
    componentId: string,
    datos: { name: string; description?: string; plannedEffort: number; assignee?: string },
  ): Observable<Task> {
    return this.http.post<Task>(`${this.url(projectId)}/components/${componentId}/tasks`, datos);
  }

  editarTarea(
    projectId: string,
    taskId: string,
    datos: { name: string; description?: string; plannedEffort: number; assignee?: string },
  ): Observable<Task> {
    return this.http.put<Task>(`${this.url(projectId)}/tasks/${taskId}`, datos);
  }

  transitarTarea(projectId: string, taskId: string, to: string): Observable<Task> {
    return this.http.put<Task>(`${this.url(projectId)}/tasks/${taskId}/status?to=${to}`, {});
  }

  eliminarTarea(projectId: string, taskId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(projectId)}/tasks/${taskId}`);
  }

  // --- Actividades ---

  crearActividad(
    projectId: string,
    taskId: string,
    datos: { name: string; plannedEffort: number },
  ): Observable<Activity> {
    return this.http.post<Activity>(`${this.url(projectId)}/tasks/${taskId}/activities`, datos);
  }

  editarActividad(
    projectId: string,
    activityId: string,
    datos: { name: string; plannedEffort: number },
  ): Observable<Activity> {
    return this.http.put<Activity>(`${this.url(projectId)}/activities/${activityId}`, datos);
  }

  /** Retira un asiento de horas. Solo puede quien lo anotó. */
  retirarHoras(projectId: string, activityId: string, entryId: string): Observable<Activity> {
    return this.http.delete<Activity>(
      `${this.url(projectId)}/activities/${activityId}/time/${entryId}`,
    );
  }

  marcarActividad(projectId: string, activityId: string, done: boolean): Observable<Activity> {
    return this.http.put<Activity>(
      `${this.url(projectId)}/activities/${activityId}/completion?done=${done}`,
      {},
    );
  }

  eliminarActividad(projectId: string, activityId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(projectId)}/activities/${activityId}`);
  }

  /** Registra horas. Las anota quien las dedicó, a su nombre. */
  registrarHoras(
    projectId: string,
    activityId: string,
    datos: { hours: number; workedOn?: string; note?: string },
  ): Observable<Activity> {
    return this.http.post<Activity>(
      `${this.url(projectId)}/activities/${activityId}/time`,
      datos,
    );
  }

  // --- Recursos ---

  crearRecurso(
    projectId: string,
    datos: { name: string; kind: string; unit?: string; quantity?: number; notes?: string },
  ): Observable<Resource> {
    return this.http.post<Resource>(`${this.url(projectId)}/resources`, datos);
  }

  editarRecurso(
    projectId: string,
    resourceId: string,
    datos: { name: string; kind: string; unit?: string; quantity?: number; notes?: string },
  ): Observable<Resource> {
    return this.http.put<Resource>(`${this.url(projectId)}/resources/${resourceId}`, datos);
  }

  eliminarRecurso(projectId: string, resourceId: string): Observable<void> {
    return this.http.delete<void>(`${this.url(projectId)}/resources/${resourceId}`);
  }

  asignarRecurso(
    projectId: string,
    taskId: string,
    datos: { resource: string; quantity?: number },
  ): Observable<Task> {
    return this.http.post<Task>(`${this.url(projectId)}/tasks/${taskId}/resources`, datos);
  }

  retirarRecurso(projectId: string, taskId: string, resourceId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.url(projectId)}/tasks/${taskId}/resources/${resourceId}`,
    );
  }
}
