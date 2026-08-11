-- ユーザーのログイン情報と選択中のアバターを保存します。
CREATE TABLE IF NOT EXISTS USERS (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    avatar_type TEXT,
    CHECK (avatar_type IS NULL OR avatar_type IN ('male_a', 'male_b', 'female_a', 'female_b'))
);

-- パブリック部屋とプライベート部屋の基本情報を保存します。
CREATE TABLE IF NOT EXISTS ROOMS (
    room_id INTEGER PRIMARY KEY AUTOINCREMENT,
    room_code TEXT UNIQUE,
    room_type TEXT NOT NULL,
    room_name TEXT NOT NULL,
    theme TEXT,
    background_url TEXT,
    max_seats INTEGER NOT NULL,
    created_by INTEGER,
    CHECK (room_type IN ('private', 'public')),
    FOREIGN KEY (created_by) REFERENCES USERS (user_id)
);

-- 各部屋に用意された座席と、画面上の座標を保存します。
CREATE TABLE IF NOT EXISTS SEATS (
    seat_id INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id INTEGER NOT NULL,
    seat_number INTEGER NOT NULL,
    pos_x REAL NOT NULL,
    pos_y REAL NOT NULL,
    UNIQUE (room_id, seat_number),
    FOREIGN KEY (room_id) REFERENCES ROOMS (room_id)
);

-- 現在どのユーザーがどの席を利用しているかを保存します。
CREATE TABLE IF NOT EXISTS SEAT_ASSIGNMENTS (
    seat_id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE,
    status TEXT NOT NULL,
    work_content TEXT,
    started_at DATETIME,
    last_heartbeat_at DATETIME,
    CHECK (status IN ('working', 'break')),
    FOREIGN KEY (seat_id) REFERENCES SEATS (seat_id),
    FOREIGN KEY (user_id) REFERENCES USERS (user_id)
);

-- 全体チャットと個別チャットのメッセージを保存します。
CREATE TABLE IF NOT EXISTS CHAT_MESSAGES (
    message_id INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    target_user_id INTEGER,
    content TEXT NOT NULL,
    sent_at DATETIME NOT NULL,
    FOREIGN KEY (room_id) REFERENCES ROOMS (room_id),
    FOREIGN KEY (user_id) REFERENCES USERS (user_id),
    FOREIGN KEY (target_user_id) REFERENCES USERS (user_id)
);
