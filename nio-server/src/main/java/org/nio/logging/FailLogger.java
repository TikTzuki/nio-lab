package org.nio.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nio.transaction.FailedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class FailLogger extends Filter<ILoggingEvent> {
  public static final Logger failLogger = LoggerFactory.getLogger("FailLogger");
  static ObjectMapper objectMapper = new ObjectMapper();

  public static void appendFail(FailedTransaction tran) {
    try {
      failLogger.error(objectMapper.writeValueAsString(tran));
    } catch (JsonProcessingException e) {
      failLogger.error(tran.toString());
    }
  }

  @Override
  public FilterReply decide(ILoggingEvent iLoggingEvent) {
    if (Objects.equals(iLoggingEvent.getLoggerName(), failLogger.getName()))
      return FilterReply.NEUTRAL;
    return FilterReply.DENY;
  }
}
