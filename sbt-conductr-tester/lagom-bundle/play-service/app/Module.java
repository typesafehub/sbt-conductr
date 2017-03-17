import com.lightbend.lagom.javadsl.api.ServiceAcl;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.client.ServiceClientGuiceSupport;
import api.FooService;


public class Module extends AbstractModule implements ServiceClientGuiceSupport {
    @Override
    protected void configure() {
        bindServiceInfo(ServiceInfo.of("web-gateway-module", ServiceAcl.path("(?!/api/).*")));
        bindClient(FooService.class);
    }
}

