CREATE TABLE lietotajs (
    lietotaja_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vards VARCHAR(50) NOT NULL,
    uzvards VARCHAR(50) NOT NULL,
    lietotajvards VARCHAR(50) NOT NULL UNIQUE,
    parole VARCHAR(255) NOT NULL,
    epasts VARCHAR(100) NOT NULL UNIQUE,
    loma VARCHAR(20) DEFAULT 'READER' NOT NULL,
    izveidots TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK (loma IN ('ADMIN', 'LIBRARIAN', 'READER', 'AUTHOR'))
);

CREATE TABLE gramata (
    gramatas_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nosaukums VARCHAR(200) NOT NULL,
    apraksts CLOB,
    izdosanas_gads INTEGER,
    kategorija VARCHAR(50),
    isbn VARCHAR(20) UNIQUE,
    autors VARCHAR(100),
    pieejamiba BOOLEAN DEFAULT FALSE NOT NULL,
    CHECK (izdosanas_gads BETWEEN 1800 AND 2100)
);

CREATE TABLE eksemplars (
    eksemplara_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gramatas_id INTEGER NOT NULL REFERENCES gramata(gramatas_id),
    biblioteka VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'pieejams' NOT NULL,
    CHECK (status IN ('pieejams', 'izsniegts', 'rezervēts'))
);

CREATE TABLE izsniegums (
    izsnieguma_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lietotaja_id INTEGER NOT NULL REFERENCES lietotajs(lietotaja_id),
    eksemplara_id INTEGER NOT NULL REFERENCES eksemplars(eksemplara_id),
    izsniegts TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    termins TIMESTAMP NOT NULL,
    atgriezts TIMESTAMP,
    CHECK (termins >= izsniegts),
    CHECK (atgriezts IS NULL OR atgriezts >= izsniegts)
);

CREATE TABLE rezervacija (
    rezervacijas_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lietotaja_id INTEGER NOT NULL REFERENCES lietotajs(lietotaja_id),
    gramatas_id INTEGER NOT NULL REFERENCES gramata(gramatas_id),
    datums TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    statuss VARCHAR(20) DEFAULT 'aktīva' NOT NULL,
    CHECK (statuss IN ('aktīva', 'izpildīta', 'atcelta'))
);

CREATE INDEX idx_izsniegums_eksemplars ON izsniegums(eksemplara_id);
CREATE INDEX idx_rezervacija_rinda ON rezervacija(gramatas_id, statuss, datums);

CREATE TRIGGER izsniegt_eksemplaru
AFTER INSERT ON izsniegums
REFERENCING NEW AS n
FOR EACH ROW MODE DB2SQL
UPDATE eksemplars SET status = 'izsniegts'
WHERE eksemplara_id = n.eksemplara_id AND n.atgriezts IS NULL;

CREATE TRIGGER atgriezt_eksemplaru
AFTER UPDATE OF atgriezts ON izsniegums
REFERENCING OLD AS o NEW AS n
FOR EACH ROW MODE DB2SQL
UPDATE eksemplars SET status = 'pieejams'
WHERE eksemplara_id = n.eksemplara_id
  AND o.atgriezts IS NULL AND n.atgriezts IS NOT NULL;

CREATE VIEW gramatu_katalogs AS
SELECT g.gramatas_id, g.nosaukums, g.autors, g.isbn,
       g.kategorija, g.izdosanas_gads,
       CASE WHEN EXISTS (
           SELECT 1 FROM eksemplars e
           WHERE e.gramatas_id = g.gramatas_id AND e.status = 'pieejams'
       ) THEN TRUE ELSE FALSE END AS pieejamiba
FROM gramata g;

CREATE VIEW kavetie_izsniegumi AS
SELECT i.izsnieguma_id, i.lietotaja_id, l.vards, l.uzvards, l.epasts,
       g.nosaukums, e.biblioteka, i.termins
FROM izsniegums i
JOIN lietotajs l ON l.lietotaja_id = i.lietotaja_id
JOIN eksemplars e ON e.eksemplara_id = i.eksemplara_id
JOIN gramata g ON g.gramatas_id = e.gramatas_id
WHERE i.atgriezts IS NULL AND i.termins < CURRENT_TIMESTAMP;
CREATE TRIGGER pieejamiba_insert
AFTER INSERT ON eksemplars
REFERENCING NEW AS n
FOR EACH ROW MODE DB2SQL
UPDATE gramata SET pieejamiba = CASE WHEN EXISTS (
    SELECT 1 FROM eksemplars e
    WHERE e.gramatas_id = gramata.gramatas_id AND e.status = 'pieejams'
) THEN TRUE ELSE FALSE END WHERE gramatas_id = n.gramatas_id;

CREATE TRIGGER pieejamiba_update
AFTER UPDATE ON eksemplars
REFERENCING NEW AS n
FOR EACH ROW MODE DB2SQL
UPDATE gramata SET pieejamiba = CASE WHEN EXISTS (
    SELECT 1 FROM eksemplars e
    WHERE e.gramatas_id = gramata.gramatas_id AND e.status = 'pieejams'
) THEN TRUE ELSE FALSE END WHERE gramatas_id = n.gramatas_id;

CREATE TRIGGER pieejamiba_delete
AFTER DELETE ON eksemplars
REFERENCING OLD AS o
FOR EACH ROW MODE DB2SQL
UPDATE gramata SET pieejamiba = CASE WHEN EXISTS (
    SELECT 1 FROM eksemplars e
    WHERE e.gramatas_id = gramata.gramatas_id AND e.status = 'pieejams'
) THEN TRUE ELSE FALSE END WHERE gramatas_id = o.gramatas_id;
