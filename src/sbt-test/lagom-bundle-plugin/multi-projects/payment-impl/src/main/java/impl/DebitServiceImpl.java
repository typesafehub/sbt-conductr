package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import api.DebitService;

import akka.stream.javadsl.Source;

public class DebitServiceImpl implements DebitService {

  @Override
  public ServiceCall<NotUsed, NotUsed, NotUsed> debit() {
    return (id, request) -> {
      return CompletableFuture.completedFuture(NotUsed.getInstance());
    };
  }
}
