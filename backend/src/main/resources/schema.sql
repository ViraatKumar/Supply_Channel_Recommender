-- Plain SQL rather than Hibernate ddl-auto so the schema is reviewable and identical on
-- Postgres (default) and H2 (the --profiles=h2 escape hatch).
DROP TABLE IF EXISTS channel;

CREATE TABLE channel (
    id                       VARCHAR(64) PRIMARY KEY,
    name                     VARCHAR(128) NOT NULL,
    type                     VARCHAR(32)  NOT NULL,
    cost_per_applicant       DOUBLE PRECISION NOT NULL,
    min_budget               DOUBLE PRECISION NOT NULL,
    expected_volume_per_week DOUBLE PRECISION NOT NULL,
    quality_score            DOUBLE PRECISION NOT NULL,
    lead_time_days           INTEGER NOT NULL,
    supported_locations      VARCHAR(1000) NOT NULL,
    skill_tags               VARCHAR(1000) NOT NULL,
    channel_constraints      VARCHAR(500)
);
