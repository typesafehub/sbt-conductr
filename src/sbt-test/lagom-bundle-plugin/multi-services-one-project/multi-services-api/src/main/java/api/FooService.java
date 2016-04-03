package api;

import akka.stream.javadsl.Source;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface FooService extends Service {

  ServiceCall<NotUsed, NotUsed, NotUsed> foos();
  ServiceCall<NotUsed, NotUsed, NotUsed> foo();
  ServiceCall<NotUsed, NotUsed, NotUsed> fooFriends();

  @Override
  default Descriptor descriptor() {
    return named("fooservice").with(
      restCall(Method.GET,  "/foo", foos()),
      restCall(Method.GET,  "/foo/:id", foo()),
      restCall(Method.GET,  "/foo/:id/friends", fooFriends())
    ).withAutoAcl(true);
  }
}
