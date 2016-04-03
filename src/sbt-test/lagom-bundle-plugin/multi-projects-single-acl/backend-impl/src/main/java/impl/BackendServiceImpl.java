package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import api.BackendService;

import akka.stream.javadsl.Source;

public class BackendServiceImpl implements BackendService {

  @Override
  public ServiceCall<NotUsed, NotUsed, NotUsed> bar() {
    return (id, request) -> {
      return CompletableFuture.completedFuture(NotUsed.getInstance());
    };
  }
}
