package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import api.DebitService;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;

public class DebitPaymentModule extends AbstractModule implements ServiceGuiceSupport {
	@Override
	protected void configure() {
		bindServices(serviceBinding(DebitService.class, DebitServiceImpl.class));
	}
}