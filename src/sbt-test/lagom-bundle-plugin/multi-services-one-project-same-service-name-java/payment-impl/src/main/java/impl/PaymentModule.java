package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import api.DebitService;
import api.CreditService;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;

public class PaymentModule extends AbstractModule implements ServiceGuiceSupport {
	@Override
	protected void configure() {
		bindServices(serviceBinding(DebitService.class, DebitServiceImpl.class) , serviceBinding(CreditService.class, CreditServiceImpl.class));
//		bindServices(serviceBinding(DebitService.class, DebitServiceImpl.class));
//		bindServices(serviceBinding(CreditService.class, CreditServiceImpl.class));
	}
}