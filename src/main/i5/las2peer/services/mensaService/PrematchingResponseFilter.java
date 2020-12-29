package i5.las2peer.services.mensaService;

import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

@Provider
@PreMatching
public class PrematchingResponseFilter implements ContainerResponseFilter {

  /**
   * gets called every time the service has finished processing a request
   */
  @Override
  public void filter(
    ContainerRequestContext requestContext,
    ContainerResponseContext responseContext
  ) {
    long processDuration =
      System.currentTimeMillis() -
      (long) requestContext.getProperty("timestamp");

    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_40,
        String.format(
          "{\"url\":%s,\"url\":%s}",
          requestContext.getUriInfo().getRequestUri(),
          processDuration
        )
      );

    MensaService service = (MensaService) Context.getCurrent().getService();
  }
}
