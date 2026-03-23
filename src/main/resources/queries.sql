INSERT INTO signalPulse.app_config (config_key,config_value) VALUES
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



INSERT INTO signalPulse.app_user (password,role,username) VALUES
                                                              ('$2a$12$F9REEx/PTTtIavijLgbxJOUe42RCA7nJl2qDKlddAAK2na/hqpFTy', 'ROLE_ADMIN','admin')



-- 1. Insert Categories with specialized weights
    INSERT INTO signalPulse.category (id, name, weight) VALUES
    (1, 'BNPL & Point of Sale', 10),
    (2, 'Embedded Finance', 9),
    (3, 'Digital Lending', 8),
    (4, 'Credit Risk & Data', 7),
    (5, 'Nano Loans & Microfinance', 6),
    (6, 'Regulatory & Central Bank', 5),
    (7, 'General Fintech', 3);

-- 2. Insert HTML Pages with mapped Categories
INSERT INTO signalPulse.html_page (id, date_selector, enabled, link_selector, list_selector, name, title_selector, trust, url, `type`, category_id) VALUES
                                                                                                                                                        (1, 'time, .posted-on time, .entry-date', 1, 'a', 'article, .hentry, .post, .type-post', 'Nepal Rastra Bank – Notices (HTML)', 'h1, h2, h3, .entry-title, .title', 0.9, 'https://www.nrb.org.np/category/notices/?department=ofg', 'html_list', 6),
                                                                                                                                                        (2, NULL, 1, 'h3 a', '.post-info', 'PYMNTS News (Lending)', 'h3 a', 0.9, 'https://www.pymnts.com/news/lending/', 'html_list', 3),
                                                                                                                                                        (3, NULL, 1, '.entry-title a', '.post', 'Finovate Blog', '.entry-title a', 0.85, 'https://finovate.com/blog/', 'html_list', 7),
                                                                                                                                                        (4, NULL, 1, '.news-item__title a', '.news-item', 'AltFi Features', '.news-item__title a', 0.85, 'https://www.altfi.com/news', 'html_list', 3),
                                                                                                                                                        (5, NULL, 1, 'h2.entry-title a', 'article', 'Crowdfund Insider', 'h2.entry-title a', 0.8, 'https://www.crowdfundinsider.com/', 'html_list', 7),
                                                                                                                                                        (6, NULL, 1, 'h4 a', '.news-item', 'The Financial Express (BD) - Fintech', 'h4 a', 0.92, 'https://thefinancialexpress.com.bd/tag/fintech', 'html_list', 7),
                                                                                                                                                        (7, NULL, 1, 'h3 a', '.card-body', 'The Business Standard (BD) - Banking', 'h3 a', 0.9, 'https://www.tbsnews.net/economy/banking', 'html_list', 7),
                                                                                                                                                        (8, NULL, 1, 'h2 a', '.news-item', 'Daily FT (Sri Lanka) - Fintech Sec', 'h2 a', 0.88, 'https://www.ft.lk/fintech/77', 'html_list', 7),
                                                                                                                                                        (9, NULL, 1, '.views-field-title a', '.views-row', 'Central Bank of Sri Lanka - News', '.views-field-title a', 0.96, 'https://www.cbsl.gov.lk/en/news', 'html_list', 6);

