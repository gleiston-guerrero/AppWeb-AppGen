-- =====================================================================
-- SLCP - Reglas que la base de datos impone por si misma
--
-- Criterio aplicado: la base de datos impone lo que debe ser verdad por
-- cualquier camino de acceso, incluido psql o una migracion futura. No impone
-- reglas de negocio, que pertenecen al dominio y que duplicadas en dos
-- lenguajes acabarian divergiendo.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. El almacen de eventos es de solo anexado (TRC-24)
--
-- Hasta ahora era una convencion sostenida por el codigo Java. Una convencion
-- protege del olvido, no del acceso directo. Esta regla protege de ambos.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_event_records_solo_anexado()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'TRC-24: el almacen de eventos es de solo anexado. Operacion % rechazada sobre event_records.',
        TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_event_records_sin_modificacion
    BEFORE UPDATE OR DELETE ON event_records
    FOR EACH ROW EXECUTE FUNCTION fn_event_records_solo_anexado();

-- ---------------------------------------------------------------------
-- 2. Los identificadores de acceso los mantiene la base de datos
--
-- Antes los insertaba el servicio. Cualquier via que crease una cuenta sin
-- pasar por ese servicio dejaria a la persona sin poder iniciar sesion, y el
-- defecto no se manifestaria hasta el primer intento de acceso.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_login_identifiers_sincronizar()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO login_identifiers (identifier, user_id, kind)
        VALUES (LOWER(NEW.username), NEW.id, 'USERNAME'),
               (LOWER(NEW.email),    NEW.id, 'EMAIL');

    ELSIF TG_OP = 'UPDATE' THEN
        IF LOWER(NEW.username) IS DISTINCT FROM LOWER(OLD.username) THEN
            UPDATE login_identifiers SET identifier = LOWER(NEW.username)
             WHERE user_id = NEW.id AND kind = 'USERNAME';
        END IF;
        IF LOWER(NEW.email) IS DISTINCT FROM LOWER(OLD.email) THEN
            UPDATE login_identifiers SET identifier = LOWER(NEW.email)
             WHERE user_id = NEW.id AND kind = 'EMAIL';
        END IF;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_users_sincronizar_identificadores
    AFTER INSERT OR UPDATE OF username, email ON users
    FOR EACH ROW EXECUTE FUNCTION fn_login_identifiers_sincronizar();

-- ---------------------------------------------------------------------
-- 3. Lo inmutable es inmutable (TRC-03)
--
-- El identificador interno y la fecha de creacion son extremos de enlaces y
-- marcas de auditoria. Nada debe poder alterarlos, ni por descuido ni por una
-- consulta suelta.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_users_campos_inmutables()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id THEN
        RAISE EXCEPTION 'TRC-03: el identificador interno de una cuenta no puede modificarse'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'TRC-03: la fecha de creacion de una cuenta no puede modificarse'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_users_inmutables
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_users_campos_inmutables();

-- ---------------------------------------------------------------------
-- 4. Tokens de renovacion de sesion (SEC-03)
--
-- No se guarda el token sino su huella: quien lea la tabla no puede usarlo.
-- La revocacion se registra, no se borra la fila, para que el cierre de sesion
-- deje rastro.
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID          PRIMARY KEY,
    token_hash  VARCHAR(64)   NOT NULL,
    user_id     UUID          NOT NULL,
    issued_at   TIMESTAMPTZ   NOT NULL,
    expires_at  TIMESTAMPTZ   NOT NULL,
    revoked_at  TIMESTAMPTZ,
    revoked_reason VARCHAR(40),

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_refresh_tokens_vigencia CHECK (expires_at > issued_at)
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- Una vez revocado, un token no vuelve a estar vigente.
CREATE OR REPLACE FUNCTION fn_refresh_tokens_revocacion_definitiva()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS NULL THEN
        RAISE EXCEPTION 'SEC-03: un token de renovacion revocado no puede volver a estar vigente'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_refresh_tokens_revocacion
    BEFORE UPDATE ON refresh_tokens
    FOR EACH ROW EXECUTE FUNCTION fn_refresh_tokens_revocacion_definitiva();
