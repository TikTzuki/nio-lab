package com.tik;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

@Slf4j
class LoggingInterceptor implements Interceptor {
  @Override public Response intercept(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();

    long t1 = System.nanoTime();
    log.info(String.format("Sending request %s on %s%n%s",
        request.url(), chain.connection(), request.headers()));

      Response response = null;
      try {
          response = chain.proceed(request);
      } catch (IOException e) {
          throw new RuntimeException(e);
      }

      long t2 = System.nanoTime();
    log.info(String.format("Received response for %s in %.1fms%n%s",
        response.request().url(), (t2 - t1) / 1e6d, response.headers()));

    return response;
  }
}