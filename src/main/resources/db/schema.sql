-- Ticket Reservation System - database schema (MySQL 8+, also runs on H2 in MySQL mode).
-- The application runs this automatically on first start when TR_STORAGE=jdbc.
-- Times are local airport times. bookings.created_at is UTC.

CREATE TABLE airports (
    code    CHAR(3)      NOT NULL PRIMARY KEY,   -- IATA code, e.g. TIA
    name    VARCHAR(100) NOT NULL,
    city    VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL
);

CREATE TABLE flights (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    flight_number     VARCHAR(10)   NOT NULL,
    origin_code       CHAR(3)       NOT NULL,
    destination_code  CHAR(3)       NOT NULL,
    departure_time    DATETIME      NOT NULL,
    arrival_time      DATETIME      NOT NULL,
    aircraft          VARCHAR(20)   NOT NULL,
    currency          CHAR(3)       NOT NULL,
    economy_price     DECIMAL(10,2) NOT NULL,
    first_class_price DECIMAL(10,2) NOT NULL,
    available_seats   INT           NOT NULL,
    CONSTRAINT fk_flights_origin      FOREIGN KEY (origin_code)      REFERENCES airports (code),
    CONSTRAINT fk_flights_destination FOREIGN KEY (destination_code) REFERENCES airports (code),
    CONSTRAINT chk_flights_route      CHECK (origin_code <> destination_code),
    CONSTRAINT chk_flights_times      CHECK (arrival_time > departure_time),
    CONSTRAINT chk_flights_prices     CHECK (economy_price >= 0 AND first_class_price >= 0),
    CONSTRAINT chk_flights_seats      CHECK (available_seats >= 0)
);

CREATE INDEX idx_flights_route ON flights (origin_code, destination_code, departure_time);
CREATE INDEX idx_flights_departure ON flights (departure_time);

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,     -- PBKDF2 hash with salt, never the plain password
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE bookings (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_reference  CHAR(6)       NOT NULL,
    user_id            BIGINT        NOT NULL,
    outbound_flight_id BIGINT        NOT NULL,
    return_flight_id   BIGINT        NULL,
    fare_class         VARCHAR(10)   NOT NULL,
    checked_baggage    BOOLEAN       NOT NULL,
    currency           CHAR(3)       NOT NULL,
    total_price        DECIMAL(10,2) NOT NULL,
    status             VARCHAR(10)   NOT NULL,
    created_at         DATETIME(3)   NOT NULL,
    CONSTRAINT uq_bookings_reference UNIQUE (booking_reference),
    CONSTRAINT fk_bookings_user     FOREIGN KEY (user_id)            REFERENCES users (id),
    CONSTRAINT fk_bookings_outbound FOREIGN KEY (outbound_flight_id) REFERENCES flights (id),
    CONSTRAINT fk_bookings_return   FOREIGN KEY (return_flight_id)   REFERENCES flights (id)
);

CREATE INDEX idx_bookings_user ON bookings (user_id, created_at);

CREATE TABLE booking_passengers (
    booking_id   BIGINT      NOT NULL,
    seq_no       INT         NOT NULL,
    first_name   VARCHAR(50) NOT NULL,
    last_name    VARCHAR(50) NOT NULL,
    phone_number VARCHAR(16) NOT NULL,
    PRIMARY KEY (booking_id, seq_no),
    CONSTRAINT fk_passengers_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
);
