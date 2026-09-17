-- ==============================================================================
-- DATABASE SYSTEM: HUIT LIBRARY MANAGEMENT
-- VERSION: 40.0 (FINAL FULL DDL - 28 TABLES)
-- DIALECT: PostgreSQL
-- ==============================================================================

CREATE TYPE user_role_enum AS ENUM ('STUDENT', 'FACULTY', 'STAFF', 'EXTERNAL');
CREATE TYPE card_status_enum AS ENUM ('INACTIVE', 'ACTIVE', 'LOCKED', 'CANCELED');
CREATE TYPE loan_status_enum AS ENUM ('PENDING_PICKUP', 'ACTIVE', 'OVERDUE', 'RETURNED', 'LOST');
CREATE TYPE room_status_enum AS ENUM ('AVAILABLE', 'IN_USE', 'MAINTENANCE');

-- ==============================================================================
-- 1. USERS & AUTHENTICATION (9 Tables)
-- ==============================================================================
CREATE TABLE STAFF_ROLES (
    role_id VARCHAR(50) PRIMARY KEY,
    role_name VARCHAR(100) NOT NULL,
    permissions JSONB NOT NULL
);

CREATE TABLE USERS (
    user_id VARCHAR(50) PRIMARY KEY,
    role_id VARCHAR(50) REFERENCES STAFF_ROLES(role_id),
    password_hash VARCHAR(255) NOT NULL,
    is_first_login BOOLEAN DEFAULT TRUE,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20),
    user_type user_role_enum NOT NULL,
    card_status card_status_enum DEFAULT 'INACTIVE',
    card_expiry_date TIMESTAMP,
    discipline_flag BOOLEAN DEFAULT FALSE,
    room_violation_count INT DEFAULT 0,
    room_lock_until TIMESTAMP
);

CREATE TABLE WALLET (
    wallet_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(50) REFERENCES USERS(user_id) UNIQUE,
    deposit_balance INT DEFAULT 0 CHECK (deposit_balance >= 0),
    total_debt INT DEFAULT 0,
    lock_version INT DEFAULT 0
);

CREATE TABLE NOTIFICATIONS (
    notif_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(50) REFERENCES USERS(user_id),
    type VARCHAR(50) NOT NULL, -- EMAIL, PUSH
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ORIENTATION_ATTENDANCE (
    record_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(50) REFERENCES USERS(user_id),
    attended_date TIMESTAMP NOT NULL,
    is_passed BOOLEAN NOT NULL DEFAULT FALSE,
    processed BOOLEAN DEFAULT FALSE
);

CREATE TABLE WAITLIST (
    waitlist_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) REFERENCES USERS(user_id),
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notified_at TIMESTAMP,
    expires_at TIMESTAMP,
    status VARCHAR(50) DEFAULT 'PENDING'
);

CREATE TABLE SHIFT_HANDOVER (
    shift_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id VARCHAR(50) REFERENCES USERS(user_id),
    pos_terminal_id VARCHAR(50),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    starting_cash INT NOT NULL,
    system_revenue INT DEFAULT 0,
    actual_cash INT,
    discrepancy_amount INT,
    status VARCHAR(50) DEFAULT 'ACTIVE'
);

CREATE TABLE INVENTORY_AUDITS (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id VARCHAR(50) REFERENCES USERS(user_id),
    pos_terminal_id VARCHAR(50),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(50) DEFAULT 'IN_PROGRESS',
    total_expected INT,
    total_scanned INT,
    total_missing INT
);

CREATE TABLE INVENTORY_DETAILS (
    detail_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id UUID REFERENCES INVENTORY_AUDITS(audit_id),
    barcode VARCHAR(100) REFERENCES BOOK_COPIES(barcode) NOT NULL,
    scan_status VARCHAR(50) NOT NULL,
    scanned_location VARCHAR(100)
);


-- ==============================================================================
-- 2. CATALOGING (7 Tables)
-- ==============================================================================
CREATE TABLE CATEGORIES (
    category_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id VARCHAR(50) REFERENCES CATEGORIES(category_id)
);

CREATE TABLE PUBLISHERS (
    publisher_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    contact_info TEXT
);

CREATE TABLE AUTHORS (
    author_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    biography TEXT
);

CREATE TABLE BOOKS (
    book_id VARCHAR(50) PRIMARY KEY,
    publisher_id VARCHAR(50) REFERENCES PUBLISHERS(publisher_id),
    category_id VARCHAR(50) REFERENCES CATEGORIES(category_id),
    title VARCHAR(255) NOT NULL,
    publish_year INT,
    language VARCHAR(50),
    label_color VARCHAR(50) NOT NULL,
    default_price INT,
    is_digital BOOLEAN DEFAULT FALSE
);

