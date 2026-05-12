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
    ('INITIAL_LOOKBACK_HOURS', '5000'),
    ('MANUAL_WINDOW',          '24'),
    ('MIN_RELEVANCE_SCORE',    '3.0'),
    ('NOTIFY_RECIPIENTS',      'narenzoshi@gmail.com'),
    ('SCAN_MAX_RETRIES',       '1'),
    ('SCAN_RETRY_INTERVAL_MS', '2000'),
    ('TOP_N_ARTICLES',         '10'),
    ('MAX_PER_SOURCE',         '3'),
    ('AUTO_DISABLE_AFTER_FAILURES', '10'),
    ('SESSION_TIMEOUT_MINS',   '1');

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
-- Focus markets (Nepal, Bangladesh, Sri Lanka) get their own buckets
-- so country-specific weighting can be tuned independently.
-- ============================================================
INSERT IGNORE INTO signalPulse.category (name, description, weight) VALUES
    ('Regulatory & Compliance', 'Central banks, financial regulators, supervisory bodies and standards organisations', 3.0),
    ('Industry News',           'High-quality fintech and banking trade publications',                                   2.0),
    ('Technology & Innovation', 'Tech stacks, cloud-native banking, APIs, AI in lending, BaaS platforms',                2.2),
    ('Research & Analysis',     'Risk research, ratings, market intelligence and academic publications',                 2.5),
    ('Nepal',                   'Nepal-specific: NRB, SEBON, Nepali fintech (eSewa, Khalti, IME Pay, Fonepay)',          2.8),
    ('Bangladesh',              'Bangladesh-specific: Bangladesh Bank, BSEC, bKash, Nagad, Rocket, Upay',                2.8),
    ('Sri Lanka',               'Sri Lanka-specific: CBSL, SEC SL, eZ Cash, Genie, FriMi, mCash',                        2.8),
    ('India / South Asia',      'India + cross-regional South Asia: RBI, UPI, NPCI, regional fintech',                   2.3);

-- ============================================================
-- RSS FEEDS
-- trust is a per-source multiplier on top of category weight.
-- 1.5 = regulator/authoritative, 1.2 = top-tier trade, 1.0 = standard, 0.8 = aggregator
-- ============================================================

