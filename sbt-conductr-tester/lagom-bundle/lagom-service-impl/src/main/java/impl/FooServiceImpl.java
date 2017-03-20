package impl;

import akka.NotUsed;
import api.FooService;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import java.util.concurrent.CompletableFuture;

public class FooServiceImpl implements FooService {

  @Override
  public ServiceCall<NotUsed, String> foo() {
    return request -> CompletableFuture.completedFuture("hardcoded-foo-response");
  }
}
