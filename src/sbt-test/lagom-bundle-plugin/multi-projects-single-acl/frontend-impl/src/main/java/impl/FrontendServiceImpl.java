package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import api.FrontendService;

import akka.stream.javadsl.Source;

public class FrontendServiceImpl implements FrontendService {

  @Override
  public ServiceCall<NotUsed, NotUsed, NotUsed> foo() {
    return (id, request) -> {
      return CompletableFuture.completedFuture(NotUsed.getInstance());
    };
  }
}
