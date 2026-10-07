package com.fc.v2.heri;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.THeriArriveBillMapper;
import com.fc.v2.mapper.auto.THeriArriveRowMapper;
import com.fc.v2.mapper.auto.THeriArriveRuleMapper;
import com.fc.v2.mapper.auto.THeriArriveRunMapper;
import com.fc.v2.mapper.auto.TSysOperLogMapper;
import com.fc.v2.model.auto.THeriArriveRule;
import com.fc.v2.service.impl.THeriArriveServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 到港与滞期提醒排期链路测试夹具：H2 内存库(MySQL 兼容) + 真实 MyBatis-Plus mapper，
 * 不启 Spring 容器（避开 Shiro/Redis/Druid 自动装配）。
 *
 * @author fuce
* @date 2026-10-07
 */
public abstract class ArriveITSupport {

    protected JdbcDataSource dataSource;
    protected SqlSessionTemplate session;

    protected THeriArriveServiceImpl arriveService;

    protected THeriArriveRuleMapper ruleMapper;
    protected THeriArriveBillMapper billMapper;
    protected THeriArriveRowMapper rowMapper;
    protected THeriArriveRunMapper runMapper;
    protected TSysOperLogMapper operLogMapper;

    protected void setUp() throws Exception {
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:mem:arrivit_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        this.dataSource.setUser("sa");
        runScript(this.dataSource);

        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(THeriArriveRuleMapper.class);
        cfg.addMapper(THeriArriveBillMapper.class);
        cfg.addMapper(THeriArriveRowMapper.class);
        cfg.addMapper(THeriArriveRunMapper.class);
        cfg.addMapper(TSysOperLogMapper.class);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(this.dataSource);
        factoryBean.setConfiguration(cfg);
        SqlSessionFactory factory = factoryBean.getObject();

        this.session = new SqlSessionTemplate(factory);
        this.ruleMapper = this.session.getMapper(THeriArriveRuleMapper.class);
        this.billMapper = this.session.getMapper(THeriArriveBillMapper.class);
        this.rowMapper = this.session.getMapper(THeriArriveRowMapper.class);
        this.runMapper = this.session.getMapper(THeriArriveRunMapper.class);
        this.operLogMapper = this.session.getMapper(TSysOperLogMapper.class);

        this.arriveService = new THeriArriveServiceImpl();
        ReflectionTestUtils.setField(this.arriveService, "arrRuleMapper", this.ruleMapper);
        ReflectionTestUtils.setField(this.arriveService, "arrBillMapper", this.billMapper);
        ReflectionTestUtils.setField(this.arriveService, "arrRowMapper", this.rowMapper);
        ReflectionTestUtils.setField(this.arriveService, "arrRunMapper", this.runMapper);
        ReflectionTestUtils.setField(this.arriveService, "sysOperLogMapper", this.operLogMapper);

        seedRules();
    }

    /** 港里判据：AR01 提前3天在用；另有撤下/过期/空/负/大数各一条。 */
    protected void seedRules() {
        rule("AR01", 3, "2020-01-01 00:00:00", "2099-12-31 23:59:59", 0);
        rule("AR09", 5, "2020-01-01 00:00:00", "2099-12-31 23:59:59", 1); // 撤下
        rule("AR08", 5, "2020-01-01 00:00:00", "2026-10-01 00:00:00", 0); // 已过期(截止不含)
        rule("AR99", null, "2020-01-01 00:00:00", "2099-12-31 23:59:59", 0); // 那格空着
        rule("AR77", -3, "2020-01-01 00:00:00", "2099-12-31 23:59:59", 0);  // 负数
        rule("AR88", 99999, "2020-01-01 00:00:00", "2099-12-31 23:59:59", 0); // 说不上来的大数
    }

    protected void rule(String code, Integer leadDays, String start, String end, int status) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            THeriArriveRule r = new THeriArriveRule();
            r.setRuleCode(code);
            r.setRuleName(code + "-提醒判据");
            r.setLeadDays(leadDays);
            r.setEffStart(f.parse(start));
            r.setEffEnd(f.parse(end));
            r.setPriority(10);
            r.setStatus(status);
            r.setDelFlag(0);
            r.setCreateBy("seed");
            r.setCreateTime(new Date());
            this.ruleMapper.insert(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static BigDecimal money(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static void runScript(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             Reader r = new InputStreamReader(
                     ArriveITSupport.class.getResourceAsStream("/schema-h2.sql"), "UTF-8")) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            for (String sql : sb.toString().split(";")) {
                if (!sql.trim().isEmpty()) {
                    st.execute(sql);
                }
            }
        }
    }
}
