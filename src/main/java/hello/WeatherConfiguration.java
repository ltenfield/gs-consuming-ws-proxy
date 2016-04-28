
package hello;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;
import org.springframework.ws.transport.http.HttpComponentsMessageSender.RemoveSoapHeadersInterceptor;

@Configuration
public class WeatherConfiguration {

    private static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 20;

    @Bean
	public Jaxb2Marshaller marshaller() {
		Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
		marshaller.setContextPath("hello.wsdl");
		return marshaller;
	}
    
	/**
	 * HttpClient {@link org.apache.http.HttpRequestInterceptor} implementation that removes {@code Content-Length} and
	 * {@code Transfer-Encoding} headers from the request. Necessary, because some SAAJ and other SOAP implementations set these
	 * headers themselves, and HttpClient throws an exception if they have been set.
	 * <p>
	 * as documented by {@linkplain http://stackoverflow.com/questions/3332370/content-length-header-already-present}
	 * <p>
	 * As pointed out by igor.zh, this problem can occur if using Spring's HttpComponentsMessageSender class.
	 * To be more precise though, this is only a problem if you are passing your own instance of HttpClient
	 * into the HttpComponentsMessageSender constructor - the issue is handled automatically otherwise.
	 * <p>
	 * As of spring-ws 2.1.4, the HttpComponentsMessageSender.RemoveSoapHeadersInterceptor subclass that is
	 * used in the default constructor was made public to address this issue  (see https://jira.spring.io/browse/SWS-835)
	 * and so can be used in your own HttpClient instances instead of writing your own class to do it. It also clears the HTTP.TRANSFER_ENCODING header.
	 * <p>
	 * Use the HttpClientBuilder.addInterceptorFirst method to inject this interceptor into your own HttpClient instance.
	 * Example below using XML bean wiring. If anybody knows a more concise way of constructing the HttpClient instance
	 * (aside from writing a factory bean class), I'm all ears!
	 */
    @Bean 
    public RemoveSoapHeadersInterceptor soapHeadInterceptor() {
    	RemoveSoapHeadersInterceptor headersInterceptor = new RemoveSoapHeadersInterceptor();
    	return headersInterceptor;
    }
	
	@Bean
	public HttpClient httpClient(RemoveSoapHeadersInterceptor headersInterceptor) {
    	CredentialsProvider cp = new BasicCredentialsProvider();
    	cp.setCredentials(new AuthScope("myproxy",8080), new UsernamePasswordCredentials("user","password"));
    	
    	RequestConfig rc = RequestConfig.custom()
    			.setProxy(new HttpHost("myproxy",8080))
    			.setCookieSpec(CookieSpecs.DEFAULT)
    			//.setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM, AuthSchemes.DIGEST))
    			//.setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.DIGEST))
    			.build();
    	
    	HttpClientBuilder hcb = HttpClients.custom();
        hcb.setMaxConnTotal(DEFAULT_MAX_TOTAL_CONNECTIONS)
        	.setMaxConnPerRoute(10)
        	//.setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        	.setDefaultCredentialsProvider(cp)
        	.setDefaultRequestConfig(rc)   
        	.addInterceptorFirst(headersInterceptor);
        return hcb.build();
	}
	
	@Bean
	public HttpComponentsMessageSender messageSender(HttpClient configuredHttpClient) {
		HttpComponentsMessageSender messageSender = new HttpComponentsMessageSender(configuredHttpClient);
		return messageSender;
	}

	@Bean
	public WeatherClient weatherClient(Jaxb2Marshaller marshaller, HttpComponentsMessageSender messageSender) {
		WeatherClient client = new WeatherClient();
		client.setDefaultUri("http://ws.cdyne.com/WeatherWS");
		client.setMarshaller(marshaller);
		client.setUnmarshaller(marshaller);
		client.setMessageSender(messageSender);
		return client;
	}

}
