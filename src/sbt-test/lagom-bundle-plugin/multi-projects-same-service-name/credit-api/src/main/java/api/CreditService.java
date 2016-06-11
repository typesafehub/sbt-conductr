package api;

import akka.stream.javadsl.Source;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface CreditService extends Service {

  ServiceCall<NotUsed, NotUsed> credit();

  @Override
  default Descriptor descriptor() {
    return named("creditservice").withCalls(
      restCall(Method.GET,  "/credit", this::credit)
    ).withAutoAcl(true);
  }
}
