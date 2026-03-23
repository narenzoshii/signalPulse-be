INSERT IGNORE INTO signalPulse.app_config (config_key,config_value) VALUES
                                                                 ('AES_KEY','986fdf815a70e37f8191cbd7bebdd922361ac3931de081d5c00892ce2693b9d0'),
                                                                 ('GMAIL_PASSWORD','ZtBkXQ+xx2wuaIvOUdH/s0hheVI3eU7or7J5BiMB27E='),
                                                                 ('GMAIL_USER','foneloan.f1soft@gmail.com'),
                                                                 ('INITIAL_LOOKBACK_HOURS','5000'),
                                                                 ('MANUAL_WINDOW','24'),
                                                                 ('MIN_RELEVANCE_SCORE','1'),
                                                                 ('NOTIFY_RECIPIENTS','narenzoshi@gmail.com'),
                                                                 ('SCAN_MAX_RETRIES','3'),
                                                                 ('SCAN_RETRY_INTERVAL_MS','2000'),
                                                                 ('TOP_N_ARTICLES','5');

INSERT IGNORE INTO signalPulse.privilege (name) VALUES 
('OP_READ_ALL'), ('OP_WRITE_SOURCES'), ('OP_WRITE_RULES'), ('OP_TRIGGER_SCAN'), ('OP_MANAGE_USERS'), ('OP_MANAGE_CONFIG'), ('OP_PAUSE_JOBS');

INSERT IGNORE INTO signalPulse.role (name) VALUES ('SUPERADMIN'), ('ADMIN'), ('USER');

-- Link SUPERADMIN to all privileges
INSERT IGNORE INTO signalPulse.roles_privileges (role_id, privilege_id)
SELECT r.id, p.id FROM signalPulse.role r, signalPulse.privilege p WHERE r.name = 'SUPERADMIN';

-- Link ADMIN to some privileges
INSERT IGNORE INTO signalPulse.roles_privileges (role_id, privilege_id)
SELECT r.id, p.id FROM signalPulse.role r, signalPulse.privilege p 
WHERE r.name = 'ADMIN' AND p.name IN ('OP_READ_ALL', 'OP_TRIGGER_SCAN');

-- Link USER to READ_ALL
INSERT IGNORE INTO signalPulse.roles_privileges (role_id, privilege_id)
SELECT r.id, p.id FROM signalPulse.role r, signalPulse.privilege p 
WHERE r.name = 'USER' AND p.name = 'OP_READ_ALL';

-- Create default SuperAdmin
INSERT IGNORE INTO signalPulse.app_user (username, password) VALUES 
('admin', '$2a$12$F9REEx/PTTtIavijLgbxJOUe42RCA7nJl2qDKlddAAK2na/hqpFTy');

-- Link Default Admin to SUPERADMIN role
INSERT IGNORE INTO signalPulse.users_roles (user_id, role_id)
SELECT u.id, r.id FROM signalPulse.app_user u, signalPulse.role r 
WHERE u.username = 'admin' AND r.name = 'SUPERADMIN';

-- Session Timeout Config
INSERT IGNORE INTO signalPulse.app_config (config_key, config_value) VALUES ('SESSION_TIMEOUT_MINS', '30');
