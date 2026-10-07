-- H2(MySQL 兼容模式) 测试库：只建签发作业链路触及的六张表，列与实体逐列对齐
CREATE TABLE IF NOT EXISTS t_heri_job (
  id BIGINT NOT NULL,
  job_no VARCHAR(32),
  job_name VARCHAR(64),
  status INT,
  total_rows INT,
  done_rows INT,
  fail_rows INT,
  eff_start DATETIME,
  eff_end DATETIME,
  version VARCHAR(32),
  row_no INT,
  bl_no VARCHAR(64),
  amount DECIMAL(14,2),
  job_qty INT,
  version_status INT,
  writeback_flag INT,
  compensate_flag INT,
  compensated INT,
  compensate_cnt INT,
  fail_reason VARCHAR(255),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_job_head UNIQUE (job_no, row_no)
);

CREATE TABLE IF NOT EXISTS t_heri_job_row (
  id BIGINT NOT NULL,
  job_no VARCHAR(32),
  row_no INT,
  bl_no VARCHAR(64),
  amount DECIMAL(14,2),
  job_qty INT,
  version VARCHAR(32),
  version_status INT,
  writeback_flag INT,
  compensate_flag INT,
  compensated INT,
  compensate_cnt INT,
  fail_reason VARCHAR(255),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_job_row UNIQUE (job_no, row_no)
);

