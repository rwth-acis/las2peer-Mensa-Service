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
          "{\"url\":%s,\"duration\":%s}",
          requestContext.getUriInfo().getRequestUri(),
          processDuration
        )
      );

    int status = responseContext.getStatus();
    if (status == 200) {
      Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING, "");
    } else {
      Context.get().monitorEvent(MonitoringEvent.RESPONSE_FAILED, "");
    }
  }
}