-- 3. Insert RSS Feeds with mapped Categories
INSERT INTO signalPulse.rss_feed (id, enabled, name, trust, url, category_id) VALUES
                                                                                  (1, 1, 'Business 360 Nepal – Banking & Finance', 0.7, 'https://www.b360nepal.com/feed/', 7),
                                                                                  (2, 1, 'Google News – Nepal lending/BNPL', 0.9, 'https://news.google.com/rss/search?q=("Nepal Rastra Bank"%20OR%20NRB)%20(lending%20OR%20loan%20OR%20credit%20OR%20BNPL)&hl=en-US&gl=US&ceid=US:en', 3),
                                                                                  (3, 1, 'IFC / World Bank – Financial', 0.95, 'https://blogs.worldbank.org/finance/rss.xml', 5),
                                                                                  (4, 1, 'MicroSave Consulting', 0.9, 'https://www.microsave.net/feed/', 5),
                                                                                  (5, 1, 'CGAP Digital Finance', 0.95, 'https://www.cgap.org/rss.xml', 5),
                                                                                  (6, 1, 'Fintech Nexus', 0.9, 'https://fintechnexus.com/feed/', 7),
                                                                                  (7, 1, 'The Financial Brand', 0.9, 'https://thefinancialbrand.com/feed/', 3),
                                                                                  (8, 1, 'Finovate RSS', 0.85, 'https://finovate.com/feed/', 7),
                                                                                  (9, 1, 'PYMNTS (All)', 0.85, 'https://www.pymnts.com/feed/', 7),
                                                                                  (10, 1, 'TechCrunch – Fintech', 0.9, 'https://techcrunch.com/tag/fintech/feed/', 7),
                                                                                  (11, 1, 'Fintech News Singapore - Nepal', 0.95, 'https://fintechnews.sg/tag/nepal/feed/', 7),
                                                                                  (12, 1, 'Fintech News Singapore - Bangladesh', 0.95, 'https://fintechnews.sg/tag/bangladesh/feed/', 7),
                                                                                  (13, 1, 'Fintech News Singapore - Sri Lanka', 0.95, 'https://fintechnews.sg/tag/sri-lanka/feed/', 7),
                                                                                  (14, 1, 'IBS Intelligence - Asia News', 0.9, 'https://ibsintelligence.com/feed/', 7),
                                                                                  (15, 1, 'Fintech Futures - Digital Banking', 0.95, 'https://www.fintechfutures.com/category/banking/feed/', 3);

-- 4. Insert Rules matching core domain goals
INSERT INTO signalPulse.topic_rule (id, active, topic_key, weight) VALUES
                                                                       (1, 1, 'bnpl_core', 6.0),
                                                                       (2, 1, 'digital_lending', 5.0),
                                                                       (3, 1, 'embedded_finance', 5.0),
                                                                       (4, 1, 'credit_scoring_data', 4.0),
                                                                       (5, 1, 'collections_recovery', 3.0),
                                                                       (6, 1, 'central_bank_regulations', 4.0),
                                                                       (7, 1, 'general_fintech', 1.0);

-- 5. Insert granular regex patterns with exactly matching IDs
INSERT INTO signalPulse.topic_rule_patterns (topic_rule_id, patterns) VALUES
                                                                          -- rule 1: BNPL
                                                                          (1, '\\bbnpl\\b'),
                                                                          (1, '\\bbuy now,? pay later\\b'),
                                                                          (1, '\\bpay in (3|4|three|four)\\b'),
                                                                          (1, '\\binstallment (loan|plan)\\b'),
                                                                          (1, '\\bpoint of sale financing\\b'),
                                                                          -- rule 2: Digital Lending
                                                                          (2, '\\b(digital|online|mobile) (lending|loan|credit)\\b'),
                                                                          (2, '\\bp2p lending\\b'),
                                                                          (2, '\\bpeer-to-peer\\b'),
                                                                          (2, '\\bmerchant cash advance\\b'),
                                                                          (2, '\\bnano loan\\b'),
                                                                          -- rule 3: Embedded Finance
                                                                          (3, '\\bembedded (finance|lending|credit|bank)\\b'),
                                                                          (3, '\\bbanking as a service\\b'),
                                                                          (3, '\\bbaas\\b'),
                                                                          (3, '\\bopen banking\\b'),
                                                                          (3, '\\bapi integration\\b'),
                                                                          -- rule 4: Credit Scoring
                                                                          (4, '\\bcredit (score|scoring|bureau|risk)\\b'),
                                                                          (4, '\\balternative data\\b'),
                                                                          (4, '\\bcibil\\b'),
                                                                          (4, '\\bdecision engine\\b'),
                                                                          -- rule 5: Collections
                                                                          (5, '\\b(debt )?(collection|recovery)\\b'),
                                                                          (5, '\\bdelinquency\\b'),
                                                                          (5, '\\bnpa\\b'),
                                                                          (5, '\\bcharge-?off\\b'),
                                                                          -- rule 6: Regulations
                                                                          (6, '\\b(nrb|nepal rastra bank|cbsl|central bank of sri lanka|bangladesh bank)\\b'),
                                                                          (6, '\\b(regulatory|compliance|circular)\\b'),
                                                                          -- rule 7: General Fintech
                                                                          (7, '\\b(fintech|neobank|neo-bank|digital bank|digital wallet)\\b');
