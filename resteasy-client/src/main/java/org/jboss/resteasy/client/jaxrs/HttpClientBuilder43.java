package org.jboss.resteasy.client.jaxrs;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient43Engine;
import org.jboss.resteasy.client.jaxrs.engines.PassthroughTrustManager;
import org.jboss.resteasy.client.jaxrs.engines.factory.ApacheHttpClient4EngineFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.SecureRandom;

/**
 * A temporary class for transition between Apache pre-4.3 apis and 4.3.
 * Must maintain support for HttpClient creation in ResteasyClientBuilder
 * and creation of HttpClient that refs 4.3 classes not available in pre-4.3.
 * This usage allows pre-4.3 resteasy tests to continue to run successful.
 *
 * User: rsearls
 * Date: 5/24/16
 */
public class HttpClientBuilder43 {

    /**
     * Create ClientHttpEngine using Apache 4.3.x+ apis.
     * @return
     */

    protected static ClientHttpEngine initDefaultEngine43(ResteasyClientBuilder that)
    {
        HttpClient httpClient = null;

        HostnameVerifier verifier = null;
        if (that.verifier != null) {
            verifier = new ResteasyClientBuilder.VerifierWrapper(that.verifier);
        }
        else
        {
            verifier = new DefaultHostnameVerifier();
        }
        try
        {
            SSLConnectionSocketFactory sslsf = null;
            SSLContext theContext = that.sslContext;
            if (that.disableTrustManager)
            {
                theContext = SSLContext.getInstance("SSL");
                theContext.init(null, new TrustManager[]{new PassthroughTrustManager()},
                    new SecureRandom());
                sslsf = new SSLConnectionSocketFactory(theContext, verifier);
            }
            else if (theContext != null)
            {
                sslsf = new SSLConnectionSocketFactory(theContext, verifier);
            }
            else if (that.clientKeyStore != null || that.truststore != null)
            {
                SSLContext ctx = SSLContexts.custom()
                    .useProtocol(SSLConnectionSocketFactory.TLS)
                    .setSecureRandom(null)
                    .loadKeyMaterial(that.clientKeyStore,
                        that.clientPrivateKeyPassword != null ? that.clientPrivateKeyPassword.toCharArray() : null)
                    .loadTrustMaterial(that.truststore, TrustSelfSignedStrategy.INSTANCE)
                    .build();
                sslsf = new SSLConnectionSocketFactory(ctx, verifier);
            }
            else
            {
                final SSLContext tlsContext = SSLContext.getInstance(SSLConnectionSocketFactory.TLS);
                tlsContext.init(null, null, null);
                sslsf = new SSLConnectionSocketFactory(tlsContext, verifier);
            }

            final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslsf)
                .build();

            HttpClientConnectionManager cm = null;
            if (that.connectionPoolSize > 0)
            {
                PoolingHttpClientConnectionManager tcm = new PoolingHttpClientConnectionManager(
                    registry, null, null ,null, that.connectionTTL, that.connectionTTLUnit);
                tcm.setMaxTotal(that.connectionPoolSize);
                if (that.maxPooledPerRoute == 0) that.maxPooledPerRoute = that.connectionPoolSize;
                tcm.setDefaultMaxPerRoute(that.maxPooledPerRoute);
                cm = tcm;

            }
            else
            {
                cm = new BasicHttpClientConnectionManager(registry);
            }

            RequestConfig.Builder rcBuilder = RequestConfig.custom();
            if (that.socketTimeout > -1)
            {
                rcBuilder.setSocketTimeout((int) that.socketTimeoutUnits.toMillis(that.socketTimeout));
            }
            if (that.establishConnectionTimeout > -1)
            {
                rcBuilder.setConnectTimeout((int)that.establishConnectionTimeoutUnits.toMillis(that.establishConnectionTimeout));
            }
            if (that.connectionCheckoutTimeoutMs > -1)
            {
                rcBuilder.setConnectionRequestTimeout(that.connectionCheckoutTimeoutMs);
            }

            httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(rcBuilder.build())
                .setProxy(that.defaultProxy)
                .build();
            ApacheHttpClient43Engine engine =
                (ApacheHttpClient43Engine) ApacheHttpClient4EngineFactory.create(httpClient, true);
            engine.setResponseBufferSize(that.responseBufferSize);
            engine.setHostnameVerifier(verifier);
            // this may be null.  We can't really support this with Apache Client.
            engine.setSslContext(theContext);
            return engine;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
