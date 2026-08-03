CREATE TABLE category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,

    CONSTRAINT chk_category_name_not_blank
        CHECK (BTRIM(name) <> '')
);

CREATE UNIQUE INDEX uq_category_name_ci
    ON category (LOWER(name));

INSERT INTO category (name)
VALUES
    ('Housing'),
    ('Groceries'),
    ('Transportation'),
    ('Restaurants'),
    ('Entertainment'),
    ('Utilities'),
    ('Healthcare'),
    ('Income'),
    ('Other');