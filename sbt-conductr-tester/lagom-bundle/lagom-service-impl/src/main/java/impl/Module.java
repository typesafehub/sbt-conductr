package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import api.FooService;
import impl.FooServiceImpl;

public class Module extends AbstractModule implements ServiceGuiceSupport {
	@Override
	protected void configure() {
		bindServices(serviceBinding(FooService.class, FooServiceImpl.class));
	}
}