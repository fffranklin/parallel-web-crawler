package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final PageParserFactory parserFactory;
  private final Duration timeout;
  private final int popularWordCount;
  private final int maxDepth;
  private final List<Pattern> ignoreUrls;
  private final ForkJoinPool pool;

  @Inject
  ParallelWebCrawler(
      Clock clock,
      PageParserFactory parserFactory,
      @Timeout Duration timeout,
      @PopularWordCount int popularWordCount,
      @MaxDepth int maxDepth,
      @IgnoredUrls List<Pattern> ignoreUrls,
      @TargetParallelism int threadCount) {
    this.clock = clock;
    this.parserFactory = parserFactory;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.maxDepth = maxDepth;
    this.ignoreUrls = ignoreUrls;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = clock.instant().plus(timeout);
    Map<String, Integer> wordCounts = Collections.synchronizedMap(new HashMap<>());
    Set<String> visitedUrls = new HashSet<>();

    pool.invoke(new CrawlTask(startingUrls, deadline, maxDepth, wordCounts, visitedUrls, parserFactory));

    if (wordCounts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(wordCounts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(wordCounts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }

  private class CrawlTask extends RecursiveAction {
    private final List<String> startingUrls;
    private final Instant deadline;
    private final int maxDepth;
    private final Map<String, Integer> wordCounts;
    private final Set<String> visitedUrls;
    private final PageParserFactory parserFactory;

    public CrawlTask(List<String> startingUrls, Instant deadline,int maxDepth, Map<String, Integer> wordCounts, Set<String> visitedUrls, PageParserFactory parserFactory) {
      this.startingUrls = startingUrls;
      this.deadline = deadline;
      this.maxDepth = maxDepth;
      this.wordCounts = wordCounts;
      this.visitedUrls = visitedUrls;
      this.parserFactory = parserFactory;
    }

    @Override
    protected void compute() {
      if (startingUrls.isEmpty()) {
        return;
      }

      if (startingUrls.size() == 1) {
        crawlInternal(startingUrls.get(0), deadline, maxDepth, wordCounts, visitedUrls, parserFactory);
      } else {
        int sublistSize = startingUrls.size() / 2;
        List<List<String>> subStartingUrls = IntStream.range(0, (startingUrls.size() + sublistSize - 1) / sublistSize)
                .mapToObj(i -> startingUrls.subList(i * sublistSize, Math.min((i + 1) * sublistSize, startingUrls.size())))
                .toList();

        List<CrawlTask> subTasks = subStartingUrls.stream()
                .map(sublist -> new CrawlTask(sublist, deadline, maxDepth, wordCounts, visitedUrls, parserFactory))
                .collect(Collectors.toList());

        invokeAll(subTasks);
      }
    }

    private synchronized void crawlInternal(String url, Instant deadline, int maxDepth, Map<String, Integer> wordCounts, Set<String> visitedUrls, PageParserFactory parserFactory) {
      if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
        return;
      }

      for (Pattern pattern : ignoreUrls) {
        if (pattern.matcher(url).matches()) {
          return;
        }
      }

      if (visitedUrls.contains(url)) {
        return;
      }

      visitedUrls.add(url);
      PageParser.Result result = parserFactory.get(url).parse();

      for (Map.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
        wordCounts.merge(e.getKey(), e.getValue(), Integer::sum);
      }

      for (String link : result.getLinks()) {
        crawlInternal(link, deadline, maxDepth - 1, wordCounts, visitedUrls, parserFactory);
      }
    }
  }
}
