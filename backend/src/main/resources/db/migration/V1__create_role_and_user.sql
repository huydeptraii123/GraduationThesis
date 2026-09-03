CREATE TABLE role (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20)  NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_id       BIGINT       NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT fk_app_user_role FOREIGN KEY (role_id) REFERENCES role (id)
);
