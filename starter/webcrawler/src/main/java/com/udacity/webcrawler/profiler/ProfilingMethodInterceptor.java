package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {
  private final Object target;
  private final ProfilingState profilingState;
  private final Clock clock;

  ProfilingMethodInterceptor(Object target, ProfilingState profilingState, Clock clock) {
    this.target = target;
    this.profilingState = profilingState;
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Instant start = clock.instant();

    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    } finally {
      Instant end = clock.instant();
      Duration duration = Duration.between(start, end);
      profilingState.record(target.getClass(), method, duration);
    }
  }
}