-- Regulators / standards bodies (high reliability — generally don't bot-block)
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('BIS — Recent publications',   'https://www.bis.org/list/recent_publications/index.rss',        'BIS — global standard-setter for central banks. Recent publications feed.',      1.5, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('BIS — Central banker speeches','https://www.bis.org/rss/cbspeeches.rss',                       'BIS — speeches by central bank governors and senior officials.',                 1.4, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('Federal Reserve — Press',     'https://www.federalreserve.gov/feeds/press_all.xml',            'US Federal Reserve press releases and supervisory communications.',              1.5, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('FCA UK — News',               'https://www.fca.org.uk/news/rss.xml',                           'UK Financial Conduct Authority — BNPL and consumer credit rules.',               1.4, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('EBA — News',                  'https://www.eba.europa.eu/news-press/news/rss.xml',             'European Banking Authority — bank regulation and prudential rules.',             1.4, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('ESMA — News',                 'https://www.esma.europa.eu/rss.xml',                            'European Securities and Markets Authority.',                                     1.3, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance')),
    ('World Bank — Blogs',          'https://blogs.worldbank.org/feed',                              'World Bank thematic blogs (finance, development, digital economy).',             1.2, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Regulatory & Compliance'));

-- Fintech trade press
--   enabled=TRUE → known to work with our header set as of the last curation pass
--   enabled=FALSE → publisher uses aggressive bot detection (Cloudflare/Datadome);
--   keeps the source in the catalog so an operator can opt in if they have an
--   IP / proxy that gets through.
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('Banking Dive',                'https://www.bankingdive.com/feeds/news/',                       'US banking industry news with regulator / fintech tilt.',                        1.1, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('Finovate',                    'https://finovate.com/feed/',                                    'Demos and announcements from fintech vendors.',                                  0.9, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Technology & Innovation')),
    ('Fintech News Singapore',      'https://fintechnews.sg/feed/',                                  'South-east Asia fintech coverage.',                                              1.0, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('Finextra — Headlines',        'https://www.finextra.com/rss/headlines.aspx',                   'Daily fintech news. Bot-blocks frequently — enable if your IP is not flagged.',  1.2, FALSE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('PYMNTS',                      'https://www.pymnts.com/feed/',                                  'Payments / BNPL. Bot-blocks frequently.',                                        1.1, FALSE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('Fintech Futures',             'https://www.fintechfutures.com/feed/',                          'Global fintech features. Bot-blocks frequently.',                                1.0, FALSE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('The Financial Brand',         'https://thefinancialbrand.com/feed/',                           'Retail banking strategy. Bot-blocks frequently.',                                1.0, FALSE, (SELECT id FROM signalPulse.category WHERE name='Industry News')),
    ('Fintech Nexus',               'https://www.fintechnexus.com/feed/',                            'Formerly Lend Academy — digital lending, alt-data, BNPL. URL changes often.',    1.1, FALSE, (SELECT id FROM signalPulse.category WHERE name='Industry News'));

-- Technology & Innovation — tech stacks, BaaS platforms, AI/cloud in fintech
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('TechCrunch — Fintech',        'https://techcrunch.com/category/fintech/feed/',                 'Fintech product launches, funding, partnerships, tech-stack moves.',             1.0, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Technology & Innovation')),
    ('a16z — Fintech',              'https://a16z.com/feed/',                                        'Andreessen Horowitz commentary on fintech infrastructure and emerging models.',  1.0, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Technology & Innovation')),
    ('Sifted',                      'https://sifted.eu/feed',                                        'European fintech with deep coverage of BNPL, embedded finance, neobanks.',       1.0, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Technology & Innovation')),
    ('AltFi',                       'https://www.altfi.com/rss',                                     'Alternative finance: digital lending, P2P, embedded credit, BNPL.',              1.1, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Technology & Innovation'));

-- India / South Asia — RBI orbit and India-specific fintech
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('Inc42 — Fintech',             'https://inc42.com/buzz/fintech/feed/',                          'India fintech / digital lending coverage.',                                      1.1, TRUE,  (SELECT id FROM signalPulse.category WHERE name='India / South Asia')),
    ('Moneycontrol — Personal Fin', 'https://www.moneycontrol.com/rss/personalfinance.xml',          'Indian retail credit and lending coverage.',                                     0.9, TRUE,  (SELECT id FROM signalPulse.category WHERE name='India / South Asia')),
    ('Economic Times — BFSI',       'https://bfsi.economictimes.indiatimes.com/rss/topstories',      'Indian banking, financial services and insurance daily.',                        1.0, TRUE,  (SELECT id FROM signalPulse.category WHERE name='India / South Asia')),
    ('Entrackr',                    'https://entrackr.com/feed/',                                    'India startup + fintech newsroom — funding, regulation, product moves.',         1.0, TRUE,  (SELECT id FROM signalPulse.category WHERE name='India / South Asia')),
    ('The Hindu BusinessLine',      'https://www.thehindubusinessline.com/feeder/default.rss',       'Indian business daily with strong banking / NBFC coverage.',                     0.9, TRUE,  (SELECT id FROM signalPulse.category WHERE name='India / South Asia'));

-- Nepal — RSS feeds (HTML scrapes for NRB / SEBON are in the HTML PAGES section below)
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('Kathmandu Post — Money',      'https://kathmandupost.com/rss',                                 'Nepali English daily; business desk covers NRB policy and digital banking.',     1.2, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Nepal')),
    ('Online Khabar (English)',     'https://english.onlinekhabar.com/feed',                         'Nepali online daily — fintech, mobile wallets, NRB notices.',                    1.0, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Nepal')),
    ('New Business Age — Nepal',    'https://www.newbusinessage.com/feed',                           'Nepal business magazine — banking sector deep-dives.',                           1.1, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Nepal')),
    ('The Himalayan Times',         'https://thehimalayantimes.com/feed/',                           'Nepali English daily; mixed coverage but catches major financial stories.',      0.9, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Nepal'));

-- Bangladesh — RSS feeds
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('The Daily Star — Business',   'https://www.thedailystar.net/business/rss.xml',                 'Bangladesh English daily — banking, fintech, BB policy.',                        1.2, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Bangladesh')),
    ('The Financial Express BD',    'https://thefinancialexpress.com.bd/rss',                        'Bangladesh business daily focused on finance and banking.',                      1.1, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Bangladesh')),
    ('Future Startup',              'https://futurestartup.com/feed/',                               'Bangladesh tech / fintech startup coverage (bKash, Nagad, Pathao Pay).',         1.1, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Bangladesh')),
    ('Dhaka Tribune — Business',    'https://www.dhakatribune.com/feed',                             'Bangladesh English daily — broad business with fintech bursts.',                 0.9, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Bangladesh'));

-- Sri Lanka — RSS feeds (CBSL HTML scrape is in the HTML PAGES section below)
INSERT IGNORE INTO signalPulse.rss_feed (name, url, description, trust, enabled, category_id) VALUES
    ('Daily FT',                    'https://www.ft.lk/rss',                                         'Sri Lanka business daily — strong CBSL and capital-markets coverage.',           1.2, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Sri Lanka')),
    ('EconomyNext',                 'https://economynext.com/feed/',                                 'Sri Lanka economy & finance independent news.',                                  1.1, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Sri Lanka')),
    ('Daily News Sri Lanka',        'https://www.dailynews.lk/rss.xml',                              'Sri Lankan English daily; business pages cover BNPL and digital banking.',       0.9, TRUE,  (SELECT id FROM signalPulse.category WHERE name='Sri Lanka'));

-- Risk.net (paywalled) and S&P Global (commercial subscription) — dropped from
-- the seed. Operators with a paid subscription can re-add them manually.

-- ============================================================
-- HTML PAGES (scraped via CSS selectors)
-- Selectors are best-effort — verify against the current DOM
-- via "View Page Source" if a scrape returns 0 elements.
-- ============================================================
-- HTML pages are seeded in 'auto' discovery mode — the scanner figures out
-- the listing structure heuristically, so no selectors are needed.
INSERT IGNORE INTO signalPulse.html_page (name, url, description, type, discovery_mode, trust, enabled, category_id) VALUES
    -- Nepal
    ('NRB — Notices',
     'https://www.nrb.org.np/category/notices/',
     'Nepal Rastra Bank notices and circulars.',
     'html_list', 'auto', 1.5, TRUE, (SELECT id FROM signalPulse.category WHERE name='Nepal')),
    ('NRB — Press releases',
     'https://www.nrb.org.np/category/press-release/',
     'Nepal Rastra Bank press releases.',
     'html_list', 'auto', 1.5, TRUE, (SELECT id FROM signalPulse.category WHERE name='Nepal')),
    ('NRB — Circulars (BFRD)',
     'https://www.nrb.org.np/category/bfrd-circulars/',
     'NRB Bank & Financial Institutions Regulation Department circulars — most directly relevant to lending policy.',
     'html_list', 'auto', 1.6, TRUE, (SELECT id FROM signalPulse.category WHERE name='Nepal')),
    ('SEBON Nepal — News',
     'https://www.sebon.gov.np/news',
     'Securities Board of Nepal — capital market and fintech licensing.',
     'html_list', 'auto', 1.3, TRUE, (SELECT id FROM signalPulse.category WHERE name='Nepal')),

    -- Bangladesh
    ('Bangladesh Bank — Circulars',
     'https://www.bb.org.bd/en/index.php/notices/cir_circulars',
     'Bangladesh Bank circulars page — directly publishes regulatory updates on banking and digital lending.',
     'html_list', 'auto', 1.6, TRUE, (SELECT id FROM signalPulse.category WHERE name='Bangladesh')),
    ('Bangladesh Bank — Press releases',
     'https://www.bb.org.bd/en/index.php/mediaroom/pressrelease',
     'Bangladesh Bank press releases.',
     'html_list', 'auto', 1.5, TRUE, (SELECT id FROM signalPulse.category WHERE name='Bangladesh')),
    ('BSEC — Notices',
     'https://sec.gov.bd/notices',
     'Bangladesh Securities and Exchange Commission — fintech licensing, IPOs.',
     'html_list', 'auto', 1.3, TRUE, (SELECT id FROM signalPulse.category WHERE name='Bangladesh')),

    -- Sri Lanka
    ('CBSL — Press releases',
     'https://www.cbsl.gov.lk/en/news/press-releases',
     'Central Bank of Sri Lanka press releases.',
     'html_list', 'auto', 1.6, TRUE, (SELECT id FROM signalPulse.category WHERE name='Sri Lanka')),
    ('CBSL — News',
     'https://www.cbsl.gov.lk/en/news',
     'CBSL general news including monetary policy and supervisory updates.',
     'html_list', 'auto', 1.5, TRUE, (SELECT id FROM signalPulse.category WHERE name='Sri Lanka')),
    ('SEC Sri Lanka — News',
     'https://www.sec.gov.lk/news/',
     'Securities and Exchange Commission of Sri Lanka — capital markets supervision.',
     'html_list', 'auto', 1.3, TRUE, (SELECT id FROM signalPulse.category WHERE name='Sri Lanka')),

    -- India regulators (RBI HTML — they have an RSS too but it is intermittent)
    ('RBI — Press releases',
     'https://www.rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx',
     'Reserve Bank of India press releases — covers digital lending guidelines, NBFC rules.',
     'html_list', 'auto', 1.5, TRUE, (SELECT id FROM signalPulse.category WHERE name='India / South Asia')),
    ('RBI — Notifications',
     'https://www.rbi.org.in/Scripts/NotificationUser.aspx',
     'RBI notifications — master directions, circulars to regulated entities.',
     'html_list', 'auto', 1.6, TRUE, (SELECT id FROM signalPulse.category WHERE name='India / South Asia'));

-- ============================================================
-- TOPIC RULES
-- Each rule adds `weight` once per article when any pattern matches
-- (case-insensitive). Keep patterns tight to avoid noise.
-- ============================================================
INSERT IGNORE INTO signalPulse.topic_rule (topic_key, description, weight, active) VALUES
    -- Core lending product topics
    ('digital_lending',     'Digital lending platforms, online loan origination',                       8.0, TRUE),
    ('p2p_lending',         'P2P lending, peer-to-peer credit, marketplace lending',                    7.0, TRUE),
    ('bnpl',                'Buy Now Pay Later, instalment payments, split-pay',                        8.0, TRUE),
    ('embedded_finance',    'Embedded finance, BaaS, banking-as-a-service, API banking',                7.0, TRUE),
    ('credit_risk',         'Credit risk, underwriting, credit bureau, NPLs, default risk',             7.0, TRUE),
    ('alt_data_uw',         'Alternative data, cash-flow underwriting, open banking data',              5.0, TRUE),

    -- Tech stack / innovation
    ('ai_in_lending',       'AI / ML used in lending, underwriting, credit decisioning',                6.0, TRUE),
    ('agentic_ai',          'Agentic AI / autonomous AI agents in financial services',                  5.0, TRUE),
    ('tech_stack',          'Core banking platforms, microservices, cloud-native banking infra',        4.0, TRUE),
    ('cloud_native',        'AWS / GCP / Azure adoption in lending/banking workloads',                  3.0, TRUE),
    ('open_banking',        'Open banking, PSD2/PSD3, account-to-account, open finance',                4.0, TRUE),

    -- Regulators (country-specific so we can weight Nepal/BD/SL high)
    ('nrb_regulation',      'Nepal Rastra Bank guidelines, circulars and directives',                   8.0, TRUE),
    ('bangladesh_bank',     'Bangladesh Bank circulars, BFIU notices, BSEC directives',                 8.0, TRUE),
    ('cbsl_regulation',     'Central Bank of Sri Lanka circulars and policy decisions',                 8.0, TRUE),
    ('rbi_regulation',      'RBI master directions, NBFC and digital-lending guidelines',               7.0, TRUE),
    ('global_lending_reg',  'CFPB / FCA / EBA / ESMA consumer-credit + BNPL regulations',               6.0, TRUE),

    -- Compliance / regtech
    ('aml_kyc',             'KYC, AML / CFT, customer due diligence, sanctions screening',              4.0, TRUE),
    ('regtech',             'Regulatory technology: compliance automation, transaction monitoring',     4.0, TRUE),
    ('data_privacy',        'GDPR, DPDP Act (India), PCI DSS, data residency, privacy law',             4.0, TRUE),

    -- Market structure / trends
    ('digital_wallets',     'Digital wallets, neobanks, super-apps, CBDC, stablecoins',                 4.0, TRUE),
    ('mobile_money',        'Mobile-money rails (UPI, eSewa, Khalti, bKash, Nagad, eZ Cash, mCash)',    5.0, TRUE),
    ('financial_inclusion', 'Financial inclusion, unbanked/underbanked, MFIs, micro-credit',            5.0, TRUE),
    ('innovation_trends',   'New product launches, fintech partnerships, B2B BNPL, embedded insurance', 4.0, TRUE),

    -- Risk / quality / funding
    ('npl_credit_quality',  'Non-performing loans, provisioning, stage-3 loans, charge-offs',           4.0, TRUE),
    ('fraud_detection',     'Application fraud, synthetic identity, transaction fraud detection',       3.0, TRUE),
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

-- ============================================================
-- NEW TOPIC PATTERNS (P2P, country regulators, tech stacks, trends)
-- ============================================================

-- p2p_lending
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(p2p lending|peer.?to.?peer lending|marketplace lending|lending club|prosper marketplace)\\b' AS p) x
WHERE topic_key='p2p_lending';

-- agentic_ai
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(agentic (ai|agents?)|autonomous agents?|ai agents?)\\b.{0,80}\\b(lending|underwriting|credit|risk|banking|finance|loan)\\b' AS p) x
WHERE topic_key='agentic_ai';

-- tech_stack — core banking platforms + modern infra
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(mambu|thought machine|10x banking|temenos|finacle|flexcube|finastra|nuvei|tata bancs|sopra banking)\\b' AS p) x
WHERE topic_key='tech_stack';
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(core banking|microservices|api.?first|event.?driven|kafka|kubernetes|service mesh)\\b' AS p) x
WHERE topic_key='tech_stack';

-- cloud_native
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(cloud.?native|aws|azure|google cloud|gcp)\\b.{0,60}\\b(bank|lending|fintech|core banking|migration)\\b' AS p) x
WHERE topic_key='cloud_native';

-- bangladesh_bank
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(bangladesh bank|bbpsd|bfiu|bsec)\\b.{0,40}\\b(circular|notice|guideline|directive|policy|notification)\\b' AS p) x
WHERE topic_key='bangladesh_bank';
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(bkash|nagad|rocket|upay|tap|mfs (in )?bangladesh|mobile financial services?)\\b' AS p) x
WHERE topic_key='bangladesh_bank';

-- cbsl_regulation
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(cbsl|central bank of sri lanka)\\b.{0,40}\\b(circular|guideline|directive|policy|notice|monetary policy|determination)\\b' AS p) x
WHERE topic_key='cbsl_regulation';
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(ez cash|mcash|genie|frimi|sampath vishwa|payable\\.lk|onepay\\b|helapay)\\b' AS p) x
WHERE topic_key='cbsl_regulation';

-- rbi_regulation
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(rbi|reserve bank of india)\\b.{0,40}\\b(circular|notification|master direction|guideline|policy|directive|caution)\\b' AS p) x
WHERE topic_key='rbi_regulation';
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(digital lending guidelines|fldg|first.?loss default guarantee|account aggregator|sa\\.fa\\.ree|nbfc)\\b' AS p) x
WHERE topic_key='rbi_regulation';

-- regtech
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(regtech|compliance automation|transaction monitoring|aml platform|kyc automation|sanctions monitoring|suspicious activity)\\b' AS p) x
WHERE topic_key='regtech';

-- data_privacy
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(gdpr|dpdp|digital personal data protection|pci.?dss|iso.?27001|soc.?2|data residency|data localisation|data localization)\\b' AS p) x
WHERE topic_key='data_privacy';

-- mobile_money — wallet/UPI rails across the focus markets
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(mobile money|mobile wallet|upi|qr payment|tap.?to.?pay|nfc payment|fast payments?)\\b' AS p) x
WHERE topic_key='mobile_money';
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(esewa|khalti|imepay|ime pay|fonepay|connectips|bkash|nagad|upay|ez cash|mcash|genie|gpay|google pay|paytm|phonepe|amazon pay)\\b' AS p) x
WHERE topic_key='mobile_money';

-- financial_inclusion
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(financial inclusion|unbanked|underbanked|microfinance|micro.?credit|nano loans?|smallholder|sme lending|msme financing)\\b' AS p) x
WHERE topic_key='financial_inclusion';

-- innovation_trends
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(neobank|challenger bank|super.?app|b2b bnpl|embedded insurance|earned wage access|ewa|crypto credit card)\\b' AS p) x
WHERE topic_key='innovation_trends';
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(partners with|partnership with|launches new|announces (a )?new|integrates? with)\\b.{0,60}\\b(lending|credit|payment|bnpl|fintech|banking|wallet)\\b' AS p) x
WHERE topic_key='innovation_trends';

-- fraud_detection
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(application fraud|synthetic identity|transaction fraud|fraud detection|deepfake|account takeover|first.?party fraud)\\b' AS p) x
WHERE topic_key='fraud_detection';

-- Refine digital_lending to NOT double-count P2P (move P2P signals out)
INSERT IGNORE INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns)
SELECT id, p FROM signalPulse.topic_rule, (SELECT '\\b(digital lender|digital lending app|loan app|fintech lender|sachet loans?)\\b' AS p) x
WHERE topic_key='digital_lending';
