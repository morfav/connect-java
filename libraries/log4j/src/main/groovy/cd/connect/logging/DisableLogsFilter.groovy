package cd.connect.logging

import groovy.transform.CompileStatic
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.config.Node
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory
import org.apache.logging.log4j.core.filter.AbstractFilter
import org.apache.logging.log4j.message.Message
import org.slf4j.MDC

@Plugin(
	name = "DisableLogsFilter",
	category = Node.CATEGORY, elementType = Filter.ELEMENT_TYPE, printObject = true)
@CompileStatic
class DisableLogsFilter extends AbstractFilter {
	private Result action() {
		if ("true" == MDC.get("connect.disable-logs")) {
			return Result.DENY
		} else {
			return Result.NEUTRAL
		}
	}

	@Override
	Result filter(final LogEvent event) {
		return event.getContextData().containsKey("connect.disable-logs") ? Result.DENY : Result.NEUTRAL
	}

	/**
	 * Appender Filter method. The default returns NEUTRAL.
	 * @param logger the Logger.
	 * @param level The logging Level.
	 * @param marker The Marker, if any.
	 * @param msg The message, if present.
	 * @param t A throwable or null.
	 * @return The Result of filtering.
	 */
	@Override
	Result filter(final Logger logger, final Level level, final Marker marker, final Message msg,
	                     final Throwable t) {
		return action()
	}

	/**
	 * Appender Filter method. The default returns NEUTRAL.
	 * @param logger the Logger.
	 * @param level The logging Level.
	 * @param marker The Marker, if any.
	 * @param msg The message, if present.
	 * @param t A throwable or null.
	 * @return The Result of filtering.
	 */
	@Override
	 Result filter(final Logger logger, final Level level, final Marker marker, final Object msg,
	                     final Throwable t) {
		return action()
	}

	/**
	 * Appender Filter method. The default returns NEUTRAL.
	 * @param logger the Logger.
	 * @param level The logging Level.
	 * @param marker The Marker, if any.
	 * @param msg The message, if present.
	 * @param params An array of parameters or null.
	 * @return The Result of filtering.
	 */
	@Override
	Result filter(final Logger logger, final Level level, final Marker marker, final String msg,
	                     final Object... params) {
		return action()
	}

	@PluginBuilderFactory
	static Builder newBuilder() {
		return new Builder()
	}

	static class Builder extends AbstractFilter.AbstractFilterBuilder<Builder>
		implements org.apache.logging.log4j.core.util.Builder<DisableLogsFilter> {
		@Override
		DisableLogsFilter build() {
			return new DisableLogsFilter()
		}
	}
}
