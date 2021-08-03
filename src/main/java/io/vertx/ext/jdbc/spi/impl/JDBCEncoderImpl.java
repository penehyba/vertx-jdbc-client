package io.vertx.ext.jdbc.spi.impl;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.impl.actions.JDBCStatementHelper;
import io.vertx.ext.jdbc.spi.JDBCEncoder;

import java.sql.JDBCType;
import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public class JDBCEncoderImpl implements JDBCEncoder {

  private static final Logger log = LoggerFactory.getLogger(JDBCEncoder.class);

  private boolean castUUID;
  private boolean castDate;
  private boolean castTime;
  private boolean castDatetime;

  @Override
  public JDBCEncoder setup(boolean castUUID, boolean castDate, boolean castTime, boolean castDatetime) {
    this.castUUID = castUUID;
    this.castDate = castDate;
    this.castTime = castTime;
    this.castDatetime = castDatetime;
    return this;
  }

  @Override
  public Object convert(ParameterMetaData metaData, int pos, JsonArray input) throws SQLException {
    return this.convert(JDBCType.valueOf(metaData.getParameterType(pos)), input.getValue(pos - 1));
  }

  protected Object convert(JDBCType jdbcType, Object javaValue) throws SQLException {
    if (javaValue == null) {
      return null;
    }
    if (isAbleToUUID(jdbcType) && javaValue instanceof String && castUUID && JDBCStatementHelper.UUID.matcher((String) javaValue).matches()) {
      return debug(jdbcType, java.util.UUID.fromString((String) javaValue));
    }
    try {
      JDBCStatementHelper.LOOKUP_SQL_DATETIME.apply(jdbcType);
      return debug(jdbcType, castDateTime(jdbcType, javaValue));
    } catch (IllegalArgumentException e) {
      //ignore
    }
    return debug(jdbcType, javaValue);
  }

  protected boolean isAbleToUUID(JDBCType jdbcType) {
    return jdbcType == JDBCType.BINARY || jdbcType == JDBCType.VARBINARY || jdbcType == JDBCType.OTHER;
  }

  protected Object castDateTime(JDBCType jdbcType, Object value) {
    if (jdbcType == JDBCType.DATE) {
      if (value instanceof String && castDate) {
        return LocalDate.parse((String) value);
      }
      if (value instanceof java.util.Date) {
        return ((java.util.Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      }
      return value;
    }
    if (jdbcType == JDBCType.TIME_WITH_TIMEZONE) {
      if (value instanceof String && castTime) {
        return OffsetTime.parse((String) value);
      }
      return value;
    }
    if (jdbcType == JDBCType.TIME) {
      if (value instanceof String && castTime) {
        try {
          return LocalTime.parse((String) value);
        } catch (DateTimeParseException e) {
          return OffsetTime.parse((String) value).withOffsetSameInstant(ZoneOffset.UTC).toLocalTime();
        }
      }
      return value;
    }
    if (jdbcType == JDBCType.TIMESTAMP_WITH_TIMEZONE) {
      if (value instanceof String && castDatetime) {
        return OffsetDateTime.parse((String) value);
      }
      return value;
    }
    if (jdbcType == JDBCType.TIMESTAMP) {
      if (value instanceof String && castDatetime) {
        try {
          return LocalDateTime.parse((String) value);
        } catch (DateTimeParseException e) {
          return OffsetDateTime.parse((String) value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        }
      }
      return value;
    }
    throw new IllegalArgumentException("Invalid Date Time JDBC Type");
  }

  protected Object debug(JDBCType jdbcType, Object javaValue) {
    log.debug("Convert JDBC type [" + jdbcType + "][" + javaValue.getClass().getName() + "]");
    return javaValue;
  }

}
