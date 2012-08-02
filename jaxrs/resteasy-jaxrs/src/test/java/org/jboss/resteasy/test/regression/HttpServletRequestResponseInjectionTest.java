package org.jboss.resteasy.test.regression;

import static org.jboss.resteasy.test.TestPortProvider.generateBaseUrl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.test.BaseResourceTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * RESTEASY-745
 *
 * @author <a href="mailto:jlivings@redhat.com">James Livingston</a>
 *
 * This test ensures that the servlet specification SRV.8.2 and SRV.14.2.5.1 can be obeyed.
 *
 * ServletContext.getRequestDispatcher().forward(req, resp) MUST be passed the original
 * HttpServlet{Request,Response} object from the container, or a chain of
 * HttpServlet{Request,Response}Wrapper objects which delegate to the original.
 *
 * For a resource to be able to do that, the objected injected for @Context
 * on HttpServletRequest and HttpServletResponse must satisfy those constraints.
 */
public class HttpServletRequestResponseInjectionTest extends BaseResourceTest
{
	@Path("/test")
	public static class MyService
	{
		@GET
		@Produces("text/plain")
		public String get(@Context HttpServletRequest req, @Context HttpServletResponse resp)
		{
			HttpServletRequest currentReq = req;
			while (currentReq instanceof HttpServletRequestWrapper) {
				currentReq = (HttpServletRequest) ((HttpServletRequestWrapper)currentReq).getRequest();
			}

			HttpServletRequest originalRequest = ResteasyProviderFactory.getContextData(HttpServletRequest.class);
			Assert.assertTrue(currentReq == originalRequest);


			HttpServletResponse currentResp = resp;
			while (currentResp instanceof HttpServletResponseWrapper) {
				currentResp = (HttpServletResponse) ((HttpServletResponseWrapper)currentResp).getResponse();
			}

			HttpServletResponse originalResponse = ResteasyProviderFactory.getContextData(HttpServletResponse.class);
			Assert.assertTrue(currentResp == originalResponse);
			return "success";
		}
	}

	@Before
	public void init() throws Exception
	{
		addPerRequestResource(MyService.class);
	}

	@Test
	public void testInjection() throws Exception
	{
		ClientRequest request = new ClientRequest(generateBaseUrl() + "/test");
		ClientResponse response = request.get();
		Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatus());
	}
}
