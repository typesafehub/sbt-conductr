package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import api.FeedService;

import akka.stream.javadsl.Source;

public class FeedServiceImpl implements FeedService {

  @Override
  public ServiceCall<NotUsed, NotUsed> feed() {
    return request -> CompletableFuture.completedFuture(NotUsed.getInstance());
  }
}