CREATE TABLE BOOK_AUTHORS (
    book_id VARCHAR(50) REFERENCES BOOKS(book_id),
    author_id VARCHAR(50) REFERENCES AUTHORS(author_id),
    PRIMARY KEY (book_id, author_id)
);

CREATE TABLE DIGITAL_DOCUMENTS (
    doc_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id VARCHAR(50) REFERENCES BOOKS(book_id) UNIQUE,
    file_url TEXT NOT NULL,
    is_open_source BOOLEAN DEFAULT FALSE,
    drm_encryption_key VARCHAR(255),
    download_count INT DEFAULT 0,
    view_count INT DEFAULT 0
);

CREATE TABLE DIGITAL_USAGE_LOGS (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id UUID REFERENCES DIGITAL_DOCUMENTS(doc_id),
    user_id VARCHAR(50) REFERENCES USERS(user_id),
    ip_address VARCHAR(50),
    accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE BOOK_COPIES (
    barcode VARCHAR(100) PRIMARY KEY,
    book_id VARCHAR(50) REFERENCES BOOKS(book_id),
    status VARCHAR(50) DEFAULT 'AVAILABLE',
    cover_price INT,
    appraised_value INT,
    binding_type VARCHAR(50),
    condition_note TEXT,
    location VARCHAR(100),
    source VARCHAR(100),
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMP,
    locked_by_user VARCHAR(50) REFERENCES USERS(user_id),
    weight_grams INT,
    replaced_by_barcode VARCHAR(100),
    lock_version INT DEFAULT 0
);

-- ==============================================================================
-- 3. CIRCULATION & ACCOUNTING (3 Tables)
-- ==============================================================================
CREATE TABLE LOAN_TICKETS (
    ticket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(50) REFERENCES USERS(user_id),
    ticket_type VARCHAR(50) NOT NULL,
    status loan_status_enum DEFAULT 'PENDING_PICKUP',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE LOAN_DETAILS (
    detail_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES LOAN_TICKETS(ticket_id),
    barcode VARCHAR(100) REFERENCES BOOK_COPIES(barcode),
    checkout_condition_note TEXT,
    return_condition_note TEXT,
    return_status VARCHAR(50) DEFAULT 'NOT_RETURNED',
    due_date TIMESTAMP NOT NULL,
    renewal_count INT DEFAULT 0 CHECK (renewal_count <= 2),
    is_recalled BOOLEAN DEFAULT FALSE,
    late_fine_accrued INT DEFAULT 0,
    return_date TIMESTAMP
);

CREATE TABLE TRANSACTIONS (
    transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(50) REFERENCES USERS(user_id),
    loan_detail_id UUID REFERENCES LOAN_DETAILS(detail_id),
    trans_type VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50),
    amount INT NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    admin_ref VARCHAR(100),
    gateway_ref VARCHAR(100),
    proof_document_url TEXT,
    parent_transaction_id UUID REFERENCES TRANSACTIONS(transaction_id),
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==============================================================================
-- 4. FACILITIES & SYSTEM CONFIGS (9 Tables)
-- ==============================================================================
CREATE TABLE ROOMS (
    room_id VARCHAR(50) PRIMARY KEY,
    room_name VARCHAR(100) NOT NULL,
    capacity INT,
    facilities TEXT,
    status room_status_enum DEFAULT 'AVAILABLE'
);

CREATE TABLE ROOM_BOOKINGS (
    booking_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(50) REFERENCES USERS(user_id),
    room_id VARCHAR(50) REFERENCES ROOMS(room_id),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    actual_start_time TIMESTAMP,
    actual_end_time TIMESTAMP,
    status VARCHAR(50) DEFAULT 'PENDING'
);

CREATE TABLE ROOM_BOOKING_MEMBERS (
    booking_id UUID REFERENCES ROOM_BOOKINGS(booking_id),
    user_id VARCHAR(50) REFERENCES USERS(user_id),
    is_leader BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (booking_id, user_id)
);

CREATE TABLE KIOSKS (
    kiosk_id VARCHAR(50) PRIMARY KEY,
    location_floor VARCHAR(50),
    ip_address VARCHAR(50),
    status VARCHAR(50) DEFAULT 'ONLINE',
    last_ping TIMESTAMP
);

CREATE TABLE SYSTEM_CONFIGS (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE AUDIT_LOGS (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    table_name VARCHAR(50) NOT NULL,
    changes JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE HOLIDAY_CALENDAR (
    date_id DATE PRIMARY KEY,
    holiday_name VARCHAR(100),
    is_closed BOOLEAN DEFAULT TRUE,
    override_open_time TIME,
    override_close_time TIME
);

CREATE TABLE UNIVERSITY_SYNC_LOGS (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint VARCHAR(255) NOT NULL,
    payload JSONB,
    sync_status VARCHAR(50) NOT NULL,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
