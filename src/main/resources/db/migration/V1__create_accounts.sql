CREATE TABLE IF NOT EXISTS accounts (
    account_number INT PRIMARY KEY,
    account_type VARCHAR(100) NOT NULL,
    account_holder VARCHAR(255) NOT NULL,
    creation_date VARCHAR(50) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    payload BLOB NOT NULL
);
