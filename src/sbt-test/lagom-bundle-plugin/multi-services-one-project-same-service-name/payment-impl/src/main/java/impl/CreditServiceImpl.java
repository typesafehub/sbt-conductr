package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import api.CreditService;

import akka.stream.javadsl.Source;

public class CreditServiceImpl implements CreditService {

  @Override
  public ServiceCall<NotUsed, NotUsed, NotUsed> credit() {
    return (id, request) -> {
      return CompletableFuture.completedFuture(NotUsed.getInstance());
    };
  }
}
