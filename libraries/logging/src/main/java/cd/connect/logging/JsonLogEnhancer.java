package cd.connect.logging;

import java.util.List;
import java.util.Map;

/**
 * These are made available by Services. I am doing this to force proper modularisation
 * because so many people (e.g. Spring) are lazy about this.
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public interface JsonLogEnhancer {
  /*
   * The ordering of the priority of this processor.
   */
  int getMapPriority();

  /**
   * translates information from the context into the log. Each subsystem that stores data in the context
   * should know how to map it to the log.
   *
   * Generic ones are provided (json: for example)
   *
   * @param context - the original context (from ConnectContext) - items should be removed as they are consumed
   * @param log - the json object being logged out - this is a Map
   * @param alreadyEncodedJsonObjects - json objects that are already encoded and need to be preserved
   */
  void map(Map<String, String> context, Map<String, Object> log, List<String> alreadyEncodedJsonObjects);

  void failed(Map<String, String> context, Map<String, Object> log, List<String> alreadyEncodedJsonObjects, Throwable e);
}
