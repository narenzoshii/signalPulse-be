-- ============================================================
-- SignalPulse — dataInitialize.sql
-- Runs at boot when spring.sql.init.mode=always (test profile).
-- Uses INSERT IGNORE so re-running is safe.
-- ============================================================

-- ---- App configuration --------------------------------------
INSERT IGNORE INTO signalPulse.app_config (config_key, config_value) VALUES
    ('AES_KEY',                '986fdf815a70e37f8191cbd7bebdd922361ac3931de081d5c00892ce2693b9d0'),
    ('GMAIL_PASSWORD',         'ZtBkXQ+xx2wuaIvOUdH/s0hheVI3eU7or7J5BiMB27E='),
    ('GMAIL_USER',             'foneloan.f1soft@gmail.com'),
    ('INITIAL_LOOKBACK_HOURS', '720'),
    ('MANUAL_WINDOW',          '24'),
    ('MIN_RELEVANCE_SCORE',    '3.0'),
    ('NOTIFY_RECIPIENTS',      'narenzoshi@gmail.com'),
    ('SCAN_MAX_RETRIES',       '3'),
    ('SCAN_RETRY_INTERVAL_MS', '2000'),
    ('TOP_N_ARTICLES',         '10'),
    ('SESSION_TIMEOUT_MINS',   '30');

-- ---- Roles & privileges -------------------------------------
INSERT IGNORE INTO signalPulse.privilege (name) VALUES
    ('OP_READ_ALL'),
    ('OP_WRITE_SOURCES'),
    ('OP_WRITE_RULES'),
    ('OP_TRIGGER_SCAN'),
    ('OP_MANAGE_USERS'),
    ('OP_MANAGE_CONFIG'),
    ('OP_PAUSE_JOBS');

INSERT IGNORE INTO signalPulse.role (name) VALUES ('SUPERADMIN'), ('ADMIN'), ('USER');

INSERT IGNORE INTO signalPulse.roles_privileges (role_id, privilege_id)
SELECT r.id, p.id FROM signalPulse.role r, signalPulse.privilege p WHERE r.name = 'SUPERADMIN';

INSERT IGNORE INTO signalPulse.roles_privileges (role_id, privilege_id)
SELECT r.id, p.id FROM signalPulse.role r, signalPulse.privilege p
WHERE r.name = 'ADMIN' AND p.name IN ('OP_READ_ALL', 'OP_TRIGGER_SCAN', 'OP_WRITE_SOURCES', 'OP_WRITE_RULES', 'OP_MANAGE_CONFIG', 'OP_PAUSE_JOBS');

INSERT IGNORE INTO signalPulse.roles_privileges (role_id, privilege_id)
SELECT r.id, p.id FROM signalPulse.role r, signalPulse.privilege p
WHERE r.name = 'USER' AND p.name = 'OP_READ_ALL';

-- Default superadmin (password: superadmin)
INSERT IGNORE INTO signalPulse.app_user (username, password) VALUES
    ('superadmin', '$2a$12$F9REEx/PTTtIavijLgbxJOUe42RCA7nJl2qDKlddAAK2na/hqpFTy');

INSERT IGNORE INTO signalPulse.users_roles (user_id, role_id)
SELECT u.id, r.id FROM signalPulse.app_user u, signalPulse.role r
WHERE u.username = 'superadmin' AND r.name = 'SUPERADMIN';

-- ============================================================
-- CONTENT CATEGORIES
-- Weight is added to *every* article whose source belongs to it.
-- Regulators carry the most signal; aggregators the least.
-- ============================================================
INSERT IGNORE INTO signalPulse.category (name, description, weight) VALUES
    ('Regulatory & Compliance', 'Central banks, financial regulators, supervisory bodies and standards organisations', 3.0),
    ('Industry News',           'High-quality fintech and banking trade publications',                                   2.0),
    ('Technology & Innovation', 'Coverage of platforms, payments tech, BaaS, alt-data and AI in lending',                2.0),
    ('Research & Analysis',     'Risk research, ratings, market intelligence and academic publications',                 2.5),
    ('Regional / South Asia',   'India, Nepal and South-Asian fintech coverage with NRB / RBI relevance',                2.5),
    ('General Business',        'Mainstream business / finance coverage where fintech surfaces incidentally',            1.0);

-- ============================================================
-- RSS FEEDS
-- trust is a per-source multiplier on top of category weight.
-- 1.5 = regulator/authoritative, 1.2 = top-tier trade, 1.0 = standard, 0.8 = aggregator
-- ============================================================

