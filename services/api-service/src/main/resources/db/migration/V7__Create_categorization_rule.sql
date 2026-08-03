CREATE TABLE categorization_rule (
    id BIGSERIAL PRIMARY KEY,

    merchant_pattern VARCHAR(100) NOT NULL,

    category_id BIGINT NOT NULL,

    priority INTEGER NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_categorization_rule_category
        FOREIGN KEY (category_id)
        REFERENCES category(id),

    CONSTRAINT chk_categorization_rule_pattern_not_blank
        CHECK (BTRIM(merchant_pattern) <> ''),

    CONSTRAINT chk_categorization_rule_priority_positive
        CHECK (priority > 0)
);

CREATE UNIQUE INDEX uq_categorization_rule_pattern_ci ON categorization_rule (LOWER(merchant_pattern));

CREATE INDEX idx_categorization_rule_active_priority ON categorization_rule (active, priority, id);

INSERT INTO categorization_rule (
    merchant_pattern,
    category_id,
    priority
)
SELECT
    seed.merchant_pattern,
    category.id,
    seed.priority
FROM (
    VALUES
        ('UBER EATS',    'Restaurants',     10),
        ('UBER',         'Transportation',  20),

        ('PUBLIX',       'Groceries',       30),
        ('ALDI',         'Groceries',       30),
        ('WHOLE FOODS',  'Groceries',       30),

        ('SHELL',        'Transportation',  40),
        ('CHEVRON',      'Transportation',  40),
        ('EXXON',        'Transportation',  40),

        ('STARBUCKS',    'Restaurants',     50),
        ('CHIPOTLE',     'Restaurants',     50),
        ('MCDONALD',     'Restaurants',     50),

        ('NETFLIX',      'Entertainment',   60),
        ('SPOTIFY',      'Entertainment',   60),
        ('AMC THEATRES', 'Entertainment',   60),

        ('DUKE ENERGY',  'Utilities',       70),
        ('TECO',         'Utilities',       70),

        ('CVS PHARMACY', 'Healthcare',      80),
        ('WALGREENS',    'Healthcare',      80),

        ('PAYROLL',      'Income',          90),
        ('DIRECT DEP',   'Income',          90)
) AS seed (
    merchant_pattern,
    category_name,
    priority
)
JOIN category
    ON LOWER(category.name) =
       LOWER(seed.category_name);