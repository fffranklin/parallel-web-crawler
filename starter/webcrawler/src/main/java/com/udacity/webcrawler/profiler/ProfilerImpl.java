package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

  private final Clock clock;
  private final ProfilingState state = new ProfilingState();
  private final ZonedDateTime startTime;

  @Inject
  ProfilerImpl(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.startTime = ZonedDateTime.now(clock);
  }

  /**
   * // TODO: Use a dynamic proxy (java.lang.reflect.Proxy) to "wrap" the delegate in a
   *     //       ProfilingMethodInterceptor and return a dynamic proxy from this method.
   *     //       See https://docs.oracle.com/javase/10/docs/api/java/lang/reflect/Proxy.html.
   * @param klass    the class object representing the interface of the delegate.
   * @param delegate the object that should be profiled.
   * @return
   * @param <T>
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T wrap(Class<T> klass, T delegate) {
    Objects.requireNonNull(klass);
    Objects.requireNonNull(delegate);

    boolean hasProfiledMethod = Arrays.stream(klass.getMethods())
            .anyMatch(method -> method.isAnnotationPresent(Profiled.class));

    if (!hasProfiledMethod) {
      throw new IllegalArgumentException("The wrapped interface does not contain any @Profiled methods.");
    }

    return (T) Proxy.newProxyInstance(
            klass.getClassLoader(),
            new Class<?>[]{klass},
            new ProfilingMethodInterceptor(delegate, state, clock)
    );
  }

  /**
   *  // TODO: Write the ProfilingState data to the given file path. If a file already exists at that
   *       // path, the new data should be appended to the existing file.
   * @param path the destination where the formatted data should be written.
   */
  @Override
  public void writeData(Path path) throws IOException {
    try(BufferedWriter writer = Files.newBufferedWriter(path,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND)){
      writer.write(state.toString());
      writeData(writer);
    } catch (IOException e){
      throw new IOException(e.getMessage());
    }
  }

  @Override
  public void writeData(Writer writer) throws IOException {
    writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
    writer.write(System.lineSeparator());
    state.write(writer);
    writer.write(System.lineSeparator());
  }
}