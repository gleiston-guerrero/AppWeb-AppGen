/**
 * Informacion publica de la plataforma.
 *
 * Corresponde exactamente al contrato que devuelve GET /api/v1/platform-info,
 * definido en el servicio por el registro PlatformInfo.
 *
 * Realiza los requisitos FUN-01 y FUN-02.
 */
export interface PlatformInfo {
  name: string;
  version: string;
  purpose: string;
  input: string;
  produces: string[];
  builtWith: string[];
  authorship: string;
  license: string;
  repository: string;
}