CREATE TABLE IF NOT EXISTS t_heri_del_row (
  id BIGINT NOT NULL,
  item_no VARCHAR(64),
  due_at DATETIME,
  amount DECIMAL(14,2),
  notify_type VARCHAR(128),
  ship_name VARCHAR(128),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_heri_adv_row (
  id BIGINT NOT NULL,
  item_no VARCHAR(64),
  biz_no VARCHAR(64),
  amount DECIMAL(14,2),
  settle_type VARCHAR(128),
  ship_name VARCHAR(128),
  stage INT,
  content VARCHAR(255),
  last_action VARCHAR(32),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_sys_oper_log (
  id BIGINT NOT NULL,
  title VARCHAR(255),
  method VARCHAR(255),
  oper_name VARCHAR(255),
  oper_url VARCHAR(255),
  oper_param VARCHAR(255),
  error_msg VARCHAR(255),
  oper_time DATETIME,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_heri_cus_book (
  id BIGINT NOT NULL,
  book_no VARCHAR(40),
  biz_no VARCHAR(64),
  source VARCHAR(64),
  declare_rows INT,
  in_rows INT DEFAULT 0,
  signed_rows INT DEFAULT 0,
  back_rows INT DEFAULT 0,
  received_flag INT DEFAULT 0,
  status INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_cus_book_no UNIQUE (book_no),
  CONSTRAINT uk_cus_book_biz UNIQUE (biz_no, source)
);

CREATE TABLE IF NOT EXISTS t_heri_cus_row (
  id BIGINT NOT NULL,
  book_no VARCHAR(40),
  row_no INT,
  entry_id VARCHAR(64),
  declare_qty DECIMAL(14,2),
  signed_qty DECIMAL(14,2),
  device_kind VARCHAR(32),
  ship_name VARCHAR(128),
  status INT,
  intake_seq INT DEFAULT 1,
  back_reason VARCHAR(255),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_cus_row_no UNIQUE (book_no, row_no),
  CONSTRAINT uk_cus_entry UNIQUE (book_no, entry_id)
);

CREATE TABLE IF NOT EXISTS t_heri_cus_kind_rule (
  id BIGINT NOT NULL,
  kind_code VARCHAR(32),
  kind_name VARCHAR(64),
  status INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_cus_kind_code UNIQUE (kind_code)
);

CREATE TABLE IF NOT EXISTS t_heri_bl_rule (
  id BIGINT NOT NULL,
  rule_code VARCHAR(32),
  rule_name VARCHAR(64),
  th1_max DECIMAL(14,2),
  th2_max DECIMAL(14,2),
  th3_max DECIMAL(14,2),
  eff_start DATETIME,
  eff_end DATETIME,
  priority INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_heri_del_grade (
  id BIGINT NOT NULL,
  grade_code VARCHAR(32),
  grade_name VARCHAR(64),
  biz_mode VARCHAR(32),
  qty_min DECIMAL(14,2),
  qty_max DECIMAL(14,2),
  gates INT,
  priority INT DEFAULT 0,
  status INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_del_grade_code UNIQUE (grade_code)
);

CREATE TABLE IF NOT EXISTS t_heri_del_bill (
  id BIGINT NOT NULL,
  bill_no VARCHAR(40),
  grade_code VARCHAR(32),
  grade_name VARCHAR(64),
  biz_mode VARCHAR(32),
  qty DECIMAL(14,2),
  gate_count INT,
  round_no INT DEFAULT 1,
  final_round_no INT,
  status INT DEFAULT 0,
  cur_gate INT DEFAULT 1,
  rollback_cnt INT DEFAULT 0,
  sealed_time DATETIME,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_del_bill_no UNIQUE (bill_no)
);

CREATE TABLE IF NOT EXISTS t_heri_del_gate (
  id BIGINT NOT NULL,
  bill_no VARCHAR(40),
  round_no INT,
  gate_no INT,
  gate_total INT,
  signer VARCHAR(64),
  sign_action VARCHAR(64),
  land_time DATETIME,
  land_seq BIGINT,
  status INT,
  trail_ok INT DEFAULT 0,
  void_reason VARCHAR(255),
  superseded_by BIGINT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE INDEX idx_del_gate_bill ON t_heri_del_gate (bill_no, round_no, gate_no);

-- ============================================================================
-- 到港与滞期提醒排期：提前几天看判据；同单多条先改定晚、同晚看到口近；
-- 发不顺的跳过接着走下一条；册上没条目整趟跳空写“无”。
-- ============================================================================

-- 港里判据：提前量那格写几天就几天；空着按一日；写负数/说不上来的大数按空着办（不是零，也不是一年半载）。
CREATE TABLE IF NOT EXISTS t_heri_remind_rule (
  id BIGINT NOT NULL,
  rule_code VARCHAR(32),
  rule_name VARCHAR(64),
  lead_days INT,
  status INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_remind_rule_code UNIQUE (rule_code)
);

-- 提醒条目册：往年入册的老提醒一条不减。同单多条按 (定得晚, 到口近, 册上序) 比，一天只发一条。
CREATE TABLE IF NOT EXISTS t_heri_remind_row (
  id BIGINT NOT NULL,
  bill_no VARCHAR(40),
  row_no INT,
  ship_name VARCHAR(128),
  due_at DATETIME,
  lead_days INT,
  rule_code VARCHAR(32),
  revised_at DATETIME,
  last_dispatch_no VARCHAR(64),
  sent_flag INT DEFAULT 0,
  fail_reason VARCHAR(255),
  skip_flag INT DEFAULT 0,
  after_cancel_flag INT DEFAULT 0,
  status INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_remind_row UNIQUE (bill_no, row_no)
);

CREATE INDEX idx_remind_row_bill ON t_heri_remind_row (bill_no);

-- 真发出去的提醒：append-only，作废也留着。同一天同一条不许连发两回。
CREATE TABLE IF NOT EXISTS t_heri_remind_dispatch (
  id BIGINT NOT NULL,
  dispatch_no VARCHAR(64),
  run_no VARCHAR(64),
  bill_no VARCHAR(40),
  row_no INT,
  ship_name VARCHAR(128),
  due_at DATETIME,
  remind_at DATETIME,
  status INT,
  fail_reason VARCHAR(255),
  after_cancel_flag INT DEFAULT 0,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  CONSTRAINT uk_remind_dispatch_no UNIQUE (dispatch_no)
);

CREATE INDEX idx_remind_dispatch_row ON t_heri_remind_dispatch (bill_no, row_no);

-- 趟头：一趟认多条要分页拉，拉到一半超时停下留一笔，续的时候接着拉。
CREATE TABLE IF NOT EXISTS t_heri_remind_run (
  id BIGINT NOT NULL,
  run_no VARCHAR(64),
  bill_nos VARCHAR(1000),
  status INT,
  fetched_rows INT DEFAULT 0,
  sent_rows INT DEFAULT 0,
  failed_rows INT DEFAULT 0,
  skipped_rows INT DEFAULT 0,
  empty_flag INT DEFAULT 0,
  page_size INT,
  last_row_id BIGINT,
  remark VARCHAR(255),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  PRIMARY KEY (id),
  CONSTRAINT uk_remind_run_no UNIQUE (run_no)
);