-- Regulators / standards bodies
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('BIS — News & speeches',       'https://www.bis.org/rss/home.xml',                              'Bank for International Settlements — global standard-setter for central banks.', 1.5, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('Federal Reserve — Press',     'https://www.federalreserve.gov/feeds/press_all.xml',            'US Federal Reserve press releases and supervisory communications.',              1.5, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('CFPB — Blog',                 'https://www.consumerfinance.gov/about-us/blog/feed/',           'US Consumer Financial Protection Bureau — consumer lending oversight.',          1.4, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('FCA UK — News',               'https://www.fca.org.uk/news/rss.xml',                           'UK Financial Conduct Authority — BNPL and consumer credit rules.',               1.4, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('EBA — News',                  'https://www.eba.europa.eu/news-press/news/rss.xml',             'European Banking Authority — bank regulation and prudential rules.',             1.4, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('ESMA — News',                 'https://www.esma.europa.eu/rss.xml',                            'European Securities and Markets Authority.',                                     1.3, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance'));

-- Top-tier fintech trade press
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('Finextra — Headlines',        'https://www.finextra.com/rss/headlines.aspx',                   'Daily fintech news with strong lending / payments coverage.',                    1.2, TRUE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('PYMNTS',                      'https://www.pymnts.com/feed/',                                  'Payments, BNPL and embedded-finance commentary.',                                1.1, TRUE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('Banking Dive',                'https://www.bankingdive.com/feeds/news/',                       'US banking industry news with regulator / fintech tilt.',                        1.1, TRUE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('Fintech Futures',             'https://www.fintechfutures.com/feed/',                          'Global fintech feature coverage — banking platforms and core systems.',          1.0, TRUE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('The Financial Brand',         'https://thefinancialbrand.com/feed/',                           'Retail banking, digital strategy, embedded finance.',                            1.0, TRUE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('Fintech Nexus',               'https://www.fintechnexus.com/feed/',                            'Formerly Lend Academy — digital lending, alt-data, BNPL.',                       1.1, TRUE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('Finovate',                    'https://finovate.com/feed/',                                    'Demos and announcements from fintech vendors.',                                  0.9, TRUE, (SELECT id FROM signalPulse.category WHERE name='Technology & Innovation'));

-- Risk / credit / research
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('Risk.net — Credit Risk',      'https://www.risk.net/credit-risk/feed',                         'Specialist credit-risk research and regulatory commentary.',                     1.3, TRUE, (SELECT id FROM signalPulse.category WHERE name='Research & Analysis')),
    ('S&P Global Market Intel',     'https://www.spglobal.com/marketintelligence/en/news-insights/rss',  'Bank ratings, credit research, NPL coverage.',                              1.2, TRUE, (SELECT id FROM signalPulse.category WHERE name='Research & Analysis'));

-- South Asia / India / regional
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('Inc42 — Fintech',             'https://inc42.com/buzz/fintech/feed/',                          'India fintech / digital lending coverage.',                                      1.1, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regional / South Asia')),
    ('Moneycontrol — Personal Fin', 'https://www.moneycontrol.com/rss/personalfinance.xml',          'Indian retail credit and lending coverage.',                                     0.9, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regional / South Asia')),
    ('Economic Times — BFSI',       'https://bfsi.economictimes.indiatimes.com/rss/topstories',      'Indian banking, financial services and insurance daily.',                        1.0, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regional / South Asia'));

-- ============================================================
-- HTML PAGES (scraped via CSS selectors)
-- Selectors are best-effort — verify against the current DOM
-- via "View Page Source" if a scrape returns 0 elements.
-- ============================================================
INSERT IGNORE INTO signalPulse.html_page (name, url, description, type, list_selector, title_selector, link_selector, date_selector, trust, enabled, category_id) VALUES
    ('NRB — Notices',
     'https://www.nrb.org.np/category/notices/',
     'Nepal Rastra Bank notices and circulars.',
     'html_list',
     'article.post, .post-list .post-item, .entry-summary',
     'h2 a, h3 a, .entry-title a',
     'h2 a, h3 a, .entry-title a',
     '.entry-date, time, .post-date',
     1.5, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regional / South Asia')),
    ('NRB — Press releases',
     'https://www.nrb.org.np/category/press-release/',
     'Nepal Rastra Bank press releases.',
     'html_list',
     'article.post, .post-list .post-item',
     'h2 a, h3 a, .entry-title a',
     'h2 a, h3 a, .entry-title a',
     '.entry-date, time',
     1.5, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regional / South Asia')),
    ('SEBON Nepal — News',
     'https://www.sebon.gov.np/news',
     'Securities Board of Nepal — capital market and fintech licensing.',
     'html_list',
     '.news-item, table.news-table tr, .post',
     'a.news-title, h3 a, td.title a',
     'a.news-title, h3 a, td.title a',
     'span.news-date, td.date',
     1.3, TRUE, (SELECT id FROM signalPulse.category WHERE name='Regional / South Asia'));

-- ============================================================
-- TOPIC RULES
-- Each rule adds `weight` once per article when any pattern matches
-- (case-insensitive). Keep patterns tight to avoid noise.
-- ============================================================
INSERT IGNORE INTO signalPulse.topic_rule (topic_key, description, weight, active) VALUES
    ('digital_lending',     'Digital lending platforms, online loan origination, P2P lending',          8.0, TRUE),
    ('bnpl',                'Buy Now Pay Later, instalment payments, split-pay',                        8.0, TRUE),
    ('embedded_finance',    'Embedded finance, BaaS, banking-as-a-service, API banking',                7.0, TRUE),
    ('credit_risk',         'Credit risk, underwriting, credit bureau, NPLs, default risk',             7.0, TRUE),
    ('alt_data_uw',         'Alternative data, cash-flow underwriting, open banking data',              5.0, TRUE),
    ('ai_in_lending',       'AI / ML used in lending, underwriting, credit decisioning',                5.0, TRUE),
    ('nrb_regulation',      'Nepal Rastra Bank guidelines, circulars and directives',                   8.0, TRUE),
    ('global_lending_reg',  'BNPL / consumer-credit / fair-lending regulations and supervisory action', 6.0, TRUE),
    ('aml_kyc',             'KYC, AML / CFT, customer due diligence, fraud prevention',                 3.0, TRUE),
    ('open_banking',        'Open banking, PSD2, account-to-account, data sharing',                     4.0, TRUE),
    ('digital_wallets',     'Digital wallets, neobanks, super-apps, CBDC, stablecoins',                 3.0, TRUE),
    ('npl_credit_quality',  'Non-performing loans, provisioning, stage-3 loans, charge-offs',           4.0, TRUE),
    ('fintech_funding',     'Fintech funding rounds, M&A, IPOs — high-signal market events',            2.0, TRUE);

-- Patterns per rule
-- digital_lending
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(digital lending|online lending|loan origination|lending platform|p2p lending|peer.?to.?peer lending|digital loan)\\b' AS p) x
WHERE topic_key='digital_lending';

-- bnpl
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(bnpl|buy.?now.?pay.?later|pay.?in.?4|split.?payment|instal+ment payments?)\\b' AS p) x
WHERE topic_key='bnpl';

-- embedded_finance
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(embedded (finance|lending|banking|payments)|banking.?as.?a.?service|baas|api.?banking|banking.?apis?)\\b' AS p) x
WHERE topic_key='embedded_finance';

-- credit_risk
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(credit (risk|scoring|underwriting|bureau|decisioning)|risk.?based pricing|default risk|loss given default|probability of default)\\b' AS p) x
WHERE topic_key='credit_risk';

-- alt_data_uw
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(alternative data|alt.?data|cash.?flow underwriting|psd2 data|open banking data|transaction-?based scoring)\\b' AS p) x
WHERE topic_key='alt_data_uw';

-- ai_in_lending
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(ai|machine learning|ml|generative ai|llm)\\b.{0,40}\\b(lending|underwriting|credit|risk|loan)\\b' AS p) x
WHERE topic_key='ai_in_lending';

-- nrb_regulation
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(nrb|nepal rastra bank)\\b.{0,40}\\b(circular|guideline|directive|framework|policy|notice|monetary policy)\\b' AS p) x
WHERE topic_key='nrb_regulation';

-- global_lending_reg
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(consumer (credit|finance|protection)|fair lending|usury|bnpl regulation|cfpb|fca|esma|eba|rbi|reserve bank of india)\\b' AS p) x
WHERE topic_key='global_lending_reg';

-- aml_kyc
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(kyc|aml|cft|anti.?money.?laundering|know.?your.?customer|customer due diligence|sanctions screening)\\b' AS p) x
WHERE topic_key='aml_kyc';

-- open_banking
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(open banking|open finance|psd2|psd3|account.?to.?account|a2a payments?)\\b' AS p) x
WHERE topic_key='open_banking';

-- digital_wallets
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(digital wallet|neobank|challenger bank|super.?app|cbdc|central bank digital currency|stablecoin)\\b' AS p) x
WHERE topic_key='digital_wallets';

-- npl_credit_quality
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(non.?performing loans?|npls?|stage.?3 loans?|loan loss provisions?|charge.?offs?|delinquenc(y|ies))\\b' AS p) x
WHERE topic_key='npl_credit_quality';

-- fintech_funding
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(series [a-e] funding|raises [$€£]\\d|acquir(es|ed|ition)|files? for ipo)\\b' AS p) x
WHERE topic_key='fintech_funding';
