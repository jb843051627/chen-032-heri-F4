package com.fc.v2.heri;

import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.THeriRemindDispatchMapper;
import com.fc.v2.mapper.auto.THeriRemindRowMapper;
import com.fc.v2.mapper.auto.THeriRemindRuleMapper;
import com.fc.v2.mapper.auto.THeriRemindRunMapper;
import com.fc.v2.mapper.auto.TSysOperLogMapper;
import com.fc.v2.model.auto.THeriRemindRow;
import com.fc.v2.model.auto.THeriRemindRule;
import com.fc.v2.service.impl.THeriRemindServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 到港与滞期提醒排期链路测试夹具：H2 内存库(MySQL 兼容) + 真实 MyBatis-Plus mapper，
 * 不启 Spring 容器。真发通道默认空发；测试可点条让某几条发不顺。
 *
 * @author fuce
 * @date 2026-10-07
 */
public abstract class RemindITSupport {

    protected JdbcDataSource dataSource;
    protected SqlSessionTemplate session;

    protected TestRemindService remindService;

    protected THeriRemindRuleMapper ruleMapper;
    protected THeriRemindRowMapper rowMapper;
    protected THeriRemindDispatchMapper dispatchMapper;
    protected THeriRemindRunMapper runMapper;
    protected TSysOperLogMapper operLogMapper;

    protected void setUp() throws Exception {
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:mem:remindit_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        this.dataSource.setUser("sa");
        runScript(this.dataSource);

        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(THeriRemindRuleMapper.class);
        cfg.addMapper(THeriRemindRowMapper.class);
        cfg.addMapper(THeriRemindDispatchMapper.class);
        cfg.addMapper(THeriRemindRunMapper.class);
        cfg.addMapper(TSysOperLogMapper.class);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(this.dataSource);
        factoryBean.setConfiguration(cfg);
        SqlSessionFactory factory = factoryBean.getObject();

        this.session = new SqlSessionTemplate(factory);
        this.ruleMapper = this.session.getMapper(THeriRemindRuleMapper.class);
        this.rowMapper = this.session.getMapper(THeriRemindRowMapper.class);
        this.dispatchMapper = this.session.getMapper(THeriRemindDispatchMapper.class);
        this.runMapper = this.session.getMapper(THeriRemindRunMapper.class);
        this.operLogMapper = this.session.getMapper(TSysOperLogMapper.class);

        this.remindService = new TestRemindService();
        ReflectionTestUtils.setField(this.remindService, "remindRuleMapper", this.ruleMapper);
        ReflectionTestUtils.setField(this.remindService, "remindRowMapper", this.rowMapper);
        ReflectionTestUtils.setField(this.remindService, "remindDispatchMapper", this.dispatchMapper);
        ReflectionTestUtils.setField(this.remindService, "remindRunMapper", this.runMapper);
        ReflectionTestUtils.setField(this.remindService, "sysOperLogMapper", this.operLogMapper);

        seedRules();
    }

    /** 港里判据：RR01 提前 3 天（在用）；其余提前量口径测试里随用随种。 */
    protected void seedRules() {
        rule(1L, "RR01", "提前3天(在用)", 3, 0);
    }

    protected void rule(Long id, String code, String name, Integer leadDays, int status) {
        THeriRemindRule r = new THeriRemindRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName(name);
        r.setLeadDays(leadDays);
        r.setStatus(status);
        r.setDelFlag(0);
        r.setCreateBy("seed");
        r.setCreateTime(new Date());
        this.ruleMapper.insert(r);
    }

    /** 可点条发不顺的测试用服务：fail("单:行") 点上的条发送时抛错，其余空发。 */
    protected static class TestRemindService extends THeriRemindServiceImpl {
        private final Set<String> failing = new HashSet<String>();

        void fail(String billNo, int rowNo) {
            this.failing.add(billNo + ":" + rowNo);
        }

        /** 修好发送端：下趟起这条照发（发不顺的不算发出去，下趟还能走） */
        void fixed(String billNo, int rowNo) {
            this.failing.remove(billNo + ":" + rowNo);
        }

        @Override
        protected void sendOne(THeriRemindRow row, java.util.Date remindAt) throws Exception {
            if (this.failing.contains(row.getBillNo() + ":" + row.getRowNo())) {
                throw new RuntimeException("发送端抖了一下：" + row.getBillNo() + " 第" + row.getRowNo() + "行");
            }
        }
    }

    private static void runScript(DataSource ds) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             Reader r = new InputStreamReader(
                     RemindITSupport.class.getResourceAsStream("/schema-h2.sql"), "UTF-8")) {
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
