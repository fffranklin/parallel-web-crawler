package com.udacity.webcrawler.main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.udacity.webcrawler.WebCrawler;
import com.udacity.webcrawler.WebCrawlerModule;
import com.udacity.webcrawler.json.ConfigurationLoader;
import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.json.CrawlResultWriter;
import com.udacity.webcrawler.json.CrawlerConfiguration;
import com.udacity.webcrawler.profiler.Profiler;
import com.udacity.webcrawler.profiler.ProfilerModule;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class WebCrawlerMain {

  private final CrawlerConfiguration config;

  private WebCrawlerMain(CrawlerConfiguration config) {
    this.config = Objects.requireNonNull(config);
  }

  @Inject
  private WebCrawler crawler;

  @Inject
  private Profiler profiler;

  /**
   *  // TODO: Write the crawl results to a JSON file (or System.out if the file name is empty)
   *  // TODO: Write the profile data to a text file (or System.out if the file name is empty)
   * @throws Exception
   */
  private void run() throws Exception {
    Guice.createInjector(new WebCrawlerModule(config), new ProfilerModule()).injectMembers(this);

    CrawlResult result = crawler.crawl(config.getStartPages());
    CrawlResultWriter resultWriter = new CrawlResultWriter(result);

    String resultPathFileName = config.getResultPath();
    writeResultPath(resultPathFileName, result, resultWriter);

    String profileOutputPath = config.getProfileOutputPath();
    profilerWriteData(profileOutputPath);
  }

  private void writeResultPath(String resultPathFileName, CrawlResult result, CrawlResultWriter resultWriter) throws IOException {
    if (!resultPathFileName.isEmpty()) {
      Path resultPath = Paths.get(resultPathFileName);
      resultWriter.write(resultPath);
    } else{
      System.out.println(" Result Path File Name is Empty!");
      try (Writer writer = new OutputStreamWriter(System.out)) {
        System.out.println(" Result Path File Name is Empty!");
        ObjectMapper mapper = new ObjectMapper();

        mapper.writeValue(writer, result);
      }
    }
  }

  private void profilerWriteData(String profileOutputPath) throws IOException {
    System.out.println(" Profile Output Path is: " + profileOutputPath);
    if(!profileOutputPath.isEmpty()) {
      Path outputPath = Paths.get(profileOutputPath);
      profiler.writeData(outputPath);
    } else{
      try (Writer writer = new OutputStreamWriter(System.out)) {
        System.out.println(" ProfileOutputPath File Name is Empty!");
        profiler.writeData(writer);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.out.println("Usage: WebCrawlerMain [starting-url]");
      return;
    }
    CrawlerConfiguration config = new ConfigurationLoader(Path.of(args[0])).load();

    new WebCrawlerMain(config).run();
  }
}
