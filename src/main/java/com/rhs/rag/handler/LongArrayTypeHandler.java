package com.rhs.rag.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.jdbc.PgArray;

import java.sql.*;

/**
 * 自定义 Long[] 的 TypeHandler（针对 PostgreSQL BIGINT[]）
 */
@MappedTypes(Long[].class)
public class LongArrayTypeHandler extends BaseTypeHandler<Long[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    Long[] parameter, JdbcType jdbcType) throws SQLException {
        // 将 Long[] 转为 PostgreSQL 数组字面量，例如 "{1,2,3}"
        StringBuilder sb = new StringBuilder("{");
        for (int j = 0; j < parameter.length; j++) {
            if (j > 0) sb.append(",");
            sb.append(parameter[j]);
        }
        sb.append("}");
        ps.setObject(i, sb.toString(), Types.OTHER);
    }

    @Override
    public Long[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getArray(columnName));
    }

    @Override
    public Long[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getArray(columnIndex));
    }

    @Override
    public Long[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getArray(columnIndex));
    }

    private Long[] parse(Array array) throws SQLException {
        if (array == null) return null;
        Object obj = array.getArray();
        if (obj instanceof Long[]) {
            return (Long[]) obj;
        }
        if (obj instanceof Object[]) {
            Object[] objs = (Object[]) obj;
            Long[] result = new Long[objs.length];
            for (int i = 0; i < objs.length; i++) {
                result[i] = ((Number) objs[i]).longValue();
            }
            return result;
        }
        // 兜底：PostgreSQL 驱动对 BIGINT[] 在某些情况下返回基本类型数组（如 long[]），
        // 它既不是 Long[] 也不是 Object[]，旧实现会直接返回 null 导致数据读不出来
        if (obj != null && obj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            Long[] result = new Long[len];
            for (int i = 0; i < len; i++) {
                Object item = java.lang.reflect.Array.get(obj, i);
                result[i] = ((Number) item).longValue();
            }
            return result;
        }
        return null;
    }
}